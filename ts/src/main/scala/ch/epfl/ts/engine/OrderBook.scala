package ch.epfl.ts.engine

import ch.epfl.ts.data.Order

import scala.collection.mutable

/**
 * Container for the Order Book.
 * It contains the ordered set of pending Order for one symbol, for either bid or ask.
 * The implementation has a HashMap with the orderIds matching the orders
 * because the naive implementation that required to execute a find() with
 * a predicate on the TreeSet to acquire the Order reference was executed 
 * in linear time.
 */
class PartialOrderBook(val comparator: Ordering[Order]) {

  // orders ordered by the comparator
  val book: mutable.TreeSet[Order] = mutable.TreeSet[Order]()(comparator)

  // key: orderId, value: order
  val bookMap: mutable.HashMap[Long, Order] = mutable.HashMap[Long, Order]()

  /**
   * delete order from the book.
   */
  def delete(o: Order): Boolean = bookMap remove o.oid map { r => book remove r; true} getOrElse {
    false
  }

  /**
   * insert order in book.
   */
  def insert(o: Order): Unit = {
    bookMap update(o.oid, o)
    book add o
  }

  def isEmpty: Boolean = book.isEmpty

  def head: Order = book.head

  def size: Int = book.size

  override def toString :String = {
    val sb = new mutable.StringBuilder
    for(i <- book){
      sb.append(i.toString + "\n")
    }
    sb.toString()
  }
}

class OrderBook(val bids: PartialOrderBook, val asks: PartialOrderBook) {

  def delete(o: Order): Unit = if (!bids.delete(o)) asks.delete(o)

  def insertAskOrder(o: Order): Unit = asks.insert(o)

  def insertBidOrder(o: Order): Unit = bids.insert(o)

  //def getOrderById(oid: Long): Option[Order] = bids.bookMap.get(oid)
}

object OrderBook {
  def apply(bidComparator: Ordering[Order], askComparator: Ordering[Order]): OrderBook =
    new OrderBook(new PartialOrderBook(bidComparator), new PartialOrderBook(askComparator))
}


