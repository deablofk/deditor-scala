package dev.cwby.guitk.registry

import dev.cwby.guitk.components.{FloatingWindow, TiledWindow, Window}
import scala.collection.mutable

final class WindowRegistry[Buffer] {
  private val windows = mutable.Map[String, Window[Buffer]]()
  private val idToWindow = mutable.Map[Long, Window[Buffer]]()
  private var nextId: Long = 0L

  def register(name: String, window: Window[Buffer]): Long = {
    val id = nextId
    nextId += 1
    windows.put(name, window)
    idToWindow.put(id, window)
    id
  }

  def registerAnonymous(window: Window[Buffer]): Long = {
    val id = nextId
    nextId += 1
    idToWindow.put(id, window)
    id
  }

  def unregister(name: String): Option[Window[Buffer]] = {
    windows.remove(name).map { window =>
      idToWindow.find(_._2 == window).foreach { case (id, _) =>
        idToWindow.remove(id)
      }
      window
    }
  }

  def unregisterById(id: Long): Option[Window[Buffer]] = {
    idToWindow.remove(id).map { window =>
      windows.find(_._2 == window).foreach { case (name, _) =>
        windows.remove(name)
      }
      window
    }
  }

  def get(name: String): Option[Window[Buffer]] = windows.get(name)

  def getById(id: Long): Option[Window[Buffer]] = idToWindow.get(id)

  def contains(name: String): Boolean = windows.contains(name)

  def containsId(id: Long): Boolean = idToWindow.contains(id)

  def findTiledWindows: Iterable[TiledWindow[Buffer]] = {
    idToWindow.values.collect { case tw: TiledWindow[Buffer] => tw }
  }

  def findFloatingWindows: Iterable[FloatingWindow[Buffer]] = {
    idToWindow.values.collect { case fw: FloatingWindow[Buffer] => fw }
  }

  def clear(): Unit = {
    windows.clear()
    idToWindow.clear()
    nextId = 0L
  }

  def size: Int = idToWindow.size

  def allWindows: Iterable[Window[Buffer]] = idToWindow.values

  def allNames: Iterable[String] = windows.keys
}

object WindowRegistry {
  private val global = new WindowRegistry[Any]()

  def getGlobal[Buffer]: WindowRegistry[Buffer] = global.asInstanceOf[WindowRegistry[Buffer]]

  def register[Buffer](name: String, window: Window[Buffer]): Long =
    global.asInstanceOf[WindowRegistry[Buffer]].register(name, window)

  def registerAnonymous[Buffer](window: Window[Buffer]): Long =
    global.asInstanceOf[WindowRegistry[Buffer]].registerAnonymous(window)

  def unregister[Buffer](name: String): Option[Window[Buffer]] =
    global.asInstanceOf[WindowRegistry[Buffer]].unregister(name)

  def unregisterById[Buffer](id: Long): Option[Window[Buffer]] =
    global.asInstanceOf[WindowRegistry[Buffer]].unregisterById(id)

  def get[Buffer](name: String): Option[Window[Buffer]] =
    global.asInstanceOf[WindowRegistry[Buffer]].get(name)

  def getById[Buffer](id: Long): Option[Window[Buffer]] =
    global.asInstanceOf[WindowRegistry[Buffer]].getById(id)

  def findTiledWindows[Buffer]: Iterable[TiledWindow[Buffer]] =
    global.asInstanceOf[WindowRegistry[Buffer]].findTiledWindows

  def findFloatingWindows[Buffer]: Iterable[FloatingWindow[Buffer]] =
    global.asInstanceOf[WindowRegistry[Buffer]].findFloatingWindows
}
