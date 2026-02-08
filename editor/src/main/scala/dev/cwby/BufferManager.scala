package dev.cwby

import dev.cwby.editor.core.FileChunkLoader
import dev.cwby.editor.core.TextBuffer
import dev.cwby.lsp.LSPManager
import dev.cwby.pkgs.PackageCategory.LSP
import dev.cwby.pkgs.PackageManager

import java.io.File
import scala.collection.mutable

object BufferManager {
  private val BUFFERS: mutable.Map[String, TextBuffer] = mutable.Map[String, TextBuffer]()

  def addEmptyBuffer(): TextBuffer = {
    new TextBuffer()
  }

  def openFileBuffer(filePath: String): TextBuffer = {
    openFileBuffer(new File(filePath))
  }

  def openFileBuffer(file: File): TextBuffer = {
    if (!file.exists()) {
      val textBuffer = new TextBuffer()
      textBuffer.file = file
      textBuffer.setFilepath(file.getAbsolutePath)
      textBuffer.setFileType(getFileExtension(textBuffer.filepath))
      BUFFERS(file.getAbsolutePath) = textBuffer
      return textBuffer
    }
    if (BUFFERS.contains(file.getAbsolutePath)) {
      return BUFFERS(file.getAbsolutePath)
    }
    val textBuffer = new TextBuffer(new FileChunkLoader(file, 64 * 1024))
    // TODO: listener autocmd before-open and after-open
    for (server <- PackageManager.filterCategory(LSP)) {
      if (server.trigger.filetypes.contains(textBuffer.getFileType())) {
        LSPManager.initializeServer(textBuffer, server)
      }
    }
    BUFFERS(file.getAbsolutePath) = textBuffer
    textBuffer
  }
}
