package ch.epfl.ts.engine

import ch.epfl.ts.data.{ MarketAskOrder, MarketBidOrder, Order, Streamable, Transaction }

/**
 * represents the cost of placing a bid and market order
 */
case class CommissionFX(limitOrderFee: Double, marketOrderFee: Double)

/**
 * Market Simulator Configuration class. Defines the orders books priority implementation, the matching function and the commission costs of limit and market orders.
 * Extend this class and override its method(s) to customize Market rules for specific markets.
 *
 */
class ForexMarketRules extends MarketRules {

  def matchingFunction(marketId: Long,
                       newOrder: Order,
                       newOrdersBook: PartialOrderBook,
                       bestMatchsBook: PartialOrderBook,
                       send: Streamable => Unit,
                       currentTradingPrice: Double): Unit = {

    newOrder match {
      case mbid: MarketBidOrder =>
        // TODO: meaningful seller order & trader ids
        val sellOrderId = -1
        val sellerTraderId = -1
        send(Transaction(
              marketId, currentTradingPrice,
              newOrder.volume, newOrder.timestamp,
              newOrder.whatC, newOrder.withC,
              newOrder.uid, newOrder.oid,
              sellerTraderId, sellOrderId))
        send(ExecutedBidOrder.apply(mbid,currentTradingPrice))

      case mask: MarketAskOrder =>
        // TODO: meaningful buyer order & trader ids
        val buyOrderId = -1
        val buyerTraderId = -1
        send(Transaction(marketId, currentTradingPrice,
                        newOrder.volume, newOrder.timestamp,
                        newOrder.whatC, newOrder.withC,
                        buyerTraderId, buyOrderId,
                        newOrder.uid, newOrder.oid))
        send(ExecutedAskOrder.apply(mask,currentTradingPrice))

    }
  }
}
