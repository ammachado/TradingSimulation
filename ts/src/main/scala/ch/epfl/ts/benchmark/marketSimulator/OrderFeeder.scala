package ch.epfl.ts.benchmark.marketSimulator

import ch.epfl.ts.component.Component
import ch.epfl.ts.data.{Currency, Order}

case class LastOrder(oid: Long, uid: Long, timestamp: Long, whatC: Currency, withC: Currency, volume: Double, price: Double) extends Order

/**
 * Component used to send orders to the MarketSimulator for the MarketSimulatorBenchmark.
 * It appends a LastOrder to the list of orders to send to notify the MarketSimulator
 * that there are no more orders to process.
 */
class OrderFeeder(orders: List[Order]) extends Component {
  def receiver: PartialFunction[Any, Unit] = {
    case _ =>
  }

  override def start: Unit = {
    val ordersSent = orders :+ LastOrder(0L, 0L, System.currentTimeMillis(), Currency.DEF, Currency.DEF, 0.0, 0.0)
    send(StartSending(orders.size))
    ordersSent.foreach { o => send(o) }
  }
}