package dev.cwby.guitk.registry

import dev.cwby.guitk.components.{FloatingWindow, TiledWindow, Window}
import scala.collection.mutable

final class WindowRegistry {
  private val windows = mutable.Map[String, Window]()
  private val idToWindow = mutable.Map[Long, Window]()
  private var nextId: Long = 0L

  def register(name: String, window: Window): Long = {
    val id = nextId
    nextId += 1
    windows.put(name, window)
    idToWindow.put(id, window)
    id
  }

  def registerAnonymous(window: Window): Long = {
    val id = nextId
    nextId += 1
    idToWindow.put(id, window)
    id
  }

  def unregister(name: String): Option[Window] = {
    windows.remove(name).map { window =>
      idToWindow.find(_._2 == window).foreach { case (id, _) =>
        idToWindow.remove(id)
      }
      window
    }
  }

  def unregisterById(id: Long): Option[Window] = {
    idToWindow.remove(id).map { window =>
      windows.find(_._2 == window).foreach { case (name, _) =>
        windows.remove(name)
      }
      window
    }
  }

  def get(name: String): Option[Window] = windows.get(name)

  def getById(id: Long): Option[Window] = idToWindow.get(id)

  def contains(name: String): Boolean = windows.contains(name)

  def containsId(id: Long): Boolean = idToWindow.contains(id)

  def findTiledWindows: Iterable[TiledWindow] = {
    idToWindow.values.collect { case tw: TiledWindow => tw }
  }

  def findFloatingWindows: Iterable[FloatingWindow] = {
    idToWindow.values.collect { case fw: FloatingWindow => fw }
  }

  def clear(): Unit = {
    windows.clear()
    idToWindow.clear()
    nextId = 0L
  }

  def size: Int = idToWindow.size

  def allWindows: Iterable[Window] = idToWindow.values

  def allNames: Iterable[String] = windows.keys
}

object WindowRegistry {
  private val global = new WindowRegistry()

  def getGlobal: WindowRegistry = global

  def register(name: String, window: Window): Long =
    global.register(name, window)

  def registerAnonymous(window: Window): Long =
    global.registerAnonymous(window)

  def unregister(name: String): Option[Window] =
    global.unregister(name)

  def unregisterById(id: Long): Option[Window] =
    global.unregisterById(id)

  def get(name: String): Option[Window] =
    global.get(name)

  def getById(id: Long): Option[Window] =
    global.getById(id)

  def findTiledWindows: Iterable[TiledWindow] =
    global.findTiledWindows

  def findFloatingWindows: Iterable[FloatingWindow] =
    global.findFloatingWindows
}
