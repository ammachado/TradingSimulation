package ch.epfl.ts.data

/**
 * Enum for Currencies
 */
object Currency extends Serializable {
  def apply(s: String) = new Currency(s)

  // Cryptocurrencies
  val BTC = Currency("btc")
  val LTC = Currency("ltc")

  // Real-life currencies
  val USD = Currency("usd")
  val CHF = Currency("chf")
  val RUR = Currency("rur")
  val EUR = Currency("eur")
  val JPY = Currency("jpy")
  val GBP = Currency("gbp")
  val AUD = Currency("aud")
  val CAD = Currency("cad")
  val BRL = Currency("brl")

  /** Fallback currency ("default") */
  val DEF = Currency("def")

  def values: Seq[Currency] = Seq(BTC, LTC, USD, CHF, RUR, EUR, JPY, GBP, AUD, CAD, BRL, DEF)

  def supportedCurrencies(): Set[Currency] = values.toSet

  def fromString(s: String): Currency = {
    this.values.find(v => v.toString().toLowerCase() == s.toLowerCase()) match {
      case Some(currency) => currency
      case None => throw new UnsupportedOperationException(s"Currency $s is not supported.")
    }
  }

  /**
   * Creates a tuple of currencies given a string
   *
   * @param s  Input string of length six, three characters for each currency. Case insensitive.
   *           Example: "EURCHF" returns (Currency.EUR, Currency.CHF)
   */
  def pairFromString(s: String): (Currency, Currency) = {
    ( Currency.fromString(s.slice(0, 3)), Currency.fromString(s.slice(3,6)) )
  }
}

/**
 * Need this (as a top level class) to help serializability
 */
class Currency(val s: String) extends Serializable {
  override def toString: String = s.toString
  override def equals(other: Any): Boolean = other match {
    case c: Currency => c.s == this.s
    case _ => false
  }
  override def hashCode: Int = s.hashCode()
}
