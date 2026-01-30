package dev.cwby.editor.commands

import dev.cwby.WindowManager
import dev.cwby.editor.core.TextBuffer
import dev.cwby.getFileExtension
import dev.cwby.editor.components.TextComponent

import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter

def runSave(args: Array[String]): Boolean = {
  val buffer = WindowManager.getCurrentWindow.component.asInstanceOf[TextComponent].getBuffer

  buffer match
    case textBuffer: TextBuffer =>
      val fileFromArgs =
        if args != null && args.nonEmpty && args(0) != null && args(0).nonEmpty then new File(args(0)).getAbsoluteFile
        else null

      val file = if textBuffer.file != null then textBuffer.file else fileFromArgs

      if file != null then
        try
          val parent = file.getParentFile
          if parent != null && !parent.exists() then parent.mkdirs()

          val writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, false)))
          try
            val content = textBuffer.getLines().map(_.toString).mkString("\n")
            writer.write(content)
            writer.flush()
          finally writer.close()

          if textBuffer.file == null then
            textBuffer.file = file
            textBuffer.setFilepath(file.getAbsolutePath)
            textBuffer.setFileType(getFileExtension(textBuffer.filepath))
        catch
          case e: IOException =>
            throw new RuntimeException(e)

  true
}
