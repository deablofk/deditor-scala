package dev.cwby.graphics.opengl

import dev.cwby.bindings.GLConstants._
import dev.cwby.bindings.gl._

import scala.scalanative.unsafe._

class Texture(val width: Int, val height: Int, data: Ptr[Byte], val isRGBA: Boolean = false) {
  private val format         = if (isRGBA) GL_RGBA else GL_RED
  private val internalFormat = if (isRGBA) GL_RGBA else GL_RED

  private val id: Int = {
    import scala.scalanative.libc.stdlib._
    val ids = malloc(4).asInstanceOf[Ptr[Int]]
    glGenTextures(1, ids)
    val textureId = ids(0)
    free(ids.asInstanceOf[Ptr[Byte]])
    textureId
  }

  bind()

  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)

  glPixelStorei(GL_UNPACK_ALIGNMENT, 1)
  glTexImage2D(GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, GL_UNSIGNED_BYTE, data)

  unbind()

  def bind(): Unit = {
    glBindTexture(GL_TEXTURE_2D, id)
  }

  def unbind(): Unit = {
    glBindTexture(GL_TEXTURE_2D, 0)
  }

  def updateRegion(x: Int, y: Int, w: Int, h: Int, data: Ptr[Byte], stride: Int, isColor: Boolean = false): Unit = {
    bind()

    glPixelStorei(GL_UNPACK_ALIGNMENT, 1)
    val updateFormat   = if (isColor) GL_RGBA else GL_RED
    val bytesPerPixel  = if (isColor) 4 else 1
    val expectedStride = w * bytesPerPixel

    if (stride == expectedStride) {
      glTexSubImage2D(GL_TEXTURE_2D, 0, x, y, w, h, updateFormat, GL_UNSIGNED_BYTE, data)
    } else {
      import scala.scalanative.libc.stdlib._
      val tempBuffer = malloc(w * h * bytesPerPixel).asInstanceOf[Ptr[Byte]]
      var srcY       = 0
      while (srcY < h) {
        val srcOffset = srcY * stride
        val dstOffset = srcY * w * bytesPerPixel
        var col       = 0
        while (col < w * bytesPerPixel) {
          tempBuffer(dstOffset + col) = data(srcOffset + col)
          col += 1
        }
        srcY += 1
      }
      glTexSubImage2D(GL_TEXTURE_2D, 0, x, y, w, h, updateFormat, GL_UNSIGNED_BYTE, tempBuffer)
      free(tempBuffer)
    }

    unbind()
  }

  def cleanup(): Unit = {
    import scala.scalanative.libc.stdlib._
    val ids = malloc(4).asInstanceOf[Ptr[Int]]
    ids(0) = id
    glDeleteTextures(1, ids)
    free(ids.asInstanceOf[Ptr[Byte]])
  }

  def getWidth(): Int = {
    width
  }

  def getHeight(): Int = {
    height
  }
}
