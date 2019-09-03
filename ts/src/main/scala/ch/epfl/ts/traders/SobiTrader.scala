package ch.epfl.ts.traders

import ch.epfl.ts.data._
import ch.epfl.ts.engine.{MarketRules, OrderBook}
import ch.epfl.ts.traders

import scala.collection.mutable
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.language.postfixOps
import scala.reflect.ClassTag

/**
 * SobiTrader companion object
 */
object SobiTrader extends TraderCompanion {
  type ConcreteTrader = SobiTrader
  override protected val concreteTraderTag: ClassTag[SobiTrader] = scala.reflect.classTag[SobiTrader]

  /**
   * Difference in price
   */
  val PRICE_DELTA = "PriceDelta"

  /**
   * @TODO Documentation
   */
  val THETA = "Theta"

  /**
   * Volume to be traded
   */
  val VOLUME = "Volume"

  /**
   * Time interval
   */
  val INTERVAL = "Interval"

  /**
   * Number of quartiles to take into account in the computation
   */
  val QUARTILES = "Quartiles"

  /**
   * Market rules of the market we are trading on
   */
  val MARKET_RULES = "MarketRules"

  override def strategyRequiredParameters: Map[traders.SobiTrader.Key, ParameterTrait] = Map(
    THETA -> RealNumberParameter,
    PRICE_DELTA -> RealNumberParameter,
    VOLUME -> NaturalNumberParameter,
    INTERVAL -> TimeParameter,
    QUARTILES -> NaturalNumberParameter,
    MARKET_RULES -> MarketRulesParameter
  )
}

/**
 * SOBI trader
 */
class SobiTrader(uid: Long, marketIds: List[Long], parameters: StrategyParameters) extends Trader(uid, marketIds, parameters) {
  import context._

  override def companion: SobiTrader.type = SobiTrader

  val theta: Double = parameters.get[Double](SobiTrader.THETA)
  val priceDelta: Double = parameters.get[Double](SobiTrader.PRICE_DELTA)
  val volume: Int = parameters.get[Int](SobiTrader.VOLUME)
  val interval: FiniteDuration = parameters.get[FiniteDuration](SobiTrader.INTERVAL)
  val quartiles: Int = parameters.get[Int](SobiTrader.QUARTILES)
  val rules: MarketRules = parameters.get[MarketRules](SobiTrader.MARKET_RULES)

  val book = OrderBook(rules.bidsOrdering(), rules.asksOrdering())
  var tradingPrice = 188700.0 // for finance.csv

  var bi: Double = 0.0
  var si: Double = 0.0
  var currentOrderId: Long = 456789

  override def receiver: PartialFunction[Any, Unit] = {
    case limitAsk: LimitAskOrder  => book insertAskOrder limitAsk
    case limitBid: LimitBidOrder  => book insertBidOrder limitBid
    case delete: DelOrder         => removeOrder(delete)
    case transaction: Transaction => tradingPrice = transaction.price

    case 'PossibleOrder =>
      bi = computeBiOrSi(book.bids.book)
      si = computeBiOrSi(book.asks.book)
      if ((si - bi) > theta) {
        currentOrderId = currentOrderId + 1
        //"place an order to buy x shares at (lastPrice-p)"
        println(s"SobiTrader: making buy order: price=${tradingPrice - priceDelta}, volume=$volume")
        send[Order](LimitBidOrder(currentOrderId, uid, currentTimeMillis, Currency.USD, Currency.USD, volume, tradingPrice - priceDelta))
      }
      if ((bi - si) > theta) {
        currentOrderId = currentOrderId + 1
        //"place an order to sell x shares at (lastPrice+p)"
        println(s"SobiTrader: making sell order: price=${tradingPrice + priceDelta}, volume=$volume")
        send[Order](LimitAskOrder(currentOrderId, uid, currentTimeMillis, Currency.USD, Currency.USD, volume, tradingPrice + priceDelta))
      }

    case _ => println("SobiTrader: received unknown")
  }

  override def init(): Unit = {
    system.scheduler.schedule(0 milliseconds, interval, self, 'PossibleOrder)
  }

  def removeOrder(order: Order): Unit = book delete order

  /**
   * compute the volume-weighted average price of the top quartile*25% of the volume of the bids/asks orders book
   */
  def computeBiOrSi[T <: Order](bids: mutable.TreeSet[T]): Double = {
    if (bids.size > 4) {
      val it = bids.iterator
      var bi: Double = 0.0
      var vol: Double = 0
      for (_ <- 0 to (bids.size * (quartiles / 4))) {
        val currentBidOrder = it.next()
        bi = bi + currentBidOrder.price * currentBidOrder.volume
        vol = vol + currentBidOrder.volume
      }
      bi / vol
    } else {
      0.0
    }
  }
}
