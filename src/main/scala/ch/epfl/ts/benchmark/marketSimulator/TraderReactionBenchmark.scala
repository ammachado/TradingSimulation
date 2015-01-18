package ch.epfl.ts.benchmark.marketSimulator

import ch.epfl.ts.component.ComponentBuilder
import akka.actor.Props
import ch.epfl.ts.data.MarketBidOrder
import ch.epfl.ts.data.DelOrder
import ch.epfl.ts.data.LimitAskOrder
import ch.epfl.ts.data.LimitBidOrder
import ch.epfl.ts.data.MarketAskOrder
import ch.epfl.ts.data.Order
import ch.epfl.ts.engine.BackLoop
import ch.epfl.ts.component.persist.TransactionPersistor
import ch.epfl.ts.data.LimitBidOrder
import ch.epfl.ts.data.Currency._
import ch.epfl.ts.data.MarketAskOrder
import ch.epfl.ts.data.Transaction
import ch.epfl.ts.engine.BackLoop

object TraderReactionBenchmark {

  def main(args: Array[String]) {
    var orders: List[Order] = Nil
    orders = MarketAskOrder(0L, 0L, System.currentTimeMillis(), BTC, USD, 50.0, 0.0) :: orders
    orders = LimitBidOrder(0L, 0L, System.currentTimeMillis(), BTC, USD, 50.0, 50.0) :: orders

    // create factory
    implicit val builder = new ComponentBuilder("MarketSimulatorBenchmarkSystem")

    // Persistor
    val persistor = new TransactionPersistor("bench-persistor")
    persistor.init()

    // Create Components
    val orderFeeder = builder.createRef(Props(classOf[OrderFeeder], orders))
    val market = builder.createRef(Props(classOf[BenchmarkMarketSimulator], 1L, new BenchmarkMarketRules()))
    val backloop = builder.createRef(Props(classOf[BackLoop], 1L, persistor))
    val trader = builder.createRef(Props(classOf[BenchmarkTrader]))
    val timeCounter = builder.createRef(Props(classOf[TimeCounter]))

    // Create Connections
    //orders
    orderFeeder.addDestination(market, classOf[LimitAskOrder])
    orderFeeder.addDestination(market, classOf[LimitBidOrder])
    orderFeeder.addDestination(market, classOf[MarketAskOrder])
    orderFeeder.addDestination(market, classOf[MarketBidOrder])
    orderFeeder.addDestination(market, classOf[DelOrder])
    orderFeeder.addDestination(market, classOf[LastOrder])
//    market.addDestination(trader, classOf[Transaction])
    market.addDestination(backloop, classOf[Transaction])
    backloop.addDestination(trader, classOf[Transaction])
    trader.addDestination(market, classOf[LastOrder])
    // start and end signals
    orderFeeder.addDestination(timeCounter, classOf[StartSending])
    market.addDestination(timeCounter, classOf[FinishedProcessingOrders])

    // start the benchmark
    builder.start
  }
}