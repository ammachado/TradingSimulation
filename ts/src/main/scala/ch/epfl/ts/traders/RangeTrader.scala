package ch.epfl.ts.traders

import akka.actor.{ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import ch.epfl.ts.component.fetch.MarketNames
import ch.epfl.ts.data._
import ch.epfl.ts.engine._
import ch.epfl.ts.indicators.{OhlcIndicator, RangeIndic, RangeIndicator}
import ch.epfl.ts.traders

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}


/**
 * @note This needs to be on top level for serializability
 */
private case class GotWalletFunds(wallet: Try[WalletFunds]) extends Streamable


/**
 * RangeTrader companion object
 */
object RangeTrader extends TraderCompanion {
  type ConcreteTrader = RangeTrader
  override protected val concreteTraderTag: ClassTag[RangeTrader] = scala.reflect.classTag[RangeTrader]

  /** Currencies to trade */
  val SYMBOL = "Symbol"
  /** Size of the window during which we will send order, expressed in percent of the range total size */
  val ORDER_WINDOW = "OrderWindow"

  override def strategyRequiredParameters: Map[traders.RangeTrader.Key, ParameterTrait] = Map(
    SYMBOL -> CurrencyPairParameter,
    ORDER_WINDOW -> CoefficientParameter
  )
}

/**
 * The strategy used by this trader is a classical mean reversion strategy.
 * We define to range the resistance and the support.
 * The resistance is considered as a ceiling and when prices are close to it we sell since we expect prices to go back to normal
 * The support is considered as a floor ans when pricess are close to it is a good time to buy. But note that if prices breaks the
 * support then we liquidate our position. We avoid the risk that prices will crash.
 */
class RangeTrader(uid: Long, marketIds: List[Long], parameters: StrategyParameters)
  extends Trader(uid, marketIds, parameters) {

  override def companion: RangeTrader.type = RangeTrader

  val (whatC, withC) = parameters.get[(Currency, Currency)](RangeTrader.SYMBOL)
  val orderWindow: Double = parameters.get[Double](RangeTrader.ORDER_WINDOW)
  var recomputeRange: Boolean = true
  var resistance: Double = Double.MaxValue
  var support: Double = Double.MinValue
  var oid: Long = 0
  var currentPrice: Double = 0.0
  var volume: Double = 0

  /** Define the height of the range the buy/sell window will be define as a percentage of this range */
  var rangeSize: Double = 0.0

  val marketId: Long = MarketNames.FOREX_ID
  val ohlcIndicator: ActorRef = context.actorOf(Props(classOf[OhlcIndicator], marketId, (whatC, withC), 1 hour), "ohlcIndicator")

  /** Number of past OHLC that we use to compute support and range */
  val timePeriod = 48
  val tolerance = 1
  val rangeIndicator: ActorRef = context.actorOf(Props(classOf[RangeIndicator], timePeriod, tolerance), "rangeIndicator")

  /**
   * To make sure that we sell when we actually have something to sell
   * and buy only when we haven't buy yet
   */
  var holdings: Double = 0.0
  var rangeReady: Boolean = false
  var askPrice = 0.0
  var bidPrice = 0.0

  /**
   * Broker information
   */
  var broker: ActorRef = _
  var registered = false

  /**
   * When we receive an OHLC we check if the price is in the buying range or in the selling range.
   * If the price break the support we sell (assumption price will crashes)
   */
  override def receiver: PartialFunction[Any, Unit] = {

    case GotWalletFunds(wallet) => wallet match {
      case Success(WalletFunds(id, funds: Map[Currency, Double])) =>
        val cashWith = funds.getOrElse(withC, 0.0)
        holdings = funds.getOrElse(whatC, 0.0)
        volume = Math.floor(cashWith / askPrice)
        decideOrder()

      case Failure(e) => log.error(s"Range trader was supposed to receive wallet funds but received $e instead")
    }

    case ConfirmRegistration =>
      broker = sender()
      registered = true
      log.debug("RangeTrader: Broker confirmed")

    case quote: Quote =>
      currentTimeMillis = quote.timestamp
      askPrice = quote.ask
      bidPrice = quote.bid
      ohlcIndicator ! quote

    case ohlc: OHLC if registered =>
      rangeIndicator ! ohlc
      currentPrice = ohlc.close
      if (rangeReady) {
        prepareOrder()
      }

    case range: RangeIndic =>
      log.debug(s"received range with support = ${range.support} and resistance = ${range.resistance}")
      if (recomputeRange) {
        support = range.support
        resistance = range.resistance
        rangeSize = resistance - support
        recomputeRange = false
        log.debug("range is updated")
      }
      rangeReady = true

    case _: ExecutedBidOrder => log.debug("RangeTrader: bid executed")
    case _: ExecutedAskOrder => log.debug("RangeTrader: ask executed")
    case _: WalletFunds =>
    case _: WalletConfirm =>
    case o => log.info("RangeTrader received unknown: " + o)
  }

  def decideOrder(): Unit = {
    /** We receive a sell signal */
    if (currentPrice >= resistance - (rangeSize * orderWindow) && holdings > 0.0) {
      oid += 1
      recomputeRange = true
      placeOrder(MarketAskOrder(oid, uid, currentTimeMillis, whatC, withC, holdings, -1))
      log.debug("sell")
    }

    /** panic sell, the prices breaks the support */
    else if (currentPrice < support - (rangeSize * orderWindow) && holdings > 0) {
      oid += 1
      recomputeRange = true
      placeOrder(MarketAskOrder(oid, uid, currentTimeMillis, whatC, withC, holdings, -1))
      log.debug("panic sell")
    }

    /** We are in the buy window */
    else if (currentPrice <= support + (rangeSize * orderWindow) && currentPrice > support && holdings == 0.0) {
      oid += 1
      recomputeRange = false
      placeOrder(MarketBidOrder(oid, uid, currentTimeMillis, whatC, withC, volume, -1))
      log.debug("buy")
    }

    /** We are breaking the resistance with no holdings recompute the range */
    else if (currentPrice > resistance && holdings == 0.0) {
      recomputeRange = true
      log.debug("resitance broken allow range recomputation")
    }

    /** panic signal better not buy */
    else if (currentPrice < support && holdings == 0.0) {
      log.debug("we break support with no holdings -> recompute range")
      recomputeRange = true
    }

    else {
      log.debug("nothing is done")
    }
  }

  override def init {
    log.debug("range trader start")
  }

  import context.dispatcher

  def prepareOrder(): Unit = {
    implicit val timeout: Timeout = new Timeout(askTimeout)
    val f: Future[WalletFunds] = (broker ? GetWalletFunds(uid, this.self)).mapTo[WalletFunds]
    // TODO: is this really needed? Maybe we could catch the `WalletFunds` response directly in the main receiver
    // TODO: could we simplify this by making a custom "waiting for wallet" receiver? (look into `context become`)
    f.onComplete { walletFund => this.self ! GotWalletFunds(walletFund) }
    f.failed.foreach(e => log.warning("RangeTrader: Wallet command failed: " + e))
  }

  def placeOrder(order: MarketOrder): Unit = {
    implicit val timeout: Timeout = new Timeout(askTimeout)
    val future = (broker ? order).mapTo[Order]
    future.foreach {
      //Transaction has been accepted by the broker (but may not be executed : e.g. limit orders) = OPEN Positions
      case _: AcceptedOrder => log.debug("Accepted order")
      case _: RejectedOrder => log.debug("MATrader: order failed")
      case _ => log.debug("MATrader: unknown order response")
    }

    future.failed.foreach(p => log.debug("Wallet command failed: " + p))
  }
}
