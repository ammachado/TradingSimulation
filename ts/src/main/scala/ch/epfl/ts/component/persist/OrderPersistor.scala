package ch.epfl.ts.component.persist
import ch.epfl.ts.component.persist
import ch.epfl.ts.data.{Currency, DelOrder, LimitAskOrder, LimitBidOrder, MarketAskOrder, MarketBidOrder, Order}

import scala.collection.mutable.ListBuffer
import scala.slick.driver.SQLiteDriver.simple._
import scala.slick.jdbc.JdbcBackend
import scala.slick.jdbc.JdbcBackend.Database
import scala.slick.jdbc.JdbcBackend.Database.dynamicSession
import scala.slick.jdbc.meta.MTable

object OrderType extends Enumeration {
  type OrderType = Value
  val LIMIT_BID: persist.OrderType.Value = Value("LB")
  val LIMIT_ASK: persist.OrderType.Value = Value("LA")
  val MARKET_BID: persist.OrderType.Value = Value("MB")
  val MARKET_ASK: persist.OrderType.Value = Value("MA")
  val DEL: persist.OrderType.Value = Value("D")
}

import ch.epfl.ts.component.persist.OrderType._

case class PersistorOrder(oid: Long, uid: Long, timestamp: Long, whatC: Currency, withC: Currency, volume: Double, price: Double, orderType: OrderType) extends Order

/**
 * Implementation of the Persistance trait for Order
 */
class OrderPersistor(dbFilename: String) extends Persistance[Order] {

  val nullStringValue = "NULL"

  val db: JdbcBackend.DatabaseDef = Database.forURL(s"jdbc:sqlite:$dbFilename.db", driver = "org.sqlite.JDBC")

  db.withDynSession {
    if (MTable.getTables("ORDERS").list.isEmpty) {
      orders.ddl.create
    }
  }

  /**
   * save single entry
   */
  override def save(newOrder: Order): Unit = {
    db.withDynSession {
      newOrder match {
        case la: LimitAskOrder  => orders += (1, la.oid, la.uid, la.timestamp, la.whatC.toString, la.withC.toString, la.volume, la.price, LIMIT_ASK.toString)
        case lb: LimitBidOrder  => orders += (1, lb.oid, lb.uid, lb.timestamp, lb.whatC.toString, lb.withC.toString, lb.volume, lb.price, LIMIT_BID.toString)
        case mb: MarketBidOrder => orders += (1, mb.oid, mb.uid, mb.timestamp, mb.whatC.toString, mb.withC.toString, mb.volume, 0, MARKET_BID.toString)
        case ma: MarketAskOrder => orders += (1, ma.oid, ma.uid, ma.timestamp, ma.whatC.toString, ma.withC.toString, ma.volume, 0, MARKET_ASK.toString)
        case del: DelOrder      => orders += (1, del.oid, del.uid, del.timestamp, nullStringValue, nullStringValue, 0, 0, DEL.toString)
        case _                  => println(s"$dbFilename Persistor: save error")
      }
    }
  }

  /**
   * save entries
   */
  override def save(os: List[Order]): Unit = {
    db.withDynSession {
      orders ++= os.map {
          case la: LimitAskOrder  => (1, la.oid, la.uid, la.timestamp, la.whatC.toString, la.withC.toString, la.volume, la.price, LIMIT_ASK.toString)
          case lb: LimitBidOrder  => (1, lb.oid, lb.uid, lb.timestamp, lb.whatC.toString, lb.withC.toString, lb.volume, lb.price, LIMIT_BID.toString)
          case mb: MarketBidOrder => (1, mb.oid, mb.uid, mb.timestamp, mb.whatC.toString, mb.withC.toString, mb.volume, 0.0, MARKET_BID.toString)
          case ma: MarketAskOrder => (1, ma.oid, ma.uid, ma.timestamp, ma.whatC.toString, ma.withC.toString, ma.volume, 0.0, MARKET_ASK.toString)
          case del: DelOrder      => (1, del.oid, del.uid, del.timestamp, nullStringValue, nullStringValue, 0.0, 0.0, DEL.toString)
      }
    }
  }

