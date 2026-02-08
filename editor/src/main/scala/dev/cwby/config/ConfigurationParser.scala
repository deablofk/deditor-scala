package dev.cwby.config

import dev.cwby.expandHome
import dev.cwby.toml.Toml
import dev.cwby.toml.TomlType
import dev.cwby.toml.TomlValue
import dev.cwby.toml.asLong
import dev.cwby.toml.asString
import dev.cwby.toml.get

import java.io.File
import java.io.PrintWriter
import scala.collection.mutable

@inline def readConfiguration(): Option[EditorConfig] = {
  val possiblePaths = Array(
    "~/.config/deditor/config.toml",
    "~/.deditor/config.toml",
    "config/config.toml"
  )

  val resolvedPaths = possiblePaths.map(expandHome)
  val existingPath  = resolvedPaths.find(p => File(p).exists())
  val expectedPath  = resolvedPaths.head

  existingPath match {
    case Some(p) =>
      try {
        val root: TomlValue      = Toml.parse(File(p))
        val config: EditorConfig = loadEditorConfig(root)
        Some(config)
      } catch {
        case _: Throwable =>
          val config = defaultConfig()
          writeDefaultConfig(expectedPath, config)
          Some(config)
      }
    case None =>
      val config = defaultConfig()
      writeDefaultConfig(expectedPath, config)
      Some(config)
  }
}

@inline private def defaultConfig(): EditorConfig = {
  val ts = mutable.Map[String, Int]("default" -> 0xffffffff)
  EditorConfig(
    cursor = Cursor(),
    font = FontConfig(),
    theme = Theme(),
    treesitter = ts,
    treesitterParsers = TreeSitterParsersConfig()
  )
}

@inline private def writeDefaultConfig(targetPath: String, config: EditorConfig): Unit = {
  if (targetPath == null || targetPath.isEmpty) return

  val file   = File(targetPath)
  val parent = file.getParentFile
  if (parent != null && !parent.exists()) parent.mkdirs()

  val writer = PrintWriter(file)
  try {
    writer.write(toToml(config))
  } finally {
    writer.close()
  }
}

@inline private def toToml(config: EditorConfig): String = {
  val sb = new StringBuilder(256)

  sb.append("[cursor]\n")
  sb.append(s"blink = ${config.cursor.blink}\n")
  sb.append(s"color = \"${config.cursor.color}\"\n")
  sb.append(s"select = \"${config.cursor.select}\"\n\n")

  sb.append("[font]\n")
  sb.append(s"family = \"${config.font.family}\"\n")
  sb.append(s"size = ${config.font.size}\n\n")

  sb.append("[theme]\n")
  sb.append(s"background = \"${config.theme.background}\"\n")
  sb.append(s"numberColor = \"${config.theme.numberColor}\"\n\n")

  sb.append("[treesitter]\n")
  @inline def writeTsColor(key: String, fallback: Int): Unit = {
    val v = config.treesitter.getOrElse(key, fallback)
    sb.append(s"$key = \"#${intToHexArgb(v)}\"\n")
  }

  writeTsColor("default", 0xffa9b7c6)
  writeTsColor("keyword", 0xffffc66d)
  writeTsColor("type", 0xff4e90f0)
  writeTsColor("function", 0xff9876aa)
  writeTsColor("variable", 0xff9876aa)
  writeTsColor("string", 0xff6a8759)
  writeTsColor("number", 0xff6897bb)
  writeTsColor("comment", 0xff808080)
  writeTsColor("operator", 0xffcc7832)
  writeTsColor("punctuation", 0xffcc7832)
  writeTsColor("attribute", 0xffbbb529)
  writeTsColor("markup.heading", 0xffffc66d)
  writeTsColor("markup.bold", 0xffffc66d)
  writeTsColor("markup.italic", 0xff9876aa)
  writeTsColor("markup.link", 0xff4e90f0)

  sb.append("\n[treesitterParsers]\n")
  sb.append(s"mode = \"${config.treesitterParsers.mode}\"\n")
  val grammars = config.treesitterParsers.grammars.toList
  var i        = 0
  while (i < grammars.length) {
    val g = grammars(i)
    if (g != null) {
      sb.append("\n[[treesitterParsers.grammars]]\n")
      sb.append(s"language = \"${g.language}\"\n")
      if (g.repo != null && g.repo.nonEmpty) sb.append(s"repo = \"${g.repo}\"\n")
      if (g.filetypes != null && g.filetypes.nonEmpty) {
        sb.append("filetypes = [")
        val fts = g.filetypes.toList.sorted
        var j   = 0
        while (j < fts.length) {
          if (j > 0) sb.append(", ")
          sb.append('"').append(fts(j)).append('"')
          j += 1
        }
        sb.append("]\n")
      }
    }
    i += 1
  }
  sb.toString()
}

@inline private def intToHexArgb(color: Int): String = {
  val hex    = java.lang.Integer.toHexString(color)
  val padded = "0" * (8 - hex.length) + hex
  padded.toUpperCase
}

