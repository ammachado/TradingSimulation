package ch.epfl.ts.test

import akka.actor.{ActorRef, ActorSystem, actorRef2Scala}
import akka.testkit.TestKit
import akka.util.Timeout
import ch.epfl.ts.brokers.StandardBroker
import ch.epfl.ts.component.ComponentBuilder
import ch.epfl.ts.engine.rules.FxMarketRulesWrapper
import ch.epfl.ts.engine.{ForexMarketRules, MarketFXSimulator}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.language.postfixOps
import scala.reflect.ClassTag


object TestHelpers {
  def makeTestActorSystem(name: String = "TestActorSystem") =
    ActorSystem(name, ConfigFactory.parseString(
      """
      akka.loglevel = "DEBUG"
      akka.loggers = ["akka.testkit.TestEventListener"]
      """
    ).withFallback(ConfigFactory.load()))
}

/**
 * Common superclass for testing actors
 * @param name Name of the actor system
 */
abstract class ActorTestSuite(val name: String)
  extends TestKit(TestHelpers.makeTestActorSystem(name))
  with WordSpecLike
  with BeforeAndAfterAll
  with BeforeAndAfterEach {
  
  implicit val builder: ComponentBuilder = new ComponentBuilder(system)
  
  val shutdownTimeout: FiniteDuration = 3 seconds
  
	/** After all tests have run, shut down the system */
  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system, shutdownTimeout)
  }
}

/**
 * A bit dirty hack to allow ComponentRef-like communication between components, while having them in Test ActorSystem
 */
class SimpleBrokerWrapped(market: ActorRef) extends StandardBroker {
  override def send[T: ClassTag](t: T) {
    market ! t
  }

  override def send[T: ClassTag](t: List[T]): Unit = t.foreach(market ! _)
}

/**
 * A bit dirty hack to allow ComponentRef-like communication between components, while having them in Test ActorSystem
 */
class FxMarketWrapped(uid: Long, rules: ForexMarketRules) extends MarketFXSimulator(uid, new FxMarketRulesWrapper(rules)) {
  override def send[T: ClassTag](t: T) {
    val brokerSelection = context.actorSelection("/user/brokers/*")
    implicit val timeout: Timeout = new Timeout(100 milliseconds)
    val broker = Await.result(brokerSelection.resolveOne(), timeout.duration)
    println("Tried to get Broker: " + broker)
    println("Market sent to Broker ONLY: " + t)
    broker ! t
  }
}
