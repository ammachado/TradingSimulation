package ch.epfl.ts.component.fetch

import ch.epfl.ts.data.Currency
import ch.epfl.ts.data.{DelOrder, LimitAskOrder, LimitBidOrder, LimitOrder, Order, Transaction}
import net.liftweb.json.{DefaultFormats, parse}
import org.apache.http.client.fluent._

/**
 * Implementation of the Transaction Fetch API for BTC-e
 */
class BtceTransactionPullFetcher extends PullFetch[Transaction] {
  val btce = new BtceAPI(Currency.USD, Currency.BTC)
  var count = 2000
  var latest = new Transaction(0, 0.0, 0.0, 0, Currency.BTC, Currency.USD, 0, 0, 0, 0)

  override def interval(): Int = 12000

  override def fetch(): List[Transaction] = {
    val trades = btce.getTrade(count)
    val idx = trades.indexOf(latest)
    count = if (idx < 0) 2000 else Math.min(10 * idx, 100)
    latest = if (trades.isEmpty) latest else trades.head

    if (idx > 0)
      trades.slice(0, idx).reverse
    else
      trades.reverse
  }
}

/**
 * Implementation of the Orders Fetch API for BTC-e
 */
class BtceOrderPullFetcher extends PullFetch[Order] {
  val btceApi = new BtceAPI(Currency.USD, Currency.BTC)
  var count = 2000
  // Contains the OrderId and The fetch timestamp
  var oldOrderBook: Map[Order, (Long, Long)] = Map[Order, (Long, Long)]()
  var oid = 5000000000L

  override def interval(): Int = 12000

  override def fetch(): List[Order] = {
    val fetchTime = System.currentTimeMillis()

    // Fetch the new Orders
    val curOrderBook = btceApi.getDepth(count)

    // new orders: newOrders = currentOrders - oldOrders
    // deleted orders: delOrders = oldOrders - currentOrders
    val newOrders = curOrderBook diff oldOrderBook.keySet.toList
    val delOrders = oldOrderBook.keySet.toList diff curOrderBook

    // Indexes deleted orders and removes them from the map
    val indexedDelOrders = delOrders map { k =>
      val oidts: (Long, Long) = oldOrderBook(k)
      oldOrderBook -= k
      k match {
        case LimitBidOrder(o, u, ft, wac, wic, v, p) => DelOrder(oidts._1, oidts._1, oidts._2, wac, wic, v, p)
        case LimitAskOrder(o, u, ft, wac, wic, v, p) => DelOrder(oidts._1, oidts._1, oidts._2, wac, wic, v, p)
      }
    }

    // Indexes new orders and add them to the map
    val indexedNewOrders = newOrders map { k =>
      oid += 1
      val order = k match {
        case LimitAskOrder(o, u, ft, wac, wic, v, p) => LimitAskOrder(oid, u, fetchTime, wac, wic, v, p)
        case LimitBidOrder(o, u, ft, wac, wic, v, p) => LimitBidOrder(oid, u, fetchTime, wac, wic, v, p)
      }
      oldOrderBook += (k ->(oid, fetchTime))
      order
    }

    indexedNewOrders ++ indexedDelOrders
  }
}

private[this] case class BTCeTransaction(date: Long, price: Double, amount: Double, tid: Int, price_currency: String, item: String, trade_type: String)

private[this] case class BTCeDepth(asks: List[List[Double]], bids: List[List[Double]])

class BtceAPI(from: Currency, to: Currency) {
  implicit val formats: DefaultFormats.type = net.liftweb.json.DefaultFormats

  val serverBase = "https://btc-e.com/api/2/"
  val pair: String = pair2path

  /**
   * Fetches count transactions from BTC-e's HTTP trade API
   * @param count number of Transactions to fetch
   * @return the fetched transactions
   */
  def getTrade(count: Int): List[Transaction] = {
    var t = List[BTCeTransaction]()
    try {
      val path = serverBase + pair + "/trades/" + count
      val json = Request.Get(path).execute().returnContent().asString()
      t = parse(json).extract[List[BTCeTransaction]]
    } catch {
      case _: Throwable => t = List[BTCeTransaction]();
    }

    if (t.nonEmpty) {
      t.map(f => Transaction(MarketNames.BTCE_ID, f.price, f.amount, f.date * 1000, Currency.BTC, Currency.USD, 0, 0, 0, 0))
    } else {
      List[Transaction]()
    }
  }

  /**
   * Fetches count orders from BTC-e's orderbook using the HTTP order API
   * @param count number of Order to fetch
   * @return the fetched orders
   */
  def getDepth(count: Int): List[Order] = {
    var t = List[LimitOrder]()
    try {
      val path = serverBase + pair + "/depth/" + count
      val json = Request.Get(path).execute().returnContent().asString()
      val depth = parse(json).extract[BTCeDepth]

      val asks = depth.asks map { o => LimitAskOrder(0, MarketNames.BTCE_ID, 0L, from, to, o.last, o.head)}
      val bids = depth.bids map { o => LimitBidOrder(0, MarketNames.BTCE_ID, 0L, from, to, o.last, o.head)}

      t = asks ++ bids
    } catch {
      case _: Throwable => t = List[LimitOrder]()
    }
    t
  }

  private def pair2path = (from, to) match {
    case (Currency.USD, Currency.BTC) => "btc_usd"
    case (Currency.BTC, Currency.USD) => "btc_usd"
    case _ => ???
  }
}
