package dev.cwby.editor

import java.io.File
import java.io.IOException
import scala.collection.mutable.ListBuffer

class FileChunkLoader(private val file: File, private val chunkSize: Int) {
  private var currentOffset: Long = 0

  def loadChunckByLineOffset(): ListBuffer[StringBuilder] = {
    val chunk = ListBuffer[StringBuilder]()
    try {
      val source = scala.io.Source.fromFile(file)
      try {
        val lines     = source.getLines().drop(currentOffset.toInt).take(chunkSize)
        var linesRead = 0
        lines.foreach { line =>
          chunk += new StringBuilder(line)
          linesRead += 1
        }
        currentOffset += linesRead
      } finally {
        source.close()
      }
    } catch {
      case e: IOException =>
        throw new RuntimeException(e)
    }
    chunk
  }

  def loadChunkByByteSize(): ListBuffer[StringBuilder] = {
    val chunk = ListBuffer[StringBuilder]()
    try {
      val source = scala.io.Source.fromFile(file)
      try {
        val content = source.mkString
        if (currentOffset >= content.length) {
          return chunk
        }

        val endOffset   = Math.min(currentOffset + chunkSize, content.length)
        val partialData = content.substring(currentOffset.toInt, endOffset.toInt)
        val lines       = partialData.split("\n")

        for (i <- 0 until lines.length) {
          if (i == lines.length - 1 && endOffset < content.length) {
            currentOffset += lines(i).getBytes.length
          } else {
            chunk += new StringBuilder(lines(i))
          }
        }
        currentOffset = endOffset
      } finally {
        source.close()
      }
    } catch {
      case e: IOException =>
        throw new RuntimeException(e)
    }
    chunk
  }

  def resetOffset(): Unit = {
    currentOffset = 0
  }

  @throws[IOException]
  def hasMoreData(): Boolean = {
    currentOffset < file.length()
  }

  def getFile(): File = {
    file
  }
}
