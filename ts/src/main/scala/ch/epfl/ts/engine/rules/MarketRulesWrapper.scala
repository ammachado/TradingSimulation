package ch.epfl.ts.engine.rules

import ch.epfl.ts.data.{Currency, Order, Quote, Streamable}
import ch.epfl.ts.engine.{MarketRules, OrderBook}

import scala.collection.mutable

/**
 * A wrapper around the MarketRules, so that all the information needed to proceed with the order is in one place.
 * Needed to be able to change between strategies without changing the MarketSimulator.
 */
abstract class MarketRulesWrapper(rules: MarketRules) extends Serializable {
  def processOrder(o: Order, marketId: Long,
                   book: OrderBook, tradingPrices: mutable.HashMap[(Currency, Currency), (Double, Double)], //TODO(sygi): get type from original class
                   send: Streamable => Unit)
  def getRules = rules

  /** give the first quote to be able to generate them periodically right from the start */
  def initQuotes(q: Quote): Unit
}
