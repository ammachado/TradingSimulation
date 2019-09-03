package ch.epfl.ts.traders

import ch.epfl.ts.data.{CoefficientParameter, Currency, CurrencyPairParameter, NaturalNumberParameter, Order, ParameterTrait, Quote, StrategyParameters, TimeParameter, _}
import ch.epfl.ts.engine._

import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps
import scala.reflect.ClassTag
import scala.util.Random

/**
 * Required and optional parameters used by this strategy
 */
object MadTrader extends TraderCompanion {
  type ConcreteTrader = MadTrader

  override protected val concreteTraderTag: ClassTag[MadTrader] = scala.reflect.classTag[MadTrader]

  /** Interval between two random trades (in ms) */
  val INTERVAL = "interval"

  /** Initial delay before the first random trade (in ms) */
  val INITIAL_DELAY = "initial_delay"

  /** Volume of currency to trade (in currency unit) */
  val ORDER_VOLUME = "order_volume"

  /** Random variations on the volume (in percentage of the order volume, both above and below `ORDER_VOLUME`) */
  val ORDER_VOLUME_VARIATION = "order_volume_variation"

  /** Which currencies to trade */
  val CURRENCY_PAIR = "currency_pair"

  override def strategyRequiredParameters: Map[Key, ParameterTrait] = Map(
    INTERVAL -> TimeParameter,
    ORDER_VOLUME -> NaturalNumberParameter,
    CURRENCY_PAIR -> CurrencyPairParameter
  )

  override def optionalParameters: Map[Key, ParameterTrait] = Map(
    INITIAL_DELAY -> TimeParameter,
    ORDER_VOLUME_VARIATION -> CoefficientParameter
  )
}

/**
 * Trader that gives just random ask and bid orders alternatively
 */
class MadTrader(uid: Long, marketIds: List[Long], parameters: StrategyParameters) extends Trader(uid, marketIds, parameters) {

  import context._

  override def companion: MadTrader.type = MadTrader

  // TODO: this initial order ID should be unique in the system
  var orderId = 4567

  val initialDelay: FiniteDuration = parameters.getOrDefault[FiniteDuration](MadTrader.INITIAL_DELAY, TimeParameter)
  val interval: FiniteDuration = parameters.get[FiniteDuration](MadTrader.INTERVAL)
  val volume: Int = parameters.get[Int](MadTrader.ORDER_VOLUME)
  val volumeVariation: Double = parameters.getOrElse[Double](MadTrader.ORDER_VOLUME_VARIATION, 0.1)
  val currencies: (Currency, Currency) = parameters.get[(Currency, Currency)](MadTrader.CURRENCY_PAIR)

  var alternate = 0
  val r = new Random

  // TODO: make wallet-aware
  var price = 1.0

  override def receiver: PartialFunction[Any, Unit] = {

    case q: Quote =>
      currentTimeMillis = q.timestamp
      price = q.bid

    case 'SendMarketOrder =>
      // Randomize volume and price
      val variation = volumeVariation * (r.nextDouble() - 0.5) * 2.0
      val theVolume = ((1 + variation) * volume).toInt
      // TODO: this is not a dummy price anymore!
      val dummyPrice = price * (1 + 1e-3 * variation)

      if (alternate % 2 == 0) {
        send[Order](LimitAskOrder(orderId, uid, currentTimeMillis, currencies._1, currencies._2, theVolume, dummyPrice))
      } else {
        send[Order](LimitBidOrder(orderId, uid, currentTimeMillis, currencies._1, currencies._2, theVolume, dummyPrice))
      }
      alternate = alternate + 1
      orderId = orderId + 1

    case ConfirmRegistration =>
    case _: WalletConfirm =>
    case _: WalletFunds =>
    case _: ExecutedBidOrder =>
    case _: ExecutedAskOrder =>
    case _: AcceptedOrder =>

    case t => log.warning("MadTrader: received unknown " + t)
  }

  /**
   * When simulation is started, plan ahead the next random trade
   */
  override def init(): Unit = {
    system.scheduler.schedule(initialDelay, interval, self, 'SendMarketOrder)
  }
}
