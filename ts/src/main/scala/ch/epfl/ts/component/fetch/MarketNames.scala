package ch.epfl.ts.component.fetch

// TODO: refactor to a package which makes more sense
object MarketNames {
  val BTCE_NAME = "BTC-e"
  val BTCE_ID = 1L
  val BITSTAMP_NAME = "Bitstamp"
  val BITSTAMP_ID = 2L
  val BITFINEX_NAME = "Bitfinex"
  val BITFINEX_ID = 3L
  val FOREX_NAME = "Forex"
  val FOREX_ID = 4L;
  val B3_NAME = "B3"
  val B3_ID = 5L

  val marketIdToName: Map[Long, String] = Map(
    BTCE_ID -> BTCE_NAME,
    BITSTAMP_ID -> BITSTAMP_NAME,
    BITFINEX_ID -> BITFINEX_NAME,
    FOREX_ID -> FOREX_NAME,
    B3_ID -> B3_NAME
  )

  val marketNameToId: Map[String, Long] = Map(
    BTCE_NAME -> BTCE_ID,
    BITSTAMP_NAME -> BITSTAMP_ID,
    BITFINEX_NAME -> BITFINEX_ID,
    FOREX_NAME -> FOREX_ID,
    B3_NAME -> B3_ID
  )
}