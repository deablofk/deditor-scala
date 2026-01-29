package dev.cwby.graphics.layout.component

import dev.cwby.BufferManager
import dev.cwby.editor.TextBuffer
import dev.cwby.executeCommand
import dev.cwby.getProjectPath
import dev.cwby.guitk.components.FloatingWindow
import dev.cwby.graphics.layout.component.TextComponent

import java.io.File
import scala.collection.mutable.ListBuffer

class DiredWindow(x: Float, y: Float, width: Float, height: Float) extends FloatingWindow(x, y, width, height, 0.9f) {

  private val buffer: TextBuffer                     = BufferManager.addEmptyBuffer()
  private var currentDir: File                       = new File(getProjectPath).getAbsoluteFile
  private val entries: ListBuffer[File]              = ListBuffer.empty
  private val headerLines: Int                       = 1
  private var lastRenderedEntryNames: Vector[String] = Vector.empty

  this.component = new TextComponent().setBuffer(buffer)
  refresh()

  def openPath(path: String): Unit = {
    if (path == null || path.isEmpty) {
      return
    }

    val f   = new File(path).getAbsoluteFile
    val dir = if (f.exists() && f.isDirectory) f else f.getParentFile
    if (dir != null && dir.exists() && dir.isDirectory) {
      currentDir = dir
      refresh()
    }
  }

  def refresh(): Unit = {
    val newLines = scala.collection.mutable.ArrayBuffer[StringBuilder]()
    entries.clear()
    lastRenderedEntryNames = Vector.empty

    newLines += new StringBuilder(formatHeader(currentDir))

    val parent = currentDir.getParentFile

    val files           = Option(currentDir.listFiles()).map(_.toList).getOrElse(Nil)
    val (dirs, regular) = files.partition(_.isDirectory)

    val sortedDirs  = dirs.sortBy(_.getName.toLowerCase)
    val sortedFiles = regular.sortBy(_.getName.toLowerCase)

    val allEntries: ListBuffer[File] = ListBuffer.empty
    if (parent != null) {
      allEntries += parent
    }
    allEntries ++= sortedDirs
    allEntries ++= sortedFiles

    val maxSizeWidth =
      if (allEntries.isEmpty) 1
      else allEntries.map(e => safeLength(e).toString.length).max

    var i = 0
    while (i < allEntries.length) {
      val entry       = allEntries(i)
      val displayName = if (parent != null && i == 0) ".." else entry.getName
      newLines += new StringBuilder(formatLine(entry, displayName, maxSizeWidth))
      entries += entry
      lastRenderedEntryNames = lastRenderedEntryNames :+ displayName
      i += 1
    }

    buffer.setLines(newLines.toArray)
    buffer.moveCursor(0, headerLines)
  }

  def applyEditsToFilesystem(): Unit = {
    val desired   = readDesiredEntryNames()
    val originals = originalEditableNames()

    if (desired.isEmpty && originals.isEmpty) {
      return
    }

    val desiredNamesSet  = desired.map(_._1).toSet
    val originalNamesSet = originals.toSet

    // Rename detection by position: if a line changed to a brand-new name,
    // and the original name on that line was removed, treat it as a rename.
    val renamePairs = scala.collection.mutable.ArrayBuffer[(String, String, Boolean)]()

    val minLen = Math.min(originals.length, desired.length)
    var i      = 0
    while (i < minLen) {
      val originalName                = originals(i)
      val (desiredName, desiredIsDir) = desired(i)
      if (
        originalName != desiredName && !originalNamesSet
          .contains(desiredName) && !desiredNamesSet.contains(originalName)
      ) {
        renamePairs += ((originalName, desiredName, desiredIsDir))
      }
      i += 1
    }

    // Apply renames first.
    renamePairs.foreach { case (fromName, toName, toIsDir) =>
      val from = new File(currentDir, fromName)
      val to   = new File(currentDir, toName)

      if (!from.exists()) {
        ()
      } else if (to.exists()) {
        ()
      } else {
        if (toIsDir && !from.isDirectory) {
          ()
        } else if (!toIsDir && from.isDirectory) {
          ()
        } else {
          from.renameTo(to)
        }
      }
    }

    val originalsAfterRenames = originals.toSet -- renamePairs.map(_._1)
    val desiredAfterRenames   = desired.map(_._1).toSet -- renamePairs.map(_._2)

    // Deletes: original names that no longer exist in desired.
    (originalsAfterRenames -- desiredAfterRenames).foreach { name =>
      val f = new File(currentDir, name)
      if (f.exists()) {
        deleteRecursively(f)
      }
    }

    // Creates: desired names that did not exist in original.
    desired.foreach { case (name, isDir) =>
      if (!originalNamesSet.contains(name)) {
        val f = new File(currentDir, name)
        if (!f.exists()) {
          if (isDir) {
            f.mkdirs()
          } else {
            val parent = f.getParentFile
            if (parent != null) {
              parent.mkdirs()
            }
            f.createNewFile()
          }
        }
      }
    }

    refresh()
  }