  /**
   * load entry with id
   */
  override def loadSingle(id: Int): Order = {
    db.withDynSession {
      val r = orders.filter(_.id === id).invoker.firstOption.get
      OrderType.withName(r._9) match {
        case LIMIT_BID  => return LimitBidOrder(r._2, r._3, r._4, Currency.fromString(r._5), Currency.fromString(r._6), r._7, r._8)
        case LIMIT_ASK  => return LimitAskOrder(r._2, r._3, r._4, Currency.fromString(r._5), Currency.fromString(r._6), r._7, r._8)
        case MARKET_BID => return MarketBidOrder(r._2, r._3, r._4, Currency.fromString(r._5), Currency.fromString(r._6), r._7, 0.0)
        case MARKET_ASK => return MarketAskOrder(r._2, r._3, r._4, Currency.fromString(r._5), Currency.fromString(r._6), r._7, 0.0)
        case DEL        => return DelOrder(r._2, r._3, r._4, Currency.DEF, Currency.DEF, 0.0, 0.0)
        case _          => println(s"$dbFilename Persistor: loadSingle error"); return null
      }
    }
  }

  /**
   * load entries with timestamp value between startTime and endTime (inclusive)
   */
  override def loadBatch(startTime: Long, endTime: Long): List[Order] = {
    val res: ListBuffer[Order] = new ListBuffer[Order]()
    db.withDynSession {
      orders.filter(e => (e.timestamp >= startTime) && (e.timestamp <= endTime)).invoker.list.map { r =>
        OrderType.withName(r._9) match {
          case LIMIT_BID  => res.append(LimitBidOrder(r._2, r._3, r._4, Currency.fromString(r._5), Currency.fromString(r._6), r._7, r._8))
          case LIMIT_ASK  => res.append(LimitAskOrder(r._2, r._3, r._4, Currency.fromString(r._5), Currency.fromString(r._6), r._7, r._8))
          case MARKET_BID => res.append(MarketBidOrder(r._2, r._3, r._4, Currency.fromString(r._5), Currency.fromString(r._6), r._7, 0.0))
          case MARKET_ASK => res.append(MarketAskOrder(r._2, r._3, r._4, Currency.fromString(r._5), Currency.fromString(r._6), r._7, 0.0))
          case DEL        => res.append(DelOrder(r._2, r._3, r._4, Currency.DEF, Currency.DEF, 0.0, 0.0))
          case _          => println(s"$dbFilename Persistor: loadBatch error")
        }
      }
    }
    res.toList
  }

  /**
   * loads the amount of entries provided in the function
   * argument at most.
   */
  def loadBatch(count: Int): List[Order] = {
    val res: ListBuffer[Order] = new ListBuffer[Order]()
    db.withDynSession {
      orders.filter(e => e.id <= count).invoker.foreach { r =>
        OrderType.withName(r._9) match {
          case LIMIT_BID  => res.append(LimitBidOrder(r._2, r._3, r._4, Currency.fromString(r._5), Currency.fromString(r._6), r._7, r._8))
          case LIMIT_ASK  => res.append(LimitAskOrder(r._2, r._3, r._4, Currency.fromString(r._5), Currency.fromString(r._6), r._7, r._8))
          case MARKET_BID => res.append(MarketBidOrder(r._2, r._3, r._4, Currency.fromString(r._5), Currency.fromString(r._6), r._7, 0.0))
          case MARKET_ASK => res.append(MarketAskOrder(r._2, r._3, r._4, Currency.fromString(r._5), Currency.fromString(r._6), r._7, 0.0))
          case DEL        => res.append(DelOrder(r._2, r._3, r._4, Currency.DEF, Currency.DEF, 0.0, 0.0))
          case _          => println(s"$dbFilename Persistor: loadBatch error")
        }
      }
    }
    res.toList
  }

  /**
   * delete entry with id
   */
  def deleteSingle(id: Int): Int = {
    db.withDynSession {
      orders.filter(_.id === id).delete
    }
  }

  /**
   * delete entries with timestamp values between startTime and endTime (inclusive)
   */
  def deleteBatch(startTime: Long, endTime: Long): Int = {
    db.withDynSession {
      orders.filter(e => e.timestamp >= startTime && e.timestamp <= endTime).delete
    }
  }

  /**
   * delete all entries
   */
  def clearAll: Int = {
    db.withDynSession {
      orders.delete
    }
  }
}
