package ch.epfl.ts.test.traders

import ch.epfl.ts.component.fetch.MarketNames
import ch.epfl.ts.data._
import ch.epfl.ts.engine.Wallet
import ch.epfl.ts.test.ActorTestSuite
import ch.epfl.ts.traders.MadTrader
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner

import scala.concurrent.duration.DurationLong
import scala.language.postfixOps

@RunWith(classOf[JUnitRunner])
class MadTraderTest
  extends ActorTestSuite("MadTraderTest") {
  
  val traderId = 42L
  val currencies = (Currency.EUR, Currency.CHF)
  val initialFunds: Wallet.Type = Map(currencies._2 -> 1000.0)
  val initialDelay =  100 milliseconds
  val interval = 50 milliseconds
  val volume = 100
  val volumeVariation = 0.1
  val marketId = MarketNames.FOREX_ID
  
  /** Give a little margin of error in our timing assumptions */
  val gracePeriod = (10 milliseconds)
  
  val parameters = new StrategyParameters(
      MadTrader.INITIAL_FUNDS -> WalletParameter(initialFunds),
      MadTrader.CURRENCY_PAIR -> CurrencyPairParameter(currencies),
      MadTrader.INITIAL_DELAY -> new TimeParameter(initialDelay),
      MadTrader.INTERVAL -> new TimeParameter(interval),
      MadTrader.ORDER_VOLUME -> NaturalNumberParameter(volume),
      MadTrader.ORDER_VOLUME_VARIATION -> CoefficientParameter(volumeVariation)
  )
  
  // TODO: refactor generic strategy testing from `StrategyParameter` test suite?
  val trader = MadTrader.getInstance(traderId, List(marketId), parameters, "MadTrader")
  
  "A MadTrader" should {
    "send its first order within the given initial delay" in {
      within(initialDelay + gracePeriod) {
        // TODO
        assert(true)
      }
    }
    
    "send orders regularly based on the given interval" in {
      within(initialDelay + interval + 2 * gracePeriod) {
        // TODO
        assert(true)
      }
    }
    
    "respect respect the given volume" in {
      // TODO
    }
  }
  
}