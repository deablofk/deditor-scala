package dev.cwby.editor.registry

import dev.cwby.guitk.events.EventDispatcher
import dev.cwby.editor.events.{CommandExecuteEvent, CommandCompleteEvent, CommandErrorEvent}
import scala.collection.mutable

type CommandFunction = Array[String] => Boolean

final case class CommandMetadata(
    name: String,
    aliases: Array[String],
    description: String,
    function: CommandFunction
)

final class CommandRegistry {
  private val commands = mutable.Map[String, CommandMetadata]()
  private val aliasMap = mutable.Map[String, String]()

  def register(
      name: String,
      function: CommandFunction,
      aliases: Array[String] = Array.empty,
      description: String = ""
  ): Unit = {
    val metadata = CommandMetadata(name, aliases, description, function)
    commands.put(name.toLowerCase, metadata)
    aliases.foreach { alias =>
      aliasMap.put(alias.toLowerCase, name.toLowerCase)
    }
  }

  def registerMetadata(metadata: CommandMetadata): Unit = {
    commands.put(metadata.name.toLowerCase, metadata)
    metadata.aliases.foreach { alias =>
      aliasMap.put(alias.toLowerCase, metadata.name.toLowerCase)
    }
  }

  def unregister(name: String): Option[CommandMetadata] = {
    val normalized = name.toLowerCase
    commands.remove(normalized).map { metadata =>
      metadata.aliases.foreach { alias =>
        aliasMap.remove(alias.toLowerCase)
      }
      metadata
    }
  }

  def get(name: String): Option[CommandFunction] = {
    val normalized = name.toLowerCase
    val actualName = aliasMap.getOrElse(normalized, normalized)
    commands.get(actualName).map(_.function)
  }

  def getMetadata(name: String): Option[CommandMetadata] = {
    val normalized = name.toLowerCase
    val actualName = aliasMap.getOrElse(normalized, normalized)
    commands.get(actualName)
  }

  def contains(name: String): Boolean = {
    val normalized = name.toLowerCase
    commands.contains(normalized) || aliasMap.contains(normalized)
  }

  def execute(name: String, args: Array[String]): Boolean = {
    get(name) match {
      case Some(function) =>
        EventDispatcher.dispatch(CommandExecuteEvent(name, args))
        try {
          val result = function(args)
          EventDispatcher.dispatch(CommandCompleteEvent(name, args, result))
          result
        } catch {
          case e: Exception =>
            EventDispatcher.dispatch(CommandErrorEvent(name, args, e.getMessage))
            System.err.println(s"Command execution error: $name -> ${e.getMessage}")
            false
        }
      case None =>
        false
    }
  }

  def executeFullCommand(fullCommand: String): Boolean = {
    val trimmed = fullCommand.trim
    if trimmed.isEmpty then return false

    val args = trimmed.split(" ")
    execute(args(0), args)
  }

  def clear(): Unit = {
    commands.clear()
    aliasMap.clear()
  }

  def size: Int = commands.size

  def allCommands: Iterable[CommandMetadata] = commands.values

  def allNames: Iterable[String] = commands.keys

  def findByPrefix(prefix: String): Iterable[CommandMetadata] = {
    val normalized = prefix.toLowerCase
    commands.values.filter { metadata =>
      metadata.name.toLowerCase.startsWith(normalized) ||
      metadata.aliases.exists(_.toLowerCase.startsWith(normalized))
    }
  }
}

object CommandRegistry {
  private val global = new CommandRegistry()

  def getGlobal: CommandRegistry = global

  def register(
      name: String,
      function: CommandFunction,
      aliases: Array[String] = Array.empty,
      description: String = ""
  ): Unit = global.register(name, function, aliases, description)

  def registerMetadata(metadata: CommandMetadata): Unit =
    global.registerMetadata(metadata)

  def unregister(name: String): Option[CommandMetadata] =
    global.unregister(name)

  def get(name: String): Option[CommandFunction] =
    global.get(name)

  def getMetadata(name: String): Option[CommandMetadata] =
    global.getMetadata(name)

  def execute(name: String, args: Array[String]): Boolean =
    global.execute(name, args)

  def executeFullCommand(fullCommand: String): Boolean =
    global.executeFullCommand(fullCommand)
}
