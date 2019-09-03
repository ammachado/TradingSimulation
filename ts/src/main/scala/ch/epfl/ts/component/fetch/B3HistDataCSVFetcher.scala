package ch.epfl.ts.component.fetch

import java.util.{Date, Timer}
import java.time.{LocalDate, LocalDateTime, LocalTime}
import java.time.format.DateTimeFormatter

import ch.epfl.ts.component.persist.QuotePersistor
import ch.epfl.ts.data.{Currency, Quote}

import scala.concurrent.duration.DurationInt
import scala.io.Source
import scala.language.postfixOps
import scala.util.parsing.combinator._

/**
 * HistDataCSVFetcher class reads data from csv source and converts it to Quotes.
 * For every Quote it has read from disk, it calls the function callback(q: Quote),
 * simulating the past by waiting for a certain time t after each call. By default
 * it is the original (historical) time difference between the quote that was last sent 
 * and the next quote to be sent.
 *
 * @param dataDir      A directory path containing data files the fetcher can read. Which files are
 *                     actually read is determined by the start and end arguments (see below).
 *                     The directory should contain substructures of the form <currency pair>/<filename>.csv, e.g.:
 *                     EURCHF/DAT_NT_EURCHF_T_ASK_201304.csv,
 *                     EURCHF/DAT_NT_EURCHF_T_BID_201304.csv,
 *                     ...
 *                     EURUSD/DAT_NT_EURUSD_T_BID_201305.csv, etc.
 *
 *                     <filename> is the same name the files have when freshly downloaded from our source,
 *                     in general they take the form "DAT_NT_<currency pair>_T_<bid/ask>_<month>".
 * @param currencyPair The currency pair to be read from the data directory, e.g. "eurchf", "USDCHF", etc.
 * @param start        From when to read. This can be any date, but will be reduced to its month. That means
 *                     if start is set to 2013-04-24 14:34 the fetcher will start reading the first quote available
 *                     in the data file for April 2013 (as if start was set to 2013-04-01 00:00).
 * @param end          Until when to read. Behaves analogous to start, i.e. if end is set to 2013-06-24 14:34
 *                     the fetcher will still read and send all data in June 2013, as if end was set to 2013-06-30 24:00.
 *                     Note this end date includes the whole corresponding month. To fetch a sigle month, pass the same
 *                     date as both start and end.
 * @param speed        The speed at which the fetcher will fetch quotes. Defaults to 1 (which means quotes are replayed
 *                     as they were recorded). Can be set e.g. to 2 (time runs twice as fast) or 60 (one hour of historical
 *                     quotes is sent in one minute), etc. Time steps are dT = int(1/speed * dT_historical), in milliseconds.
 *                     Accordingly: be careful with high speeds, if e.g. speed=1000 the system can't make the difference
 *                     between dT_historical=4200 and dT_historical=4900, they will be both sent after 4ms in the simulation.
 *                     If we go even higher, dT will become < 1 and thus 0, which in theory would mean send all the quotes
 *                     you read at the same time, but in practice would probably mess up things.
 */

class B3HistDataCSVFetcher(dataDir: String, currencyPair: String,
                           start: Date, end: Date,
                           speed: Double = 1.0) extends PushFetchComponent[Quote] {

  val workingDir = dataDir + "/" + currencyPair.toUpperCase() + "/";
  val (whatC, withC) = Currency.pairFromString(currencyPair);

  val bidPref = "DAT_NT_" + currencyPair.toUpperCase() + "_T_BID_"
  val askPref = "DAT_NT_" + currencyPair.toUpperCase() + "_T_ASK_"

  /**
   * Initial delay before starting to send quotes in order to let the system
   * get each component started.
   */
  val initialDelay = (1 second)

  /**
   * The centerpiece of this class, where we actually load the data.
   * It contains all quotes this fetcher reads from disk, ready to be fetch()ed.
   *
   * However, since allQuotes is an Iterator, it acts lazily. That means it only
   * actually reads from disc when it needs to. The data is not prematurely loaded
   * into memory.
   */
  var allQuotes = Iterator[Quote]()
  loadData()

  def loadData() = {
    allQuotes = Iterator[Quote]()
    monthsBetweenStartAndEnd.foreach {
      m => allQuotes ++= parse(bidPref + m + ".csv", askPref + m + ".csv")
    }
  }

  /**
   * Current and next quotes to be sent, updated whenever a quote was sent.
   */
  var currentQuote = allQuotes.next()
  var nextQuote = allQuotes.next()

  /**
   * Using java.util.Timer to simulate the timing of the quotes when they were generated originally.
   * Schedules a new version of itself t milliseconds after it has send the current quote,
   *
   * t = (time when next quote was recorded) - (time when current quote was recorded)
   */
  val timer = new Timer()
  timer.schedule(new SendQuotes, initialDelay.toMillis)

  class SendQuotes extends java.util.TimerTask {

    def run(): Unit = ???
  }

  /**
   * Saves the quotes this fetcher has fetched (i.e. quotes that are stored in val allQuotes)
   * in an sqlite database of a given name. The fetcher does nothing else during this time.
   * The data is reloaded (rewinded all the way to the start) at the end of the function.
   *
   * @param   filename Name of the DB file the quotes will be saved to, the final file
   *                   will be called <filename>.db
   */
  def loadInPersistor(filename: String) {
    val persistor = new QuotePersistor(filename)
    while (allQuotes.hasNext) {
      val batchSize = 100000
      println("Loading batch of " + batchSize + " records into persistor...")
      var batch = List[Quote]()
      var counter = 0
      while (counter < batchSize && allQuotes.hasNext) {
        batch = allQuotes.next :: batch
        counter = counter + 1
      }
      persistor.save(batch)
    }
    // Reload the data because Iterator.next() is destroyed it in the process
    loadData()
  }

  /**
   * Finds the months this HistDataCSVFetcher object should fetch, given the class 
   * constructor arguments (start: Date) and (end: Date)
   *
   * @return A list of months of the form List("201411", "201412", "201501", ...)
   */
  def monthsBetweenStartAndEnd: List[String] = ???

  /**
   * Given the parent class variable (workingDir: String), reads
   * two files from that directory containing bid and ask data.
   *
   * @param   bidCSVFilename Name of the bidCSV file, e.g. "DAT_NT_EURCHF_T_BID_201304.csv"
   * @param   askCSVFilename Name of the askCSV file, e.g. "DAT_NT_EURCHF_T_ASK_201304.csv"
   * @return An iterator of Quotes contained in those two files
   */
  def parse(bidCSVFilename: String, askCSVFilename: String): Iterator[Quote] = {
    val bidlines = Source.fromFile(workingDir + bidCSVFilename).getLines
    val asklines = Source.fromFile(workingDir + askCSVFilename).getLines
    bidlines.zip(asklines)
      // Combine bid and ask data into one line
      .map(l => {
        val q = B3CSVParser.parse(B3CSVParser.csvcombo, l._1 + " " + l._2).get
        // `withC` and `whatC` are not available in the CVS, we add them back
        // after parsing (they are in the path to the file opened above)
        Quote(q.marketId, q.timestamp, whatC, withC, q.bid, q.ask)
      })
  }

  override def stop() = {
    timer.cancel()
  }
}

