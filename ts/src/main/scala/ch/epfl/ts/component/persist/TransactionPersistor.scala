package ch.epfl.ts.component.persist

import ch.epfl.ts.data.{Currency, Transaction}

import scala.collection.mutable.ListBuffer
import scala.slick.driver.SQLiteDriver.simple._
import scala.slick.jdbc.JdbcBackend
import scala.slick.jdbc.JdbcBackend.Database
import scala.slick.jdbc.JdbcBackend.Database.dynamicSession
import scala.slick.jdbc.meta.MTable

/**
 * Implementation of the Persistance trait for Transaction
 */
class TransactionPersistor(dbFilename: String) extends Persistance[Transaction] {

  val db: JdbcBackend.DatabaseDef = Database.forURL("jdbc:sqlite:" + dbFilename + ".db", driver = "org.sqlite.JDBC")

  db.withDynSession {
    if (MTable.getTables("TRANSACTIONS").list.isEmpty) {
      transactions.ddl.create
    }
  }

  /**
   * save single entry
   */
  override def save(newTransaction: Transaction): Unit = {
    db.withDynSession {
      transactions += (1, newTransaction.mid, newTransaction.price, newTransaction.volume, newTransaction.timestamp, newTransaction.whatC.toString, newTransaction.withC.toString, newTransaction.buyerId, newTransaction.buyOrderId, newTransaction.sellerId, newTransaction.sellOrderId) // AutoInc are implicitly ignored
    }
  }

  /**
   * save entries
   */
  override def save(ts: List[Transaction]): Unit = {
    db.withDynSession {
      transactions ++= ts.map { x => (1, x.mid, x.price, x.volume, x.timestamp, x.whatC.toString, x.withC.toString, x.buyerId, x.buyOrderId, x.sellerId, x.sellOrderId) }
    }
  }

  /**
   * load entry with id
   */
  override def loadSingle(id: Int): Transaction = {
    db.withDynSession {
      val r = transactions.filter(_.id === id).invoker.firstOption.get
      return Transaction(r._2, r._3, r._4, r._5, Currency.fromString(r._6), Currency.fromString(r._7), r._8, r._9, r._10, r._11)
    }
  }

  /**
   * load entries with timestamp value between startTime and endTime (inclusive)
   */
  override def loadBatch(startTime: Long, endTime: Long): List[Transaction] = {
    var res: ListBuffer[Transaction] = ListBuffer[Transaction]()
    db.withDynSession {
      transactions.filter(e => e.timestamp >= startTime && e.timestamp <= endTime).invoker.foreach { r => res.append(Transaction(r._2, r._3, r._4, r._5, Currency.fromString(r._6), Currency.fromString(r._7), r._8, r._9, r._10, r._11)) }
    }
    res.toList
  }

  /**
   * delete entry with id
   */
  def deleteSingle(id: Int): Int = {
    db.withDynSession {
      transactions.filter(_.id === id).delete
    }
  }

  /**
   * delete entries with timestamp values between startTime and endTime (inclusive)
   */
  def deleteBatch(startTime: Long, endTime: Long): Int = {
    db.withDynSession {
      transactions.filter(e => e.timestamp >= startTime && e.timestamp <= endTime).delete
    }
  }

  /**
   * delete all entries
   */
  def clearAll: Int = {
    db.withDynSession {
      transactions.delete
    }
  }
}
