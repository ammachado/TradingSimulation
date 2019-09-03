package ch.epfl.ts.component.persist

import ch.epfl.ts.data.{Currency, Quote}
import ch.epfl.ts.component.fetch.MarketNames

import scala.slick.driver.SQLiteDriver
import scala.slick.driver.SQLiteDriver.simple._
import scala.slick.jdbc.JdbcBackend.Database.dynamicSession
import scala.slick.jdbc.meta.MTable

/**
 * Provides methods to save or load a set of quotes into an SQLite database
 *
 * @param dbFilename    The database this persistor works on. The actual file accessed
 *                      will be at data/<dbFilename>.db
 */
class QuotePersistor(dbFilename: String) extends Persistance[Quote] {

  // Open DB session and query handler on the QUOTES table
  val db: SQLiteDriver.backend.DatabaseDef = Database.forURL(s"jdbc:sqlite:data/$dbFilename.db", driver = "org.sqlite.JDBC")

  db.withDynSession {
    if (MTable.getTables("QUOTES").list.isEmpty) {
      quotes.ddl.create
    }
  }

  override def loadBatch(startTime: Long, endTime: Long): List[Quote] = db.withDynSession {
    val res = quotes.filter(e => e.timestamp >= startTime && e.timestamp <= endTime).invoker
    res.list.map( r => Quote(MarketNames.FOREX_ID, r._2, Currency.fromString(r._3), Currency.fromString(r._4), r._5, r._6))
  }

  // TODO
  override def loadSingle(id: Int): Quote = ???

  override def save(newQuotes: List[Quote]): Unit = db.withDynSession {
    // The first field of the quote (QUOTE_ID) is set to -1 but this will be
    // ignored and auto incremented by jdbc:sqlite in the actual DB table.
    quotes ++= newQuotes.map(q => (-1, q.timestamp, q.whatC.toString, q.withC.toString, q.bid, q.ask))
  }

  override def save(q: Quote): Unit = save(List(q))
}
