package ch.epfl.ts.benchmark.marketSimulator

import ch.epfl.ts.data.Currency
import ch.epfl.ts.data.{DelOrder, LimitAskOrder, LimitBidOrder, MarketAskOrder, MarketBidOrder, Order, Streamable, Transaction}
import ch.epfl.ts.engine.{MarketRules, PartialOrderBook}

class BenchmarkMarketRules extends MarketRules {

  override def matchingFunction(marketId:         Long,
                                newOrder:         Order,
                                newOrdersBook:    PartialOrderBook,
                                bestMatchsBook:   PartialOrderBook,
                                send:             Streamable => Unit,
                                matchExists:      (Double, Double) => Boolean = alwaysTrue,
                                oldTradingPrice:  Double,
                                enqueueOrElse:    (Order, PartialOrderBook) => Unit): Double = {
    if (bestMatchsBook.isEmpty) {
      enqueueOrElse(newOrder, newOrdersBook)
      oldTradingPrice
    } else {
      val bestMatch = bestMatchsBook.head

      // check if a matching order exists when used with a limit order, if market order: matchExists = true
      if (matchExists(bestMatch.price, newOrder.price)) {

        bestMatchsBook delete bestMatch
        send(DelOrder(bestMatch.oid, bestMatch.uid, newOrder.timestamp, Currency.DEF, Currency.DEF, 0.0, 0.0))

        if (bestMatch.volume == newOrder.volume) {
          bestMatch match {
            case _: LimitBidOrder =>
              send(Transaction(marketId, bestMatch.price, bestMatch.volume, newOrder.timestamp, bestMatch.whatC, bestMatch.withC, bestMatch.uid, bestMatch.oid, newOrder.uid, newOrder.oid))
            case _: LimitAskOrder =>
              send(Transaction(marketId, bestMatch.price, bestMatch.volume, newOrder.timestamp, bestMatch.whatC, bestMatch.withC, newOrder.uid, newOrder.oid, bestMatch.uid, bestMatch.oid))
            case _ =>
          }
        } else if (bestMatch.volume > newOrder.volume) {
          bestMatch match {
            case _: LimitBidOrder =>
              bestMatchsBook insert LimitBidOrder(bestMatch.oid, bestMatch.uid, bestMatch.timestamp, bestMatch.whatC, bestMatch.withC, bestMatch.volume - newOrder.volume, bestMatch.price)
              send(Transaction(marketId, bestMatch.price, newOrder.volume, newOrder.timestamp, bestMatch.whatC, bestMatch.withC, bestMatch.uid, bestMatch.oid, newOrder.uid, newOrder.oid))
              send(LimitBidOrder(bestMatch.oid, bestMatch.uid, bestMatch.timestamp, bestMatch.whatC, bestMatch.withC, bestMatch.volume - newOrder.volume, bestMatch.price))
            case _: LimitAskOrder =>
              bestMatchsBook insert LimitAskOrder(bestMatch.oid, bestMatch.uid, bestMatch.timestamp, bestMatch.whatC, bestMatch.withC, bestMatch.volume - newOrder.volume, bestMatch.price)
              send(Transaction(marketId, bestMatch.price, newOrder.volume, newOrder.timestamp, bestMatch.whatC, bestMatch.withC, newOrder.uid, newOrder.oid, bestMatch.uid, bestMatch.oid))
              send(LimitAskOrder(bestMatch.oid, bestMatch.uid, bestMatch.timestamp, bestMatch.whatC, bestMatch.withC, bestMatch.volume - newOrder.volume, bestMatch.price))
            case _ =>
          }
        } else {
          bestMatch match {
            case _: LimitBidOrder =>
              send(Transaction(marketId, bestMatch.price, bestMatch.volume, newOrder.timestamp, bestMatch.whatC, bestMatch.withC, newOrder.uid, newOrder.oid, bestMatch.uid, bestMatch.oid))
              matchingFunction(marketId, LimitBidOrder(newOrder.oid, newOrder.uid, newOrder.timestamp, newOrder.whatC, newOrder.withC, newOrder.volume - bestMatch.volume, bestMatch.price), newOrdersBook, bestMatchsBook, send, matchExists, oldTradingPrice, enqueueOrElse)
            case _: LimitAskOrder =>
              send(Transaction(marketId, bestMatch.price, bestMatch.volume, newOrder.timestamp, bestMatch.whatC, bestMatch.withC, bestMatch.uid, bestMatch.oid, newOrder.uid, newOrder.oid))
              matchingFunction(marketId, LimitAskOrder(newOrder.oid, newOrder.uid, newOrder.timestamp, newOrder.whatC, newOrder.withC, newOrder.volume - bestMatch.volume, bestMatch.price), newOrdersBook, bestMatchsBook, send, matchExists, oldTradingPrice, enqueueOrElse)
            case _: MarketBidOrder =>
              send(Transaction(marketId, bestMatch.price, bestMatch.volume, newOrder.timestamp, bestMatch.whatC, bestMatch.withC, newOrder.uid, newOrder.oid, bestMatch.uid, bestMatch.oid))
              matchingFunction(marketId, MarketBidOrder(newOrder.oid, newOrder.uid, newOrder.timestamp, newOrder.whatC, newOrder.withC, newOrder.volume - bestMatch.volume, newOrder.price), newOrdersBook, bestMatchsBook, send, matchExists, oldTradingPrice, enqueueOrElse)
            case _: MarketAskOrder =>
              send(Transaction(marketId, bestMatch.price, bestMatch.volume, newOrder.timestamp, bestMatch.whatC, bestMatch.withC, bestMatch.uid, bestMatch.oid, newOrder.uid, newOrder.oid))
              matchingFunction(marketId, MarketAskOrder(newOrder.oid, newOrder.uid, newOrder.timestamp, newOrder.whatC, newOrder.withC, newOrder.volume - bestMatch.volume, newOrder.price), newOrdersBook, bestMatchsBook, send, matchExists, oldTradingPrice, enqueueOrElse)
            case _ =>
          }
        }
        bestMatch.price
      } else {
        enqueueOrElse(newOrder, newOrdersBook)
        oldTradingPrice
      }
    }
  }
}
