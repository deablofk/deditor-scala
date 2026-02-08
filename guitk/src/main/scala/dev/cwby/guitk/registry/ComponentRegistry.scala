package dev.cwby.guitk.registry

import dev.cwby.guitk.components.IComponent
import scala.collection.mutable

final class ComponentRegistry {
  private val components = mutable.Map[String, IComponent]()
  private val idToComponent = mutable.Map[Long, IComponent]()
  private var nextId: Long = 0L

  def register(name: String, component: IComponent): Long = {
    val id = nextId
    nextId += 1
    components.put(name, component)
    idToComponent.put(id, component)
    id
  }

  def registerAnonymous(component: IComponent): Long = {
    val id = nextId
    nextId += 1
    idToComponent.put(id, component)
    id
  }

  def unregister(name: String): Option[IComponent] = {
    components.remove(name).map { component =>
      idToComponent.find(_._2 == component).foreach { case (id, _) =>
        idToComponent.remove(id)
      }
      component
    }
  }

  def unregisterById(id: Long): Option[IComponent] = {
    idToComponent.remove(id).map { component =>
      components.find(_._2 == component).foreach { case (name, _) =>
        components.remove(name)
      }
      component
    }
  }

  def get(name: String): Option[IComponent] = components.get(name)

  def getById(id: Long): Option[IComponent] = idToComponent.get(id)

  def contains(name: String): Boolean = components.contains(name)

  def containsId(id: Long): Boolean = idToComponent.contains(id)

  def clear(): Unit = {
    components.clear()
    idToComponent.clear()
    nextId = 0L
  }

  def size: Int = idToComponent.size

  def allComponents: Iterable[IComponent] = idToComponent.values

  def allNames: Iterable[String] = components.keys
}

object ComponentRegistry {
  private val global = new ComponentRegistry()

  def getGlobal: ComponentRegistry = global

  def register(name: String, component: IComponent): Long =
    global.register(name, component)

  def registerAnonymous(component: IComponent): Long =
    global.registerAnonymous(component)

  def unregister(name: String): Option[IComponent] =
    global.unregister(name)

  def unregisterById(id: Long): Option[IComponent] =
    global.unregisterById(id)

  def get(name: String): Option[IComponent] =
    global.get(name)

  def getById(id: Long): Option[IComponent] =
    global.getById(id)
}
