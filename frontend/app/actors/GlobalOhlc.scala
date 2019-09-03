package actors

import akka.actor.{Actor, ActorRef, ActorSelection, Props}
import ch.epfl.ts.component.ComponentRegistration
import ch.epfl.ts.data.{Currency, OHLC, Quote}
import ch.epfl.ts.indicators.OhlcIndicator
import com.typesafe.config.ConfigFactory
import net.liftweb.json.Serialization.write
import net.liftweb.json._
import utils.TradingSimulationActorSelection

import scala.collection.mutable
import scala.concurrent.duration._
import scala.language.postfixOps

case class SymbolOhlc(whatC: Currency, withC: Currency, ohlc: OHLC)

/**
 * Computes OHLC for each currency based on the quote data fetched in the TradingSimulation backend
 *
 * Since each trader in the backend instantiates its own indicators, we simply compute a global
 * OHLC to display on a graph in the frontend
 * Starts a child actor for each symbol and converts the result to JSON which included the symbol
 */
class GlobalOhlc(out: ActorRef) extends Actor {
  implicit val formats: DefaultFormats.type = DefaultFormats

  type Symbol = (Currency, Currency)
  var workers: mutable.HashMap[(Currency, Currency), ActorRef] = mutable.HashMap[Symbol, ActorRef]()
  val ohlcPeriod: FiniteDuration = 1 hour

  val fetchers: ActorSelection = new TradingSimulationActorSelection(context,
    ConfigFactory.load().getString("akka.backend.fetchersActorSelection")).get
    
  fetchers ! ComponentRegistration(self, classOf[Quote], "frontendQuote")

  def receive(): PartialFunction[Any, Unit] = {
    case q: Quote =>
      val symbol: Symbol = (q.whatC, q.withC)
      val worker = workers.getOrElseUpdate(symbol,
        context.actorOf(Props(classOf[OhlcIndicator], q.marketId, symbol, ohlcPeriod)))
      worker ! q

    case ohlc: OHLC =>
      workers.find(_._2 == sender) match {
        case Some((symbol: Symbol, _)) =>
          out ! write(SymbolOhlc(symbol._1, symbol._2, ohlc))
        case _ =>
      }

    case _ =>
  }
}

object GlobalOhlc {
  def props(out: ActorRef) = Props(new GlobalOhlc(out))
}
