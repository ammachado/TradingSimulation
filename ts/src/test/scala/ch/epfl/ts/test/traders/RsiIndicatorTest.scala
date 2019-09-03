package ch.epfl.ts.test.traders

import akka.actor.Props
import akka.testkit.EventFilter
import ch.epfl.ts.data.OHLC
import ch.epfl.ts.indicators.RsiIndicator
import ch.epfl.ts.test.ActorTestSuite
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

@RunWith(classOf[JUnitRunner])
class RsiIndicatorTest extends ActorTestSuite("RsiIndicator") {
    
  val period = 5
  val rsiIndicator = system.actorOf(Props(classOf[RsiIndicator], period),"RsiIndicator")

  
  "An indicator " should {
    
    "receive a quote " in {
      within(1 second) {
        EventFilter.debug(message = "receive OHLC", occurrences = 1) intercept {
          rsiIndicator ! OHLC(1L, 1.0, 1.0,1.0,1.0,1.0,0L,0L)

        }
      }
    }
    "wait to receive period + 1 OHLC" in {
      within(1 second){
              EventFilter.debug(message = "building datas", occurrences = 5) intercept {
                         rsiIndicator ! OHLC(1L, 2.0, 2.0,2.0,2.0,2.0,0L,0L)
                         rsiIndicator ! OHLC(1L, 1.0, 1.0,1.0,1.0,1.0,0L,0L)
                         rsiIndicator ! OHLC(1L, 2.0, 2.0,2.0,2.0,2.0,0L,0L)
                         rsiIndicator ! OHLC(1L, 1.0, 1.0,1.0,1.0,1.0,0L,0L)
                         rsiIndicator ! OHLC(1L, 2.0, 2.0,2.0,2.0,2.0,0L,0L)
              }
        }   
    }
    
    "send RSI when available " in {
      within(1 second) {
        EventFilter.debug(message = "send RSI", occurrences = 1) intercept {
          rsiIndicator ! OHLC(1L, 1.0, 1.0,1.0,1.0,1.0,0L,0L)
        }
      }
    }
  }
}