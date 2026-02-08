package dev.cwby

import dev.cwby.editor.commands._

import scala.collection.mutable

type ICommand = Array[String] => Boolean

final case class CommandHandlerState(commands: mutable.Map[String, ICommand], buffer: StringBuilder)

val commandHandlerState: CommandHandlerState = CommandHandlerState(
  commands = mutable.Map(
    "quit"            -> runQuit,
    "q"               -> runQuit,
    "edit"            -> runEdit,
    "e"               -> runEdit,
    "save"            -> runSave,
    "w"               -> runSave,
    "vs"              -> runVerticalSplit,
    "s"               -> runSplit,
    "term"            -> runTerminal,
    "terminal"        -> runTerminal,
    "telescope-files" -> runTelescopeFiles,
    "telescope-grep"  -> runTelescopeGrep,
    "dired"           -> runDired,
    "pkgman"          -> runPkgMan
  ),
  buffer = StringBuilder()
)

@inline def getCommand(name: String): Option[ICommand] = {
  commandHandlerState.commands.get(name.toLowerCase)
}

@inline def setCommand(name: String, command: ICommand): Unit = {
  commandHandlerState.commands.put(name.toLowerCase, command): Unit
}

@inline private def runCommand(cmd: ICommand, args: Array[String], fullCommand: String): Boolean = {
  try cmd(args)
  catch {
    case e: Exception =>
      println(s"Command error: $fullCommand -> ${e.getMessage}")
      false
  }
}

def executeCommand(fullCommand: String): Boolean = {
  val input = fullCommand.trim
  if (input.isEmpty) return false

  val args = fullCommand.split(" ")
  val name = args(0).toLowerCase

  getCommand(name).exists(runCommand(_, args, fullCommand))
}

@inline def clearCommandBuffer(): Unit = {
  commandHandlerState.buffer.setLength(0)
}

@inline def getCommandBuffer: String = {
  commandHandlerState.buffer.toString()
}

@inline def appendCommandBuffer(c: Char): Unit = {
  commandHandlerState.buffer.append(c)
}
