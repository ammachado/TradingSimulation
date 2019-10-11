package ch.epfl.ts.test.component

import akka.actor.{ActorRef, Props}
import akka.testkit.EventFilter
import ch.epfl.ts.component.StartSignal
import ch.epfl.ts.component.fetch.MarketNames
import ch.epfl.ts.data.{Currency, Quote, StrategyParameters, WalletParameter}
import ch.epfl.ts.engine.{ForexMarketRules, GetWalletFunds}
import ch.epfl.ts.test.{ActorTestSuite, FxMarketWrapped, SimpleBrokerWrapped}
import ch.epfl.ts.traders.SimpleTraderWithBroker
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.reflect.ClassTag

@RunWith(classOf[JUnitRunner])
class BrokerInteractionTest
    extends ActorTestSuite("BrokerInteractionTest") {

  val marketID = 1L
  val market = builder.createRef(Props(classOf[FxMarketWrapped], marketID, new ForexMarketRules()), MarketNames.FOREX_NAME)
  val broker = builder.createRef(Props(classOf[SimpleBrokerWrapped], market.ar), "Broker")

  val tId = 15L
  val parameters = new StrategyParameters(SimpleTraderWithBroker.INITIAL_FUNDS -> WalletParameter(Map()))
  val trader = builder.createRef(Props(classOf[SimpleTraderWrapped], tId, List(marketID), parameters, broker.ar), "Trader")

  market.ar ! StartSignal
  broker.ar ! StartSignal
  market.ar ! Quote(marketID, System.currentTimeMillis(), Currency.CHF, Currency.USD, 10.2, 13.2)
  broker.ar ! Quote(marketID, System.currentTimeMillis(), Currency.CHF, Currency.USD, 10.2, 13.2)
  "A trader " should {
    " register in a broker on startSignal" in {
      within(1 second) {
        EventFilter.debug(message = "TraderWithB: Broker confirmed", occurrences = 1) intercept {
          EventFilter.debug(message = "TraderWithB received startSignal", occurrences = 1) intercept {
            trader.ar ! StartSignal
          }
        }
      }
    }
  }
  //this executes sequentially - don't change the order
  "A broker " should {
    " create a wallet for the trader" in {
      within(1 second) {
        EventFilter.debug(message = "Broker: someone asks for not - his wallet", occurrences = 1) intercept {
          broker.ar ! GetWalletFunds(38265L, trader.ar)
        }
        EventFilter.debug(message = "Broker: someone asks for not - his wallet", occurrences = 0) intercept {
          EventFilter.debug(message = "Broker: No such wallet", occurrences = 0) intercept {
            trader.ar ! 'knowYourWallet
          }
        }
      }
    }
  }

  "A trader " can {
    " add funds to his wallet" in {
      //TODO(sygi): can he, actually?
      within(1 second) {
        EventFilter.debug(message = "TraderWithB: trying to add 100 bucks", occurrences = 1) intercept {
          EventFilter.debug(message = "Broker: got a request to fund a wallet", occurrences = 1) intercept {
            EventFilter.debug(message = "TraderWithB: Got a wallet confirmation", occurrences = 1) intercept {
              trader.ar ! 'addFunds
            }
          }
        }
      }
    }
    " check the state of his wallet" in {
      within(1 second) {
        EventFilter.debug(message = Currency.USD + " -> Some(100.0)", occurrences = 1) intercept {
          EventFilter.debug(message = "TraderWithB: money I have: ", occurrences = 1) intercept {
            EventFilter.debug(message = "Broker: got a get show wallet request", occurrences = 1) intercept {
              trader.ar ! 'knowYourWallet
            }
          }
        }
      }
    }
    " place the order" in {
      within(1 second) {
        EventFilter.debug(message = "TraderWithB: Got an executed order", occurrences = 1) intercept {
          EventFilter.debug(message = "Broker: received order", occurrences = 1) intercept {
            EventFilter.debug(message = "TraderWithB: order placement succeeded", occurrences = 1) intercept {
              trader.ar ! 'sendMarketOrder
            }
          }
        }
        //sendMarketOrder is a Market Bid Order in CHF/USD of volume 3 it means buy 3CHF at market price (ask price = 13.2 in the test)
        //So the cost here is  : 3*13.2
        EventFilter.debug(message = Currency.USD + " -> Some("+(100.0-3*13.2)+")", occurrences = 1) intercept {
          EventFilter.debug(start = Currency.CHF + " -> Some", occurrences = 1) intercept {
              trader.ar ! 'knowYourWallet
          }
        }
      }
    }
  }

  "Wallet " should {
    " block the orders exceeding funds" in {
      within(1 second) {
        EventFilter.debug(message = "FxMS: received a bidOrder", occurrences = 0) intercept {
          EventFilter.debug(message = "TraderWithB: order failed", occurrences = 1) intercept {
            trader.ar ! 'sendTooBigOrder
          }
        }
      }
    }
  }

  "Market " should {
    " reply to the broker" in {
      within(1 second){
        EventFilter.debug(message = "Broker: Transaction executed", occurrences = 1) intercept {
          EventFilter.debug(message = "TraderWithB: Got an executed order", occurrences = 1) intercept {
            trader.ar ! 'sendMarketOrder
          }
        }
      }
    }
  }
}

/**
 * A bit dirty hack to allow ComponentRef-like communication between components, while having them in Test ActorSystem
 * @param uid traderID
 * @param broker ActorRef
 */
class SimpleTraderWrapped(uid: Long, marketIds : List[Long], parameters: StrategyParameters, broker: ActorRef)
    extends SimpleTraderWithBroker(uid, marketIds, parameters) {
  override def send[T: ClassTag](t: T) {
    broker ! t
  }

  override def send[T: ClassTag](t: List[T]) = t.map(broker ! _)
}
