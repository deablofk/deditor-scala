package dev.cwby

import dev.cwby.config.Cursor
import dev.cwby.config.EditorConfig
import dev.cwby.config.FontConfig
import dev.cwby.config.Theme
import dev.cwby.config.TreeSitterParsersConfig
import dev.cwby.config.readConfiguration
import dev.cwby.editor.TextInteractionMode
import dev.cwby.graphics.Engine
import dev.cwby.treesitter.TreeSitterGrammarManager

import scala.collection.mutable

private val editorConfig = {
  val cfg = readConfiguration().getOrElse(
    EditorConfig(
      cursor = Cursor(),
      font = FontConfig(),
      theme = Theme(),
      treesitter = mutable.Map("default" -> 0xffffffff),
      treesitterParsers = TreeSitterParsersConfig()
    )
  )
  TreeSitterGrammarManager.preinstallEnabled(cfg.treesitterParsers)
  cfg
}
private var MODE                = TextInteractionMode.NAVIGATION
private var projectPath: String = System.getProperty("user.dir")

inline def setBufferMode(mode: TextInteractionMode): Unit =
  MODE = mode

inline def getBufferMode: TextInteractionMode =
  MODE

inline def getConfig: EditorConfig =
  editorConfig

inline def getProjectPath: String =
  projectPath

object Deditor {

  def main(args: Array[String]): Unit = {
    Engine.run()
  }

}
