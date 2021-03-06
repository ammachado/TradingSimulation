package ch.epfl.ts.test.data

import ch.epfl.ts.data._
import ch.epfl.ts.engine.{ForexMarketRules, Wallet}
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatestplus.junit.JUnitRunner

import scala.concurrent.duration.DurationLong
import scala.language.postfixOps
import scala.util.Try

@RunWith(classOf[JUnitRunner])
class StrategyParametersTests extends FunSuite {
  /**
   * Common test values
   */
  val legalCurrencies1 = (Currency.EUR, Currency.CHF)
  val legalCurrencies2 = (Currency.GBP, Currency.USD)
  val illegalCurrencies = (Currency.GBP, Currency.GBP)
  val legalCoefficient = 0.2342341341
  val illegalCoefficient = -0.2342341341
  val validWallet1: Wallet.Type = Map(Currency.EUR -> 1000.0)
  val validWallet2: Wallet.Type = Map(Currency.CHF -> 1000.0, Currency.USD -> 0.0, Currency.BTC -> 50.0)
  val validWallet3: Wallet.Type = Map()
  val invalidWallet1: Wallet.Type = Map(Currency.CHF -> 1000.0, Currency.USD -> 0.0, Currency.BTC -> -50.0)

  private type CurrencyPair = (Currency, Currency)

  /** Generic function to test basic functionality expected from Parameter subclasses */
  def testConcreteParameter[V](parameterTrait: ParameterTrait{ type T = V}, validValues: Iterable[V], invalidValues: Iterable[V]) = {

    test(parameterTrait + " should give back the value it was instantiated with") {
      for {
        myValue <- validValues
        parameter = parameterTrait.getInstance(myValue)
      } yield assert(parameter.value() == myValue, "Should give back value " + myValue)
    }

    test(parameterTrait + " should reject invalid values") {
      for {
        myValue <- invalidValues
        attempt = Try(parameterTrait.getInstance(myValue))
      } yield assert(attempt.isFailure, "Should fail with illegal value " + myValue)
    }

    test(parameterTrait + " should accept the 100 first values it declares as valid values") {
      for {
        value <- (parameterTrait.validValues take 100).toList
        attempt = Try(parameterTrait.getInstance(value))
      } yield assert(attempt.isSuccess, "Should accept " + value)
    }

    test(parameterTrait + " should have a default value which is valid") {
      val attempt = Try(parameterTrait.getInstance(parameterTrait.defaultValue))
      assert(attempt.isSuccess, "Should accept " + parameterTrait.defaultValue)
    }
  }

  test("Should allow to add several parameters at a time") {
    val myParameters = new StrategyParameters(
      "tradedCurrencies" -> CurrencyPairParameter(legalCurrencies1),
      "someCoefficient" -> CoefficientParameter(legalCoefficient),
      "someOtherParameter" -> CurrencyPairParameter(legalCurrencies2)
    )
  }

  test("Should have a nice `toString` representation") {
    val myParameters = new StrategyParameters(
      "tradedCurrencies" -> CurrencyPairParameter(legalCurrencies1),
      "someCoefficient" -> CoefficientParameter(legalCoefficient)
    )
    val expected =
      "tradedCurrencies (type CurrencyPair) = " + legalCurrencies1 + "\n" +
      "someCoefficient (type Coefficient) = " + legalCoefficient

    assert(myParameters.toString().equals(expected), "\n" + myParameters + "\n Should equal:\n" + expected);
  }

  test("Should fail at instantiation with illegal parameters") {
    val attempt = Try(new StrategyParameters("someCoefficient" -> CoefficientParameter(illegalCoefficient)))
    assert(attempt.isFailure, "Should fail to instantiate a coefficient with value " + illegalCoefficient)
  }

  test("Should hold the parameters and yield back their values") {
    val myParameters = new StrategyParameters(
      "tradedCurrencies" -> CurrencyPairParameter(legalCurrencies1),
      "someCoefficient" -> CoefficientParameter(legalCoefficient)
    )

    assert(myParameters.get[CurrencyPair]("tradedCurrencies") == legalCurrencies1)
    assert(myParameters.get[Double]("someCoefficient") == legalCoefficient)
  }

  test("Should not yield a value if the key asked was never set") {
    val myParameters = new StrategyParameters("someCoefficient" -> CoefficientParameter(legalCoefficient))

    val got = myParameters.getOption[CoefficientParameter]("unknownKey")
    assert(got == None, "Should yield nothing for an unknown key")
  }

  test("Should not yield a value if it doesn't have the expected type") {
    val myParameters = new StrategyParameters("tradedCurrencies" -> CoefficientParameter(legalCoefficient))

    val got = myParameters.getOption[CurrencyPair]("tradedCurrencies")
    assert(got == None, "Should not allow for the wrong type to be retrieved")
  }

  test("Should say it has a value if we don't care about the type") {
    val myParameters = new StrategyParameters("tradedCurrencies" -> CoefficientParameter(legalCoefficient))
    assert(myParameters.has("tradedCurrencies"), "Should allow for another type to match")
  }

  test("Should fallback on default if allowed") {
    val myParameters = new StrategyParameters()

    val got1 = myParameters.getOrDefault("tradedCurrencies", CurrencyPairParameter)
    val got2 = myParameters.getOrDefault("someCoefficient", CoefficientParameter)
    assert(got1 == CurrencyPairParameter.defaultValue)
    assert(got2 == CoefficientParameter.defaultValue)
  }

  testConcreteParameter(
      BooleanParameter,
      List(true, false),
      List()
    )

  testConcreteParameter(
  		CoefficientParameter,
  		List(0.058924379237491379, 0.0, 1.0),
  		List(-1.0, -0.0001, 1.000001)
    )

  testConcreteParameter(
      NaturalNumberParameter,
      List(0, 10, 1000, 1337),
      List(-1, -10, -1337)
    )

  testConcreteParameter(
      RealNumberParameter,
      List(0.0, 10, -100.0, 1337),
      List()
      )

  testConcreteParameter(
      TimeParameter,
      List(0L hours, 10L seconds, 1L milliseconds, 1337L milliseconds),
      List(-1L seconds, -1000L milliseconds)
    )

  testConcreteParameter(
      CurrencyPairParameter,
      List(legalCurrencies1, legalCurrencies2),
      List(illegalCurrencies)
    )

  testConcreteParameter(
      WalletParameter,
      List(validWallet1, validWallet2, validWallet3),
      List(invalidWallet1)
    )
    
  testConcreteParameter(
      MarketRulesParameter,
      List(new ForexMarketRules),
      List()
    )
}