  private def originalEditableNames(): Vector[String] = {
    // Skip the ".." entry (parent) if present as first element.
    if (lastRenderedEntryNames.nonEmpty && lastRenderedEntryNames.headOption.contains("..")) {
      lastRenderedEntryNames.drop(1)
    } else {
      lastRenderedEntryNames
    }
  }

  private def readDesiredEntryNames(): Vector[(String, Boolean)] = {
    if (buffer == null || buffer.lines == null) {
      return Vector.empty
    }

    val lines = buffer.lines.drop(headerLines).toVector
    val raw   = lines.map(_.toString.trim).filter(_.nonEmpty)

    val namesOnly = raw.flatMap(extractDisplayName)

    // Skip the ".." entry if it exists in the edited view.
    val filtered = if (namesOnly.headOption.contains("..")) namesOnly.drop(1) else namesOnly

    filtered.map { n =>
      val isDir      = n.endsWith("/")
      val normalized = if (isDir) n.dropRight(1) else n
      (normalized, isDir)
    }
  }

  private def extractDisplayName(line: String): Option[String] = {
    if (line == null) {
      return None
    }

    val trimmed = line.trim
    if (trimmed.isEmpty) {
      return None
    }

    // Dired line format: "<perms> <links> <user> <group> <size> <date> <name>"
    // We treat the last whitespace-delimited token as the editable name.
    val parts = trimmed.split("\\s+")
    if (parts.isEmpty) None
    else Some(parts.last)
  }

  private def deleteRecursively(f: File): Unit = {
    if (f == null || !f.exists()) {
      return
    }
    if (f.isDirectory) {
      val children = Option(f.listFiles()).getOrElse(Array.empty[File])
      children.foreach(deleteRecursively)
    }
    f.delete()
  }

  private def formatHeader(dir: File): String = {
    val path = if (dir == null) "" else dir.getAbsolutePath
    s"$path"
  }

  private def formatLine(f: File, displayName: String, sizeWidth: Int): String = {
    val perms = formatPermissions(f)
    val links = "1"
    val user  = Option(System.getProperty("user.name")).getOrElse("?")
    val group = user
    val size  = leftPad(safeLength(f).toString, sizeWidth)
    val date  = formatDiredDate(f.lastModified())
    s"$perms $links $user $group $size $date $displayName"
  }

  private def safeLength(f: File): Long = {
    if (f == null) 0L
    else {
      try f.length()
      catch {
        case _: Throwable => 0L
      }
    }
  }

  private def formatPermissions(f: File): String = {
    if (f == null) {
      return "----------"
    }

    val t = if (f.isDirectory) 'd' else '-'
    val r = if (f.canRead) 'r' else '-'
    val w = if (f.canWrite) 'w' else '-'
    val x = if (f.canExecute) 'x' else '-'

    // Best-effort: File API doesn't expose POSIX perms, so mirror owner perms to group/other.
    s"$t$r$w$x$r-$x$r-$x"
  }

  private def formatDiredDate(millis: Long): String = {
    val s = new java.util.Date(millis).toString
    if (s == null || s.isEmpty) {
      return "??? ?? ??:??"
    }

    val parts = s.split(" ")
    if (parts.length < 4) {
      return "??? ?? ??:??"
    }

    val month         = parts(1)
    val day           = leftPad(parts(2), 2)
    val time          = parts(3)
    val timeFormatted = if (time.length >= 5) time.substring(0, 5) else time

    s"$month $day $timeFormatted"
  }

  private def leftPad(s: String, width: Int): String = {
    val str = if (s == null) "" else s
    if (str.length >= width) str
    else (" " * (width - str.length)) + str
  }

  override def onTrigger(): Unit = {
    if (buffer.cursorY < headerLines) {
      return
    }

    val file = selectedFile()
    if (file == null) {
      return
    }

    if (file.exists() && file.isDirectory) {
      currentDir = file.getAbsoluteFile
      refresh()
    } else {
      close()
      executeCommand(s"edit ${file.getAbsolutePath}")
    }
  }

  def selectedFile(): File = {
    val y = buffer.cursorY - headerLines
    if (y < 0 || y >= entries.length) {
      return null
    }
    entries(y)
  }
}
