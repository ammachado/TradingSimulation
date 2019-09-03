package ch.epfl.ts.traders

import akka.actor.{ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import ch.epfl.ts
import ch.epfl.ts.data._
import ch.epfl.ts.engine._
import ch.epfl.ts.indicators._
import ch.epfl.ts.traders

import scala.collection.mutable.{HashMap => MHashMap}
import scala.concurrent.duration.FiniteDuration
import scala.math.floor
import scala.reflect.ClassTag


object RsiTrader extends TraderCompanion {
  type ConcreteTrader = RsiTrader
  override protected val concreteTraderTag: ClassTag[RsiTrader] = scala.reflect.classTag[RsiTrader]

  /** Currency pair to trade */
  val SYMBOL = "Symbol"
  /** OHLC period (duration) */
  val OHLC_PERIOD = "OhlcPeriod"
  /** Period for RSI in number of OHLC*/
  val RSI_PERIOD = "RsiPeriod"
  /** HIGH_RSI : Market is overbought , time to sell  */
  val HIGH_RSI = "highRsi"
  /**LOW_RSI : Market is oversell, time to buy"*/
  val LOW_RSI = "lowRsi"

  /**WITH_SMA_CONFIRMATION : need a confirmation by SMA indicator*/
  val WITH_SMA_CONFIRMATION = "withSmaConfirmation"
  /**LONG_SMA_PERIOD : should be > to the period of RSI*/
  val LONG_SMA_PERIOD = "longSMAPeriod"

  override def strategyRequiredParameters: Map[traders.RsiTrader.Key, ParameterTrait] = Map(
    SYMBOL -> CurrencyPairParameter,
    OHLC_PERIOD -> TimeParameter,
    RSI_PERIOD -> NaturalNumberParameter,
    HIGH_RSI -> RealNumberParameter,
    LOW_RSI -> RealNumberParameter)

  override def optionalParameters: Map[ts.traders.RsiTrader.Key, ParameterTrait] = Map(
    WITH_SMA_CONFIRMATION -> BooleanParameter,
    LONG_SMA_PERIOD -> NaturalNumberParameter)
}

class RsiTrader(uid: Long, marketIds: List[Long], parameters: StrategyParameters) extends Trader(uid, marketIds, parameters) {
  import context.dispatcher
  override def companion: RsiTrader.type = RsiTrader
  val marketId: Long = marketIds.head

  val symbol: (Currency, Currency) = parameters.get[(Currency, Currency)](RsiTrader.SYMBOL)
  val (whatC, withC) = symbol
  val ohlcPeriod: FiniteDuration = parameters.get[FiniteDuration](RsiTrader.OHLC_PERIOD)
  val rsiPeriod: Int = parameters.get[Int](RsiTrader.RSI_PERIOD)
  val highRsi: Double = parameters.get[Double](RsiTrader.HIGH_RSI)
  val lowRsi: Double = parameters.get[Double](RsiTrader.HIGH_RSI)

  val withSmaConfirmation: Boolean = parameters.get[Boolean](RsiTrader.WITH_SMA_CONFIRMATION)
  val longSmaPeriod: Int = parameters.get[Int](RsiTrader.LONG_SMA_PERIOD)
  val shortSmaPeriod: Int = rsiPeriod

  /**Indicators needed for RSI strategy*/
  val ohlcIndicator: ActorRef = context.actorOf(Props(classOf[OhlcIndicator], marketId, symbol, ohlcPeriod))
  val rsiIndicator: ActorRef = context.actorOf(Props(classOf[RsiIndicator], rsiPeriod))

  lazy val smaIndicator: ActorRef = context.actorOf(Props(classOf[SmaIndicator], List(shortSmaPeriod, longSmaPeriod)))
  var currentShort = 0.0
  var currentLong = 0.0

  /**
   * Broker information
   */
  var broker: ActorRef = _
  var registered = false

  /**
   * To store prices
   */
  var tradingPrices: MHashMap[(Currency, Currency), (Double, Double)] = MHashMap[(Currency, Currency), (Double, Double)]()

  var oid = 0

  override def receiver: PartialFunction[Any, Unit] = {

    case q: Quote =>
      currentTimeMillis = q.timestamp
      tradingPrices((q.whatC, q.withC)) = (q.bid, q.ask)
      ohlcIndicator ! q

    case ohlc: OHLC =>
      rsiIndicator ! ohlc
      if (withSmaConfirmation) {
        smaIndicator ! ohlc
      }

    case ConfirmRegistration =>
      broker = sender()
      registered = true
      log.debug("RsiIndicator: Broker confirmed")

    case rsi: RSI if registered => decideOrder(rsi.value)

    case ma: MovingAverage if registered => {
      ma.value.get(shortSmaPeriod) match {
        case Some(x) => currentShort = x
        case None    => log.warning("Error: Missing indicator with period " + shortSmaPeriod)
      }
      ma.value.get(longSmaPeriod) match {
        case Some(x) => currentLong = x
        case None    => log.warning("Error: Missing indicator with period " + longSmaPeriod)
      }
    }

    case eb: ExecutedBidOrder    => log.debug("executed bid volume: " + eb.volume)
    case ea: ExecutedAskOrder    => log.debug("executed ask volume: " + ea.volume)

    case whatever if !registered => log.warning("RsiTrader: received while not registered [check that you have a Broker]: " + whatever)
    case whatever                => log.warning("RsiTrader: received unknown : " + whatever)
  }
  def decideOrder(rsi: Double): Unit = {
    implicit val timeout: Timeout = new Timeout(askTimeout)
    val future = (broker ? GetWalletFunds(uid, this.self)).mapTo[WalletFunds]
    future.foreach {
      case WalletFunds(_, funds: Map[Currency, Double]) => {
        var holdings = 0.0
        val cashWith = funds.getOrElse(withC, 0.0)
        holdings = funds.getOrElse(whatC, 0.0)
        if (!withSmaConfirmation) {
          //overbought : time to sell
          if (rsi >= highRsi && holdings > 0.0) {
            placeOrder(MarketAskOrder(oid, uid, currentTimeMillis, whatC, withC, holdings, -1))
            //oversell : time to buy
          } else if (rsi <= lowRsi && holdings == 0) {
            val askPrice = tradingPrices(whatC, withC)._2
            val volumeToBuy = floor(cashWith / askPrice)
            placeOrder(MarketBidOrder(oid, uid, currentTimeMillis, whatC, withC, volumeToBuy, -1))
          }
        } //withSmaConfirmation
        else {
          //overbought : time to sell
          if (rsi >= highRsi && holdings > 0.0 && currentShort <= currentLong) {
            placeOrder(MarketAskOrder(oid, uid, currentTimeMillis, whatC, withC, holdings, -1))
            //oversell : time to buy
          } else if (rsi <= lowRsi && holdings == 0 && currentShort >= currentLong) {
            val askPrice = tradingPrices(whatC, withC)._2
            val volumeToBuy = floor(cashWith / askPrice)
            placeOrder(MarketBidOrder(oid, uid, currentTimeMillis, whatC, withC, volumeToBuy, -1))
          }
        }
      }
    }

    future.failed.foreach { p =>
      log.debug("MA Trader : Wallet command failed : " + p)
      stop
    }
  }

  def placeOrder(order: MarketOrder): Unit = {
    oid += 1
    implicit val timeout: Timeout = new Timeout(askTimeout)
    val future = (broker ? order).mapTo[Order]
    future.foreach {
      // Transaction has been accepted by the broker (but may not be executed : e.g. limit orders) = OPEN Positions
      case ao: AcceptedOrder => log.debug("Accepted order costCurrency: " + order.costCurrency() + " volume: " + ao.volume)
      case _: RejectedOrder => log.debug("MATrader: order failed")
      case _ => log.debug("MATrader: unknown order response")
    }

    future.failed.foreach(p => log.debug(s"Wallet command failed: $p"))
  }
}
