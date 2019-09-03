package actors

import akka.actor.{Actor, ActorRef, ActorSelection}
import ch.epfl.ts.component.ComponentRegistration
import net.liftweb.json.{DefaultFormats, Formats, Serialization}
import utils.{DoubleSerializer, MapSerializer, TradingSimulationActorSelection}

import scala.reflect.ClassTag

/**
 * Receives Messages of a given Class Tag from the Trading Simulation backend (ts)
 * and converts them to JSON in order to be passed to the client through a web socket
 *
 * Note: we are using lift-json since there is no easy way to use Play's json
 * library with generic type parameters.
 */
class MessageToJson[T <: AnyRef: ClassTag](out: ActorRef, actorSelection: String) extends Actor {
  val clazz: Class[_] = implicitly[ClassTag[T]].runtimeClass
  implicit val formats: Formats = DefaultFormats + MapSerializer + DoubleSerializer

  val actors: ActorSelection = new TradingSimulationActorSelection(context, actorSelection).get

  actors ! ComponentRegistration(self, clazz, s"frontend$clazz")

  def receive(): PartialFunction[Any, Unit] = {
    case msg: T =>
      out ! Serialization.write(msg)
    case _ =>
  }
}
