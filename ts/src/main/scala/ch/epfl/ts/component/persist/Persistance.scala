package ch.epfl.ts.component.persist

import ch.epfl.ts.component.Component

import scala.reflect.ClassTag

/**
 * Defines the Persistance interface
 * @tparam T type
 */
trait Persistance[T] extends Schema {
  def save(t: T)
  def save(ts: List[T])
  def loadSingle(id: Int): T
  def loadBatch(startTime: Long, endTime: Long): List[T]
}

/**
 * The Abstraction for the persistance actors
 */
class Persistor[T: ClassTag](p: Persistance[T]) extends Component {
  val clazz: Class[_] = implicitly[ClassTag[T]].runtimeClass

  override def receiver: PartialFunction[Any, Unit] = {
    case d if clazz.isInstance(d) => p.save(d.asInstanceOf[T])
    case x => println(s"Persistance got: ${x.getClass.toString}")
  }
}
