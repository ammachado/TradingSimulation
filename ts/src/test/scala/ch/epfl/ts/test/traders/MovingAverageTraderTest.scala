package ch.epfl.ts.test.traders

import akka.actor.{Props, actorRef2Scala}
import akka.testkit.EventFilter
import ch.epfl.ts.component.fetch.MarketNames
import ch.epfl.ts.component.{ComponentRef, StartSignal}
import ch.epfl.ts.data._
import ch.epfl.ts.engine.Wallet.Type
import ch.epfl.ts.engine.{ForexMarketRules, Wallet}
import ch.epfl.ts.indicators.SMA
import ch.epfl.ts.test.{ActorTestSuite, FxMarketWrapped, SimpleBrokerWrapped}
import ch.epfl.ts.traders.MovingAverageTrader
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.math.floor

/**
 * @warning Some of the following tests are dependent and should be executed in the specified order.
 */
@RunWith(classOf[JUnitRunner])
class MovingAverageTraderTest
  extends ActorTestSuite("MovingAverageTraderTestSystem") {

  val traderId: Long = 123L
  val symbol: (Currency, Currency) = (Currency.USD, Currency.CHF)
  val initialFunds: Wallet.Type = Map(symbol._2 -> 5000.0)
  val periods: Seq[Int] = Seq(5, 30)
  val tolerance = 0.0002

  val parameters = new StrategyParameters(
    MovingAverageTrader.INITIAL_FUNDS -> WalletParameter(initialFunds),
    MovingAverageTrader.SYMBOL -> CurrencyPairParameter(symbol),
    MovingAverageTrader.OHLC_PERIOD -> new TimeParameter(1 minute),
    MovingAverageTrader.SHORT_PERIODS.->(NaturalNumberParameter(periods.head)),
    MovingAverageTrader.LONG_PERIODS -> NaturalNumberParameter(periods(1)),
    MovingAverageTrader.TOLERANCE -> RealNumberParameter(tolerance),
    MovingAverageTrader.WITH_SHORT -> BooleanParameter(false)
  )

  val marketID = 1L
  val market: ComponentRef = builder.createRef(Props(classOf[FxMarketWrapped], marketID, new ForexMarketRules()), MarketNames.FOREX_NAME)
  val broker: ComponentRef = builder.createRef(Props(classOf[SimpleBrokerWrapped], market.ar), "Broker")
  val trader: ComponentRef = builder.createRef(Props(classOf[MovingAverageTraderWrapped], traderId,List(marketID),parameters, broker.ar), "Trader")

  market.ar ! StartSignal
  broker.ar ! StartSignal

  val (bidPrice, askPrice) = (0.90, 0.95)
  val testQuote = Quote(marketID, System.currentTimeMillis(), symbol._1, symbol._2, bidPrice, askPrice)
  market.ar ! testQuote
  broker.ar ! testQuote

  val initWallet: Type = initialFunds
  var cash: Double = initialFunds(Currency.CHF)
  var volume: Double = floor(cash / askPrice)

  "A trader " should {

    "register" in {
      within(1 second) {
        EventFilter.debug(message = "MATrader: Broker confirmed", occurrences = 1) intercept {
          trader.ar ! StartSignal
          trader.ar ! testQuote
        }
      }
    }

    "buy (20,3)" in {
      within(1 second) {
        EventFilter.debug(message = "Accepted order costCurrency: " + symbol._2 + " volume: " + volume, occurrences = 1) intercept {

          trader.ar ! SMA(Map(5 -> 20.0, 30 -> 3.0))
        }
      }
      cash -= volume * askPrice
    }

    "sell(3,20)" in {
      within(1 second) {
        EventFilter.debug(message = "Accepted order costCurrency: " + symbol._1 + " volume: " + volume, occurrences = 1) intercept {

          trader.ar ! SMA(Map(5 -> 3.0, 30 -> 20.0))
        }
      }
      cash += volume * bidPrice
      volume = floor(cash / askPrice)
    }

    "not buy(10.001,10)" in {
      within(1 second) {
        EventFilter.debug(message = "Accepted order costCurrency: " + symbol._2 + " volume: " + volume, occurrences = 0) intercept {

          trader.ar ! SMA(Map(5 -> 10.001, 30 -> 10.0))
        }
      }
    }

    // For small numbers > is eq to >=  (10*(1+0.0002) = 10.00199999)
    "buy(10.002,10)" in {
      within(1 second) {
        EventFilter.debug(message = "Accepted order costCurrency: " + symbol._2 + " volume: " + volume, occurrences = 1) intercept {
          trader.ar ! SMA(Map(5 -> 10.002, 30 -> 10))

        }
      }
      cash -= volume * askPrice
    }

    "not buy(10.003,10) (already hold a position)" in {
      within(1 second) {
        EventFilter.debug(message = "Accepted order costCurrency: " + symbol._2 + " volume: " + volume, occurrences = 0) intercept {

          trader.ar ! SMA(Map(5 -> 10.003, 30 -> 10))
        }
      }
    }

    "sell(9.9999,10)" in {
      within(1 second) {
        EventFilter.debug(message = "Accepted order costCurrency: " + symbol._1 + " volume: " + volume, occurrences = 1) intercept {

          trader.ar ! SMA(Map(5 -> 9.9999, 30 -> 10))
        }
      }
      cash += volume * bidPrice
      volume = floor(cash / askPrice)
    }

    "not sell(9.9999,10) (no holding)" in {
      within(1 second) {
        EventFilter.debug(message = "Accepted order costCurrency: " + symbol._1 + " volume: " + volume, occurrences = 0) intercept {

          trader.ar ! SMA(Map(5 -> 9.9999, 30 -> 10))
        }
      }
    }
  }
}

