package ch.epfl.ts.test.traders

import akka.pattern.ask
import akka.util.Timeout
import ch.epfl.ts.component.{ComponentBuilder, ComponentRef}
import ch.epfl.ts.data.{ParameterTrait, StrategyParameters}
import ch.epfl.ts.engine.{GetTraderParameters, TraderIdentity}
import ch.epfl.ts.test.ActorTestSuite
import ch.epfl.ts.traders._
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Try

@RunWith(classOf[JUnitRunner])
class TraderTestSuite extends ActorTestSuite("ConcreteStrategyTest") {

  /**
   * Shutdown all actors in between each test
   */
  override def afterEach(): Unit = {
    val f = builder.shutdownManagedActors(shutdownTimeout)
    Await.result(f, shutdownTimeout)
    assert(builder.instances === List(), "ComponentBuilder was not cleared correctly after a test had finished!")
  }

  /* Simple tests for strategy's parameterization */
  new ConcreteStrategyTestSuite(MadTrader)
  new ConcreteStrategyTestSuite(TransactionVwapTrader)
  new ConcreteStrategyTestSuite(MovingAverageTrader)
  new ConcreteStrategyTestSuite(SimpleTraderWithBroker)
  new ConcreteStrategyTestSuite(Arbitrageur)
  new ConcreteStrategyTestSuite(SobiTrader)

  /**
   * Generic test suite to ensure that concrete trading strategy implementation is not
   * wrong in an obvious way. We check its behavior when instantiated with both
   * legal & illegal parameters.
   */
  class ConcreteStrategyTestSuite(val strategyCompanion: TraderCompanion)(implicit builder: ComponentBuilder) {

    val marketId = 1L
    val traderId = 42L

    def make(p: StrategyParameters): ComponentRef = {
      // Need to give actors a unique name
      val suffix = System.currentTimeMillis() + (Math.random() * 100000L).toLong
      val name = s"TraderBeingTested-${suffix.toString}"
      strategyCompanion.getInstance(traderId, List(marketId), p, name)
    }

    val emptyParameters = new StrategyParameters()
    val required: Map[strategyCompanion.Key, ParameterTrait] = strategyCompanion.requiredParameters
    val optional: Map[strategyCompanion.Key, ParameterTrait] = strategyCompanion.optionalParameters

    // TODO: test optional parameters

    private val requiredDefaultValues = for {
      pair <- required.toSeq
      key = pair._1
      parameter = pair._2.getInstance(pair._2.defaultValue)
    } yield (key, parameter)

    val requiredDefaultParameterization = new StrategyParameters(requiredDefaultValues: _*)

    "A " + strategyCompanion.toString() should {
      // ----- Strategies not having any required parameter
      if (required.isEmpty) {
        "should allow instantiation with no parameters" in {
          val attempt = Try(make(emptyParameters))
          assert(attempt.isSuccess, attempt.failed)
        }
      }
      // ----- Strategies with required parameters
      else {
        "not allow instantiation with no parameters" in {
          val attempt = Try(make(emptyParameters))
          assert(attempt.isFailure)
        }

        "allow instantiation with parameters' default values" in {
          val attempt = Try(make(requiredDefaultParameterization))
          assert(attempt.isSuccess, attempt.failed)
        }
      }

      // ----- All strategies
      "give out its strategy parameters when asked" in {
        implicit val timeout: Timeout = Timeout(500 milliseconds)

        val askee = make(requiredDefaultParameterization)

        builder.start
        val f = askee.ar ? GetTraderParameters
        val p = Await.result(f, timeout.duration)
        val expected = TraderIdentity(askee.name, traderId, strategyCompanion, requiredDefaultParameterization)

        assert(p === expected)
        builder.stop
      }
    }
  }
}
