package dev.cwby.guitk.events

import scala.collection.mutable
import scala.reflect.ClassTag

type EventListener[T <: Event] = T => Unit

trait Event

object EventCategory {
  case object Window extends Event
  case object Input extends Event
  case object System extends Event
}

final class EventDispatcher {
  private val listeners = mutable.Map[Class[_], mutable.ArrayBuffer[EventListener[_]]]()
  private val eventQueue = mutable.Queue[Event]()
  private var processing = false

  def subscribe[T <: Event: ClassTag](listener: EventListener[T]): Unit = {
    val eventClass = implicitly[ClassTag[T]].runtimeClass
    val buffer = listeners.getOrElseUpdate(eventClass, mutable.ArrayBuffer())
    buffer += listener
  }

  def unsubscribe[T <: Event: ClassTag](listener: EventListener[T]): Unit = {
    val eventClass = implicitly[ClassTag[T]].runtimeClass
    listeners.get(eventClass).foreach { buffer =>
      buffer -= listener
      if buffer.isEmpty then listeners.remove(eventClass)
    }
  }

  def dispatch[T <: Event](event: T): Unit = {
    if processing then
      eventQueue.enqueue(event)
      return

    processing = true
    dispatchImmediate(event)

    while eventQueue.nonEmpty do
      val queued = eventQueue.dequeue()
      dispatchImmediate(queued)

    processing = false
  }

  private def dispatchImmediate[T <: Event](event: T): Unit = {
    val eventClass = event.getClass
    listeners.get(eventClass).foreach { buffer =>
      buffer.foreach { listener =>
        try listener.asInstanceOf[EventListener[T]](event)
        catch {
          case e: Exception =>
            System.err.println(s"Event listener error: ${e.getMessage}")
        }
      }
    }
  }

  def clear(): Unit = {
    listeners.clear()
    eventQueue.clear()
    processing = false
  }

  def listenerCount[T <: Event: ClassTag]: Int = {
    val eventClass = implicitly[ClassTag[T]].runtimeClass
    listeners.get(eventClass).map(_.size).getOrElse(0)
  }
}

object EventDispatcher {
  private val global = new EventDispatcher()

  def getGlobal: EventDispatcher = global

  def dispatch[T <: Event](event: T): Unit = global.dispatch(event)

  def subscribe[T <: Event: ClassTag](listener: EventListener[T]): Unit =
    global.subscribe(listener)

  def unsubscribe[T <: Event: ClassTag](listener: EventListener[T]): Unit =
    global.unsubscribe(listener)
}
