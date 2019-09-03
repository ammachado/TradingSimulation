package ch.epfl.ts.component

import akka.actor.{ActorRef, ActorSystem, Props, actorRef2Scala}
import akka.pattern.ask
import akka.util.Timeout
import ch.epfl.ts.brokers.Broker
import ch.epfl.ts.component.fetch.FetchingComponent
import ch.epfl.ts.component.utils.{ParentActor, Reaper, StartKilling, Timekeeper}
import ch.epfl.ts.engine.MarketSimulator
import ch.epfl.ts.evaluation.Evaluator
import ch.epfl.ts.traders.Trader
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.{Await, Future, Promise, blocking}
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.language.{existentials, postfixOps}
import scala.util.{Failure, Success}

case object StartSignal
case object StopSignal
case class ComponentRegistration(ar: ActorRef, ct: Class[_], name: String)

final class ComponentBuilder(val system: ActorSystem) {
  /** Alternative constructors */
  def this() {
    this(ActorSystem(ConfigFactory.load().getString("akka.systemName"), ConfigFactory.load()))
  }

  def this(name: String) {
    this(ActorSystem(name, ConfigFactory.load()))
  }

  def this(myName: String, config: Config) {
    this(ActorSystem(myName, config))
  }


  type ComponentProps = akka.actor.Props
  var graph: Map[ComponentRef, List[(ComponentRef, Class[_])]] = Map[ComponentRef, List[(ComponentRef, Class[_])]]()
  var instances: List[ComponentRef] = List[ComponentRef]()

  /** Responsible for sending `PoisonPill`s when we want to terminate the system gracefully */
  private val reaper = system.actorOf(Props(classOf[Reaper]), "Reaper")

  /**
   * "Builder" actors that are empty but useful to create instances
   * under their name. This way, we create automatically an easy-to-query
   * actor hierarchy based on concrete Actor type.
   */
  private lazy val roots: Seq[(Class[_], ActorRef)] = {
    val rootNames = Seq(
      classOf[Trader] -> "traders",
      classOf[Evaluator] -> "evaluators",
      classOf[Broker] -> "brokers",
      classOf[FetchingComponent] -> "fetchers",
      classOf[MarketSimulator] -> "markets",
      classOf[Timekeeper] -> "timekeepers",
      classOf[Any] -> "other"
    )

    rootNames.map({
      case (clazz, name) => clazz -> system.actorOf(Props(classOf[ParentActor]), name)
    })
  }

  def getRootForClass(clazz: Class[_]): ActorRef = {
    val opt = roots.find(p => p._1.isAssignableFrom(clazz))
  	// Should not fail since we have an `Any` entry in `roots`
    opt.get._2
  }


  /**
   * Connect `src` to `dest` on the given type of messages
   */
  def add(src: ComponentRef, dest: ComponentRef, data: Class[_]) {
    //println("Connecting " + src.ar + " to " + dest.ar + " for type " + data.getSimpleName)
    graph = graph + (src -> ((dest, data) :: graph.getOrElse(src, List[(ComponentRef, Class[_])]())))
    src.ar ! ComponentRegistration(dest.ar, data, dest.name)
  }

  def add(src: ComponentRef, dest: ComponentRef): (ComponentRef, ComponentRef, Class[Any]) = (src, dest, classOf[Any])

  def start(): List[Unit] = instances.map(cr => {
    cr.ar ! StartSignal
    //println(s"Sending start Signal to ${cr.ar}")
  })

  /**
   * Send a `StopSignal` to all managed components, giving them the opportunity
   * to run some cleanup.
   */
  def stop: Seq[Unit] = instances.map(cr => {
    cr.ar ! StopSignal
    //println(s"Sending stop Signal to ${cr.ar}")
  })

  /**
   * Create a new component with the given name using the `props`.
   * Certain types of components such as `Trader`s, `Evaluator`s, `Market`s, etc
   * are automatically created under an empty root actor corresponding to their class.
   * This allows us to query the actor hierarchy easily for given types.
   * @warning Current implementation is blocking
   * @todo Any way to make this non-blocking?
   */
  def createRef(props: ComponentProps, name: String): ComponentRef = blocking {
    // Determine the root actor to use for this class
    val root = getRootForClass(props.actorClass())

    // Ask the relevant root actor to make an instance for us
    implicit val timeout: Timeout = new Timeout(1 second)
    val future = (root ? ParentActor.Create(props, name)).mapTo[ParentActor.Done]
    val ref = Await.result(future, timeout.duration).ref

    // Wrap it into a ComponentRef
    instances = new ComponentRef(ref, props.actorClass(), name, this) :: instances
    instances.head
  }

  /**
   * Gracefully stop all managed components.
   * When all stops are successful, we clear the `instances` list.
   *
   * @note This differs from the `stop` in that here, actors get killed for good,
   *       and cannot get restarted.
   *
   * @return A future which completes when *all* managed actors have shut down.
   */
  def shutdownManagedActors(timeout: FiniteDuration = 10 seconds): Future[Unit] = {
    import scala.concurrent.ExecutionContext.Implicits.global

    // This allows the user of this function to be notified when shutdown is complete
    val externalPromise = Promise[Unit]()

    implicit val tt: Timeout = new Timeout(timeout)
    val p: Future[Any] = reaper ? StartKilling(instances.map(_.ar))

    p.onComplete {
      case Success(_) =>
        instances = List[ComponentRef]()
        externalPromise.success(Unit)
      case Failure(exception) =>
        externalPromise.failure(exception)
    }

    externalPromise.future
  }
}
