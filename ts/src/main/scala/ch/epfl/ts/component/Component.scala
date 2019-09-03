package ch.epfl.ts.component

import akka.actor.{Actor, ActorLogging, ActorRef, actorRef2Scala}

import scala.collection.mutable
import scala.language.{existentials, postfixOps}
import scala.reflect.ClassTag

trait Receiver extends Actor with ActorLogging {
  def receive: PartialFunction[Any, Unit]

  def send[T: ClassTag](t: T): Unit

  def send[T: ClassTag](t: List[T]): Unit
}

abstract class Component extends Receiver {
  var dest: mutable.HashMap[Class[_], List[ActorRef]] = mutable.HashMap[Class[_], List[ActorRef]]()
  var stopped = true

  final def componentReceive: PartialFunction[Any, Unit] = {
    case ComponentRegistration(ar, ct, name) =>
      connect(ar, ct, name)
      log.debug(s"Received destination ${this.getClass.getSimpleName}: from $ar to ${ct.getSimpleName}")

    case StartSignal => stopped = false
      start()
      log.debug(s"Received Start ${this.getClass.getSimpleName}")

    case StopSignal => context.stop(self)
      stop()
      log.debug(s"Received Stop ${this.getClass.getSimpleName}")
      stopped = true

    case y if stopped => log.debug(s"Received data when stopped ${this.getClass.getSimpleName} of type ${y.getClass}")
  }

  /**
   * Connects two components
   *
   * Normally subclass don't need to override this method.
   */
  def connect(ar: ActorRef, ct: Class[_], name: String): Unit = {
    dest += (ct -> (ar :: dest.getOrElse(ct, List())))
  }

  /**
   * Starts the component
   *
   * Subclass can override do initialization here
   */
  def start(): Unit = {}

  /**
   * Stops the component
   *
   * Subclass can override do release resources here
   */
  def stop(): Unit = {}

  def receiver: PartialFunction[Any, Unit]

  /* TODO: Dirty hack, componentReceive giving back unmatched to rematch in receiver using a andThen */
  override def receive: PartialFunction[Any, Unit] = componentReceive orElse receiver

  override def send[T: ClassTag](t: T): Unit = dest.get(t.getClass).map(_.map(_ ! t)) //TODO(sygi): support superclasses

  override def send[T: ClassTag](t: List[T]): Unit = t.map(elem => dest.get(elem.getClass).map(_.map(_ ! elem)))
}

/**
 * Encapsulates [[akka.actor.ActorRef]] to facilitate connection of components
 *
 * TODO(sygi): support sending messages to ComponentRefs through!
 */
class ComponentRef(val ar: ActorRef, val clazz: Class[_], val name: String, cb: ComponentBuilder) extends Serializable {
  /** Connects current component to the destination component
    *
    * @param destination the destination component
    * @param types the types of messages that the destination expects to receive
    */
  def ->(destination: ComponentRef, types: Class[_]*) = {
    types.map(cb.add(this, destination, _))
  }

  /** Connects current component to the specified components
    *
    * @param refs the destination components
    * @param types the types of messages that the destination components expect to receive
    */
  def ->(refs: Seq[ComponentRef], types: Class[_]*) = {
    for (ref <- refs; typ <- types) cb.add(this, ref, typ)
  }
}
