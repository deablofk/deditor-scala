package dev.cwby.graphics.layout.component

import dev.cwby.editor.TextBuffer
import dev.cwby.executeCommand
import dev.cwby.getFileExtension
import dev.cwby.getProjectPath
import dev.cwby.graphics.layout.FloatingWindow

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ListBuffer

class TelescopeWindow(x: Float, y: Float, width: Float, height: Float)
    extends FloatingWindow(x, y, width, height, 0.9f) {

  enum Mode {
    case Files
    case Grep
  }

  private val resultsBuffer = new TextBuffer()
  private val previewBuffer = new TextBuffer()
  private val componentImpl = TelescopeComponent(resultsBuffer, previewBuffer)

  private var mode: Mode                     = Mode.Files
  private var fileCandidates: Vector[String] = Vector.empty
  private var query: String                  = ""

  private val grepGeneration = AtomicInteger(0)

  this.component = componentImpl

  def openFilesPicker(): Unit = {
    mode = Mode.Files
    query = ""
    fileCandidates = loadProjectFiles()
    updateResultsFromQuery()
    updatePreview()
  }

  def openGrepPicker(): Unit = {
    mode = Mode.Grep
    query = ""
    fileCandidates = Vector.empty
    updateResults(List.empty)
    updatePreviewContent("", 0)
  }

  def onQueryChar(c: Char): Unit = {
    query = query + c
    onQueryChanged()
  }

  def onQueryBackspace(): Unit = {
    if (query.nonEmpty) {
      query = query.dropRight(1)
      onQueryChanged()
    }
  }

  private def onQueryChanged(): Unit = {
    mode match {
      case Mode.Files =>
        updateResultsFromQuery()
        updatePreview()
      case Mode.Grep =>
        runGrepAsync(query)
    }
  }

  def moveSelectionDown(): Unit = {
    resultsBuffer.synchronized {
      resultsBuffer.moveCursorDown()
    }
    updatePreview()
  }

  def moveSelectionUp(): Unit = {
    resultsBuffer.synchronized {
      resultsBuffer.moveCursorUp()
    }
    updatePreview()
  }

  override def onTrigger(): Unit = {
    mode match {
      case Mode.Files =>
        val path = selectedLine()
        if (path != null && path.nonEmpty) {
          close()
          executeCommand(s"edit $path")
        }
      case Mode.Grep =>
        val sel = selectedLine()
        if (sel != null && sel.nonEmpty) {
          val parts = sel.split(":", 4)
          if (parts.length >= 3) {
            val file  = parts(0)
            val line1 = parts(1).toIntOption.getOrElse(1)
            val col1  = parts(2).toIntOption.getOrElse(1)
            close()
            executeCommand(s"edit $file")
            val buffer = dev.cwby.guitk.renderer.OpenGLRenderer.getCurrentTextBuffer()
            if (buffer != null) {
              buffer.gotoPosition(Math.max(0, col1 - 1), Math.max(0, line1 - 1))
            }
          }
        }
    }
  }

  def selectedLine(): String = {
    resultsBuffer.synchronized {
      if (resultsBuffer.lines == null || resultsBuffer.lines.isEmpty) {
        return ""
      }
      val y = Math.max(0, Math.min(resultsBuffer.cursorY, resultsBuffer.lines.length - 1))
      resultsBuffer.lines(y).toString
    }
  }

  private def updateResultsFromQuery(): Unit = {
    val base     = fileCandidates
    val filtered =
      if (query.isEmpty) base
      else base.filter(fuzzyMatch(query, _))

    updateResults(filtered)
  }

  private def updateResults(lines: Seq[String]): Unit = {
    resultsBuffer.synchronized {
      val newLines =
        if (lines == null || lines.isEmpty) Array(StringBuilder(""))
        else lines.map(l => StringBuilder(l)).toArray

      resultsBuffer.setLines(newLines)
      resultsBuffer.moveCursor(0, 0)
    }
  }

  private def updatePreview(): Unit = {
    mode match {
      case Mode.Files =>
        val path = selectedLine()
        if (path != null && path.nonEmpty) {
          updatePreviewFile(path, None)
        } else {
          updatePreviewContent("", 0)
        }
      case Mode.Grep =>
        val sel = selectedLine()
        if (sel != null && sel.nonEmpty) {
          val parts = sel.split(":", 4)
          if (parts.length >= 2) {
            val file  = parts(0)
            val line1 = parts(1).toIntOption.getOrElse(1)
            updatePreviewFile(file, Some(line1))
          } else {
            updatePreviewContent("", 0)
          }
        } else {
          updatePreviewContent("", 0)
        }
    }
  }

  private def updatePreviewFile(filePath: String, highlightLine1: Option[Int]): Unit = {
    val file = File(filePath)
    if (!file.exists() || !file.isFile) {
      updatePreviewContent("", 0)
      return
    }

    val startLine1 = highlightLine1.map(l => Math.max(1, l - 80)).getOrElse(1)
    val endLine1   = highlightLine1.map(l => l + 160).getOrElse(200)

    val content = readFileLines(file, startLine1, endLine1)
    val cursorY = highlightLine1.map(l => Math.max(0, l - startLine1)).getOrElse(0)

    previewBuffer.setFilepath(file.getAbsolutePath)
    previewBuffer.setFileType(getFileExtension(file.getAbsolutePath))
    updatePreviewContent(content, cursorY)
  }

  private def updatePreviewContent(text: String, cursorY: Int): Unit = {
    previewBuffer.synchronized {
      val newLines =
        if (text == null || text.isEmpty) Array(StringBuilder(""))
        else text.split("\n", -1).map(l => StringBuilder(l))

      previewBuffer.setLines(newLines)
      val clampedY = Math.max(0, Math.min(cursorY, previewBuffer.lines.length - 1))
      previewBuffer.moveCursor(0, clampedY)
    }
  }

  private def readFileLines(file: File, startLine1: Int, endLine1: Int): String = {
    val reader = BufferedReader(java.io.FileReader(file))
    try {
      val sb           = StringBuilder()
      var line: String = reader.readLine()
      var lineNo       = 1
      while (line != null && lineNo <= endLine1) {
        if (lineNo >= startLine1) {
          sb.append(line).append("\n")
        }
        lineNo += 1
        line = reader.readLine()
      }
      sb.toString
    } finally {
      reader.close()
    }
  }

  private def runGrepAsync(q: String): Unit = {
    val gen = grepGeneration.incrementAndGet()
    if (q == null || q.isBlank) {
      updateResults(List.empty)
      updatePreviewContent("", 0)
      println("query is null or blank, skip grep")
      return
    }

    val root     = File(getProjectPath)
    val queryStr = q

    val t = Thread(() => {
      val results = runRipgrep(root, queryStr)
      if (grepGeneration.get() != gen) {
        println("grepGeneration get mismatched, discard results")
        return
      }
      println("Results received for query: " + queryStr + ", lines: " + results.length)
      updateResults(results)
      updatePreview()
    })
    t.setDaemon(true)
    t.setName("TelescopeGrep")
    t.start()
  }

  private def runRipgrep(root: File, q: String): List[String] = {
    try {
      val pb = ProcessBuilder(
        "rg",
        "--column",
        "--line-number",
        "--no-heading",
        "--smart-case",
        "--color=never",
        q,
        "."
      )
      println(pb.command().toString)
      pb.directory(root)
      pb.redirectErrorStream(true)
      val p = pb.start()
      p.getOutputStream.close()

      val reader       = BufferedReader(InputStreamReader(p.getInputStream))
      val out          = ListBuffer.empty[String]
      var line: String = reader.readLine()
      while (line != null) {
        if (line.nonEmpty) {
          out += line
        }
        line = reader.readLine()
      }
      reader.close()
      p.waitFor()
      print(s"Ripgrep returned: ${p.exitValue()}")
      out.toList
    } catch {
      case _: Throwable =>
        List.empty
    }
  }

  private def loadProjectFiles(): Vector[String] = {
    try {
      val root = File(getProjectPath)
      val pb   = ProcessBuilder("rg", "--files", "--color=never")
      pb.directory(root)
      pb.redirectErrorStream(true)
      val p = pb.start()
      p.getOutputStream.close()

      val reader       = BufferedReader(InputStreamReader(p.getInputStream))
      val out          = ListBuffer.empty[String]
      var line: String = reader.readLine()
      while (line != null) {
        if (line.nonEmpty) {
          out += line
        }
        line = reader.readLine()
      }
      reader.close()
      val code = p.waitFor()
      if (code != 0) {
        return walkFiles(root).toVector
      }
      out.toVector
    } catch {
      case _: Throwable =>
        walkFiles(File(getProjectPath)).toVector
    }
  }

  private def walkFiles(root: File): ListBuffer[String] = {
    val out                 = ListBuffer.empty[String]
    def walk(d: File): Unit = {
      if (d == null || !d.exists()) {
        return
      }
      if (d.isFile) {
        out += d.getAbsolutePath
      } else if (d.isDirectory) {
        val files = d.listFiles()
        if (files != null) {
          files.foreach(walk)
        }
      }
    }
    walk(root)
    out
  }

  private def fuzzyMatch(q: String, target: String): Boolean = {
    var qi = 0
    val qq = if (q == null) "" else q
    if (qq.isEmpty) {
      return true
    }
    val t     = if (target == null) "" else target
    val chars = t.toCharArray
    var i     = 0
    while (i < chars.length && qi < qq.length) {
      if (chars(i) == qq.charAt(qi)) {
        qi += 1
      }
      i += 1
    }
    qi == qq.length
  }

  
}