@inline private def loadCursor(v: TomlValue): Cursor = {
  val c = Cursor()

  if (v != null && v.kind == TomlType.TABLE) {
    val blink = v.get("blink")
    if (blink != null) c.blink = blink.asLong

    val color = v.get("color")
    if (color != null) c.color = color.asString

    val select = v.get("select")
    if (select != null) c.select = select.asString
  }

  c
}

@inline private def loadTheme(v: TomlValue): Theme = {
  val t = Theme()

  if (v != null && v.kind == TomlType.TABLE) {
    val bg = v.get("background")
    if (bg != null) t.background = bg.asString

    val num = v.get("numberColor")
    if (num != null) t.numberColor = num.asString
  }

  t
}

@inline private def loadFont(v: TomlValue): FontConfig = {
  val f = FontConfig()

  if (v != null && v.kind == TomlType.TABLE) {
    val family = v.get("family")
    if (family != null) f.family = family.asString

    val size = v.get("size")
    if (size != null) f.size = size.asLong.toInt
  }

  f
}

@inline def loadTreeSitter(v: TomlValue): mutable.Map[String, Int] = {
  val theme = mutable.Map[String, Int]()

  def flatten(prefix: String, value: TomlValue): Unit = {
    if (value.kind == TomlType.STRING) {
      theme.put(prefix, hexToInt(value.asString))
    } else if (value.kind == TomlType.TABLE) {
      val it = value.tbl.iterator
      while (it.hasNext) {
        val (k, tv) = it.next()
        val key     = if (prefix.isEmpty) k else prefix + "." + k
        flatten(key, tv)
      }
    }
  }

  if (v != null && v.kind == TomlType.TABLE) {
    val it = v.tbl.iterator
    while (it.hasNext) {
      val (k, tv) = it.next()
      flatten(k, tv)
    }
  }

  theme
}

@inline private def loadTreeSitterParsers(v: TomlValue): TreeSitterParsersConfig = {
  val cfg = TreeSitterParsersConfig()

  if (v != null && v.kind == TomlType.TABLE) {
    val mode = v.get("mode")
    if (mode != null && mode.kind == TomlType.STRING) {
      cfg.mode = mode.asString
    }

    val grammars = v.get("grammars")
    if (grammars != null && grammars.kind == TomlType.ARRAY && grammars.arr != null) {
      val it = grammars.arr.iterator
      while (it.hasNext) {
        val tv = it.next()
        if (tv == null) {
          // skip
        } else if (tv.kind == TomlType.TABLE) {
          val g = TreeSitterGrammarConfig()

          val lang = tv.get("language")
          if (lang != null && lang.kind == TomlType.STRING) g.language = lang.asString

          val repo = tv.get("repo")
          if (repo != null && repo.kind == TomlType.STRING) g.repo = repo.asString

          val fts = tv.get("filetypes")
          if (fts != null && fts.kind == TomlType.ARRAY && fts.arr != null) {
            val it2 = fts.arr.iterator
            while (it2.hasNext) {
              val ft = it2.next()
              if (ft != null && ft.kind == TomlType.STRING) {
                val raw = ft.asString
                if (raw != null && raw.nonEmpty) {
                  val cleaned = if (raw.startsWith(".")) raw.substring(1) else raw
                  if (cleaned.nonEmpty) g.filetypes.add(cleaned)
                }
              }
            }
          }

          if (g.language != null && g.language.nonEmpty) cfg.grammars.addOne(g)
        } else if (tv.kind == TomlType.STRING) {
          // Legacy format: grammars = ["java", "markdown"]
          val name = tv.asString
          if (name != null && name.nonEmpty) {
            val g = TreeSitterGrammarConfig()
            g.language = name
            cfg.grammars.addOne(g)
          }
        }
      }
    }
  }

  cfg
}

def loadEditorConfig(root: TomlValue): EditorConfig = {
  val cursor = loadCursor(root.get("cursor"))
  val font   = loadFont(root.get("font"))
  val theme  = loadTheme(root.get("theme"))
  val ts     = loadTreeSitter(root.get("treesitter"))
  val tsp    = loadTreeSitterParsers(root.get("treesitterParsers"))

  if (!ts.contains("default")) ts.put("default", 0xffffffff)
  EditorConfig(cursor = cursor, font = font, theme = theme, treesitter = ts, treesitterParsers = tsp)
}

@inline def hexToInt(hexColor: String): Int = {
  val s =
    if (hexColor.startsWith("#")) hexColor.substring(1)
    else hexColor

  val len = s.length
  if (len != 6 && len != 8) return -1

  var value = 0
  var i     = 0

  while (i < len) {
    val c     = s.charAt(i)
    val digit =
      if (c >= '0' && c <= '9') c - '0'
      else if (c >= 'a' && c <= 'f') c - 'a' + 10
      else if (c >= 'A' && c <= 'F') c - 'A' + 10
      else return -1

    value = (value << 4) | digit
    i += 1
  }

  if (len == 6) value |= 0xff000000
  value
}
