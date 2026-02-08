package dev.cwby.config

import scala.collection.mutable

final class Cursor(
    var blink: Long = 500,
    var color: String = "#77FFFFFF",
    var select: String = "#8066B9FF"
)

final class Theme(
    var background: String = "#FF1B1B1B",
    var numberColor: String = "#FF666666"
)

final class FontConfig(
    var family: String = "Iosevka Nerd Font Mono",
    var size: Int = 32
)

final class TreeSitterGrammarConfig(
    var language: String = "",
    var repo: String = "",
    val filetypes: mutable.Set[String] = mutable.Set.empty
)

final class TreeSitterParsersConfig(
    var mode: String = "on_demand",
    val grammars: mutable.ArrayBuffer[TreeSitterGrammarConfig] = mutable.ArrayBuffer.empty
)

final class EditorConfig(
    val cursor: Cursor,
    val font: FontConfig,
    val theme: Theme,
    val treesitter: mutable.Map[String, Int],
    val treesitterParsers: TreeSitterParsersConfig
)
