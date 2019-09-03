package ch.epfl.ts.component.persist

import scala.slick.driver.SQLiteDriver.simple._
import scala.slick.lifted.{Column, TableQuery, Tag}

trait Schema {

  class Quotes(tag: Tag) extends Table[(Int, Long, String, String, Double, Double)](tag, "QUOTES") {
    def id: Column[Int] = column[Int]("QUOTE_ID", O.PrimaryKey, O.AutoInc)
    def timestamp: Column[Long] = column[Long]("TIMESTAMP")
    def whatC: Column[String] = column[String]("WHAT_C")
    def withC: Column[String] = column[String]("WITH_C")
    def bid: Column[Double] = column[Double]("BID")
    def ask: Column[Double] = column[Double]("ASK")

    def * = (id, timestamp, whatC, withC, bid, ask)
  }

  lazy val quotes: TableQuery[Quotes] = TableQuery[Quotes]

  class Orders(tag: Tag) extends Table[(Int, Long, Long, Long, String, String, Double, Double, String)](tag, "ORDERS") {
    def id: Column[Int] = column[Int]("ID", O.PrimaryKey, O.AutoInc)
    def oid: Column[Long] = column[Long]("ORDER_ID")
    def uid: Column[Long] = column[Long]("USER_ID")
    def timestamp: Column[Long] = column[Long]("TIMESTAMP")
    def whatC: Column[String] = column[String]("WHAT_C")
    def withC: Column[String] = column[String]("WITH_C")
    def volume: Column[Double] = column[Double]("VOLUME")
    def price: Column[Double] = column[Double]("PRICE")
    def orderType: Column[String] = column[String]("ORDER_TYPE")

    def * = (id, oid, uid, timestamp, whatC, withC, volume, price, orderType)
  }

  lazy val orders: TableQuery[Orders] = TableQuery[Orders]

  class Transactions(tag: Tag) extends Table[(Int, Long, Double, Double, Long, String, String, Long, Long, Long, Long)](tag, "TRANSACTIONS") {
    def id: Column[Int] = column[Int]("ID", O.PrimaryKey, O.AutoInc)
    def mid: Column[Long] = column[Long]("MARKET_ID")
    def price: Column[Double] = column[Double]("PRICE")
    def volume: Column[Double] = column[Double]("QUANTITY")
    def timestamp: Column[Long] = column[Long]("TIMESTAMP")
    def whatC: Column[String] = column[String]("WHAT_C")
    def withC: Column[String] = column[String]("WITH_C")
    def buyerId: Column[Long] = column[Long]("BUYER_ID")
    def buyerOrderId: Column[Long] = column[Long]("BUYER_ORDER_ID")
    def sellerId: Column[Long] = column[Long]("SELLER_ID")
    def sellerOrderId: Column[Long] = column[Long]("SELLER_ORDER_ID")
    def * = (id, mid, price, volume, timestamp, whatC, withC, buyerId, buyerOrderId, sellerId, sellerOrderId)
  }

  val transactions: TableQuery[Transactions] = TableQuery[Transactions]
}