/**
 * Parser object used by HistDataCSVFetcher.parse() to convert the CSV to Quotes.
 */
object B3CSVParser extends RegexParsers with java.io.Serializable {

  /**
   * The csvcombo format reads a line of text that has been stitched together from a bid
   * csv line and a corresponding ask csv line, and converts it to a Quote:
   *
   * For example:
   * 20130331 235953;1.216450;0 20130331 235953;1.216570;0
   * <-bid------csv------part-> <-ask------csv------part->
   */
  def csvcombo: Parser[Quote] = {
    datestamp ~ timestamp ~ ";" ~ floatingpoint ~ ";0" ~ datestamp ~ timestamp ~ ";" ~ floatingpoint ~ ";0" ^^ {
      case d ~ t ~ _ ~ bid ~ _ ~ d2 ~ t2 ~ _ ~ ask ~ _ =>
        Quote(MarketNames.FOREX_ID, toTime(d, t), Currency.DEF, Currency.DEF, bid.toDouble, ask.toDouble)
    }
  }

  val datestamp: Parser[String] = """[0-9]{4}\-""".r
  val timestamp: Parser[String] = """[0-9]{6}""".r
  val floatingpoint: Parser[String] = """[0-9]*\.?[0-9]*""".r

  val stampFormat = new java.text.SimpleDateFormat("yyyyMMddHHmmss")

  def toTime(datestamp: String, timestamp: String): Long = {
    stampFormat.parse(datestamp + timestamp).getTime
  }

  private[B3CSVParser] def dateTime(pattern: String): Parser[LocalDateTime] = new Parser[LocalDateTime] {
    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern(pattern)

    val localDateTimeParser: (CharSequence, Int) => (LocalDateTime, Int) = new ((CharSequence, Int) => (LocalDateTime, Int)) {
      def apply(text: CharSequence, offset: Int): (LocalDateTime, Int) = {
        val temporalAccessor = formatter.parse(text)
        val newPos = offset + pattern.length
        (LocalDateTime.from(temporalAccessor), newPos)
      }
    }

    def apply(in: Input): ParseResult[LocalDateTime] = {
      val source = in.source
      val offset = in.offset
      val start = handleWhiteSpace(source, offset)
      val (dateTime, endPos) = localDateTimeParser(source, start)
      if (endPos >= 0)
        Success(dateTime, in.drop(endPos - offset))
      else
        Failure("Failed to parse date", in.drop(start - offset))
    }
  }

  private[B3CSVParser] def date(pattern: String): Parser[LocalDate] = new Parser[LocalDate] {
    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern(pattern)

    val localDateParser: (CharSequence, Int) => (LocalDate, Int) = new ((CharSequence, Int) => (LocalDate, Int)) {
      def apply(text: CharSequence, offset: Int): (LocalDate, Int) = {
        val temporalAccessor = formatter.parse(text)
        val newPos = offset + pattern.length
        (LocalDate.from(temporalAccessor), newPos)
      }
    }

    def apply(in: Input): ParseResult[LocalDate] = parse(in, localDateParser)
  }

  private[B3CSVParser] def time(pattern: String): Parser[LocalTime] = new Parser[LocalTime] {
    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern(pattern)

    val localTimeParser: ((CharSequence, Int) => (LocalTime, Int)) = new ((CharSequence, Int) => (LocalTime, Int)) {
      def apply(text: CharSequence, offset: Int): (LocalTime, Int) = {
        val temporalAccessor = formatter.parse(text)
        val newPos = offset + pattern.length
        (LocalTime.from(temporalAccessor), newPos)
      }
    }

    override def apply(in: Input) = parse(in, localTimeParser)
  }

  private def parse[E](in: Input, function: (CharSequence, Int) => (E, Int)) = {
    val source = in.source
    val offset = in.offset
    val start = handleWhiteSpace(source, offset)
    val (temporalAccessor, endPos) = function(source, start)
    if (endPos >= 0)
      Success(temporalAccessor, in.drop(endPos - offset))
    else
      Failure("Failed to parse value", in.drop(start - offset))
  }
}
