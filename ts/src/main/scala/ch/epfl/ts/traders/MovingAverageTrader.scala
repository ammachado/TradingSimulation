package ch.epfl.ts.traders

import akka.actor.{ActorRef, Props, actorRef2Scala}
import akka.pattern.ask
import akka.util.Timeout
import ch.epfl.ts
import ch.epfl.ts.data._
import ch.epfl.ts.engine._
import ch.epfl.ts.indicators.{EmaIndicator, MovingAverage, OhlcIndicator}
import ch.epfl.ts.traders

import scala.collection.mutable.{HashMap => MHashMap}
import scala.concurrent.duration.FiniteDuration
import scala.math.{abs, floor}
import scala.reflect.ClassTag

/**
 * MovingAverageTrader companion object
 */
object MovingAverageTrader extends TraderCompanion {
  type ConcreteTrader = MovingAverageTrader
  override protected val concreteTraderTag: ClassTag[MovingAverageTrader] = scala.reflect.classTag[MovingAverageTrader]

  /** Currency pair to trade */
  val SYMBOL = "Symbol"
  /** OHLC period (duration) */
  val OHLC_PERIOD = "OhlcPeriod"
  /** Number of OHLC periods to use for the shorter moving average **/
  val SHORT_PERIODS = "ShortPeriods"
  /** Number of OHLC periods to use for the longer moving average **/
  val LONG_PERIODS = "LongPeriods"
  /** Tolerance: a kind of sensitivity threshold to avoid "fake" buy signals */
  val TOLERANCE = "Tolerance"
  /** Allow the use of Short orders in the strategy */
  val WITH_SHORT = "WithShort"
  /** Maximum percentage of wallet that we allow to short */
  val SHORT_PERCENT = "ShortPercent"

  override def strategyRequiredParameters: Map[traders.MovingAverageTrader.Key, ParameterTrait] = Map(
    SYMBOL -> CurrencyPairParameter,
    OHLC_PERIOD -> TimeParameter,
    SHORT_PERIODS -> NaturalNumberParameter,
    LONG_PERIODS -> NaturalNumberParameter,
    TOLERANCE -> RealNumberParameter)

  override def optionalParameters: Map[ts.traders.MovingAverageTrader.Key, ParameterTrait] = Map(
    WITH_SHORT -> BooleanParameter,
    SHORT_PERCENT -> CoefficientParameter)
}

/**
 * Simple momentum strategy.
 */
