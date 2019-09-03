package ch.epfl.ts.example

import ch.epfl.ts.component.StartSignal
import ch.epfl.ts.data.{Currency, CurrencyPairParameter, Parameter, WalletParameter}
import ch.epfl.ts.engine.Wallet
import ch.epfl.ts.traders.RangeTrader

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.io.Source
import scala.language.postfixOps

/**
 * Class used for a live demo of the project
 */
object DemoExample extends AbstractOptimizationExample {

  override val maximumRunDuration: Option[FiniteDuration] = None

  val symbol: (Currency, Currency) = (Currency.EUR, Currency.CHF)

  // Historical data
  val useLiveData = false
  val replaySpeed = 3600.0
  val startDate = "201304"
  val endDate = "201505"

  // Evaluation
  override val evaluationPeriod: FiniteDuration = 10.seconds

  /** Names for our trader instances */
  override lazy val traderNames: Set[String] = {
    val urlToNames = getClass.getResource("/names-shuffled.txt")
    val names = Source.fromFile(urlToNames.toURI()).getLines()
    names.toSet
  }

  // Trading strategy
  val maxInstances: Int = traderNames.size
  val strategy: RangeTrader.type = RangeTrader

  val parametersToOptimize: Set[String] = Set(RangeTrader.ORDER_WINDOW)

  val otherParameterValues: Map[String, Parameter] = {
    val initialWallet: Wallet.Type = Map(symbol._1 -> 0, symbol._2 -> 5000.0)

    Map(
      RangeTrader.INITIAL_FUNDS -> WalletParameter(initialWallet),
      RangeTrader.SYMBOL -> CurrencyPairParameter(symbol)
    )
  }

  override def main(args: Array[String]): Unit = {
    println("Going to create " + parameterizations.size + " traders on localhost")

    // ----- Create instances
    val d = factory.createDeployment(localHost, strategy, parameterizations, traderNames)

    // ----- Connecting actors
    makeConnections(d)

    // ----- Start
    // Give an early start to important components
    supervisorActor.get.ar ! StartSignal
    d.broker.ar ! StartSignal
    d.market.ar ! StartSignal
    builder.start

    // ----- Registration to the supervisor
    // Register each new trader to the master
    for (e <- d.evaluators) {
      supervisorActor.get.ar ! e.ar
    }

    // ----- Controlled duration (optional)
    maximumRunDuration match {
      case Some(duration) => terminateOptimizationAfter(duration, supervisorActor.get.ar)
      case None           =>
    }
  }
}