class MovingAverageTrader(uid: Long, marketIds: List[Long], parameters: StrategyParameters)
  extends Trader(uid, marketIds, parameters) {

  import context.dispatcher

  override def companion: MovingAverageTrader.type = MovingAverageTrader

  val symbol: (Currency, Currency) = parameters.get[(Currency, Currency)](MovingAverageTrader.SYMBOL)
  val (whatC, withC) = symbol

  val ohlcPeriod: FiniteDuration = parameters.get[FiniteDuration](MovingAverageTrader.OHLC_PERIOD)
  val shortPeriods: Int = parameters.get[Int](MovingAverageTrader.SHORT_PERIODS)
  val longPeriods: Int = parameters.get[Int](MovingAverageTrader.LONG_PERIODS)
  val tolerance: Double = parameters.get[Double](MovingAverageTrader.TOLERANCE)
  val withShort: Boolean = parameters.getOrElse[Boolean](MovingAverageTrader.WITH_SHORT, false)

  val marketId: Long = marketIds.head
  val shortPercent: Double = parameters.getOrElse[Double](MovingAverageTrader.SHORT_PERCENT, 0.0)

  /**
   * Indicators needed by the Moving Average Trader
   */
  val ohlcIndicator: ActorRef = context.actorOf(Props(classOf[OhlcIndicator], marketId, symbol, ohlcPeriod))
  val movingAverageIndicator: ActorRef = context.actorOf(Props(classOf[EmaIndicator], List(shortPeriods, longPeriods)))

  /**
   * Broker information
   */
  var broker: ActorRef = _
  var registered = false

  /**
   * Moving average of the current period
   */
  var currentShort: Double = 0.0
  var currentLong: Double = 0.0

  var oid = 0

  // TODO: replace by being wallet-aware
  var holdings: Double = 0.0
  var shortings: Double = 0.0

  var tradingPrices: MHashMap[(Currency, Currency), (Double, Double)] = MHashMap[(Currency, Currency), (Double, Double)]()

  override def receiver: PartialFunction[Any, Unit] = {

    /**
     * When receive a quote update bid and ask
     * and forward to ohlcIndicator
     */
    case q: Quote =>
      currentTimeMillis = q.timestamp
      tradingPrices((q.whatC, q.withC)) = (q.bid, q.ask)
      ohlcIndicator ! q

    case ohlc: OHLC => movingAverageIndicator ! ohlc

    case ConfirmRegistration =>
      broker = sender()
      registered = true
      log.debug("MATrader: Broker confirmed")

    case ma: MovingAverage if registered => {
      ma.value.get(shortPeriods) match {
        case Some(x) => currentShort = x
        case None => println("Error: Missing indicator with period " + shortPeriods)
      }
      ma.value.get(longPeriods) match {
        case Some(x) => currentLong = x
        case None => println("Error: Missing indicator with period " + longPeriods)
      }
      decideOrder()
    }

    // Order has been executed on the market = CLOSE Positions
    case eb: ExecutedBidOrder => log.debug("executed bid volume: " + eb.volume)
    case ea: ExecutedAskOrder => log.debug("executed ask volume: " + ea.volume)

    case _: WalletFunds =>
    case _: WalletConfirm =>

    case whatever if !registered => println("MATrader: received while not registered [check that you have a Broker]: " + whatever)
    case whatever => println("MATrader: received unknown : " + whatever)
  }

  def decideOrder(): Unit = {
    implicit val timeout: Timeout = new Timeout(askTimeout)

    val future = (broker ? GetWalletFunds(uid, this.self)).mapTo[WalletFunds]
    future.foreach {
      case WalletFunds(_, funds) => {
        var volume = 0.0
        var holdings = 0.0
        var shortings = 0.0
        var toShortAmount = 0.0

        val cashWith = funds.getOrElse(withC, 0.0)
        holdings = funds.getOrElse(whatC, 0.0)
        log.debug("cash " + cashWith + " holdings" + holdings)
        val (bidPrice, askPrice) = tradingPrices(whatC, withC)
        if (holdings < 0.0) {
          shortings = abs(holdings)
          holdings = 0.0
          volume = floor(cashWith / askPrice)
        } else if (holdings == 0.0) {
          volume = floor(cashWith / askPrice)
          toShortAmount = floor((cashWith * 0.2) / askPrice)
        } else {
          //estimation of withC available for shorting
          val estimateWithCtoShort = (cashWith + holdings * bidPrice) * shortPercent
          //We took askPrice : we short the volume that we could BUY with estimateWithC
          toShortAmount = floor(estimateWithCtoShort / askPrice)
        }
        if (withShort) {
          decideOrderWithShort(volume, holdings, shortings, toShortAmount)
        } else {
          decideOrderWithoutShort(volume, holdings)
        }
      }
    }

    future.failed.foreach(p => log.debug("MA Trader : Wallet command failed : " + p))
  }

  def decideOrderWithoutShort(volume: Double, holdings: Double): Unit = {
    // BUY signal
    if (currentShort > currentLong * (1 + tolerance) && holdings == 0.0) {
      placeOrder(MarketBidOrder(oid, uid, currentTimeMillis, whatC, withC, volume, -1))
      oid += 1
    } // SELL signal
    else if (currentShort < currentLong && holdings > 0.0) {
      placeOrder(MarketAskOrder(oid, uid, currentTimeMillis, whatC, withC, holdings, -1))
      oid += 1
    }
  }

  def decideOrderWithShort(volume: Double, holdings: Double, shortings: Double, toShortAmount: Double): Unit = {
    // BUY signal
    if (currentShort > currentLong) {
      if (shortings > 0.0) {
        if (currentShort > currentLong * (1 + tolerance)) {
          placeOrder(MarketBidOrder(oid, uid, currentTimeMillis, whatC, withC, volume, -1))
        } else {
          placeOrder(MarketBidOrder(oid, uid, currentTimeMillis, whatC, withC, shortings, -1))
        }
      } //No shorting
      else if (currentShort > currentLong * (1 + tolerance) && holdings == 0.0) {
        placeOrder(MarketBidOrder(oid, uid, currentTimeMillis, whatC, withC, volume, -1))

      }
    } // SELL signal
    else if (currentShort < currentLong) {
      //hold money
      if (holdings > 0.0) {
        if (currentShort * (1 + tolerance) < currentLong && shortings == 0.0) {
          placeOrder(MarketShortOrder(oid, uid, currentTimeMillis, whatC, withC, holdings + toShortAmount, -1))
        } else {
          placeOrder(MarketAskOrder(oid, uid, currentTimeMillis, whatC, withC, holdings, -1))
        }
      } else if (currentShort * (1 + tolerance) < currentLong && shortings == 0) {
        placeOrder(MarketShortOrder(oid, uid, currentTimeMillis, whatC, withC, toShortAmount, -1))

      }

    }
  }

  def placeOrder(order: MarketOrder): Unit = {
    log.debug("An order is placed")
    implicit val timeout: Timeout = new Timeout(askTimeout)
    oid += 1
    val future = (broker ? order).mapTo[Order]
    future.foreach {
      // Transaction has been accepted by the broker (but may not be executed : e.g. limit orders) = OPEN Positions
      case ao: AcceptedOrder => log.debug("Accepted order costCurrency: " + order.costCurrency() + " volume: " + ao.volume)
      case _: RejectedOrder => log.debug("MATrader: order failed")
      case _ => log.debug("MATrader: unknown order response")
    }

    future.failed.foreach(p => log.debug(s"Wallet command failed: $p"))
  }

  override def init: Unit = {
    log.debug("MovingAverageTrader received startSignal")
  }
}
