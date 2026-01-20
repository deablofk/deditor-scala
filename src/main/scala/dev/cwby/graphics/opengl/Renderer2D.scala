package dev.cwby.graphics.opengl

import dev.cwby.bindings.GLConstants._
import dev.cwby.bindings.GLHelpers
import dev.cwby.bindings.gl

import scala.collection.mutable.Stack
import scala.compiletime.uninitialized
import scala.scalanative.libc.stdlib._
import scala.scalanative.unsafe._

object Renderer2D {
  private val MAX_BATCH_SIZE = 50000 // 5x larger for text-heavy rendering
  private val VERTEX_SIZE    = 9     // x, y, z, r, g, b, a, u, v

  private class ClipRect(val x: Float, val y: Float, val width: Float, val height: Float)
}

class Renderer2D(screenWidth: Int, screenHeight: Int) {

  import Renderer2D.*

  private val rectShader: Shader        = createRectShader()
  private val textShader: Shader        = createTextShader()
  private val colorTextShader: Shader   = createColorTextShader()
  private var vao: Int                  = uninitialized
  private var vbo: Int                  = uninitialized
  private var vertexBuffer: Ptr[CFloat] = uninitialized
  MAX_BATCH_SIZE * VERTEX_SIZE
  private var vertexCount: Int          = 0
  private var vertexBufferPos: Int      = 0

  private var projectionMatrix: Array[Float] = uninitialized
  private val clipStack: Stack[ClipRect]     = new Stack[ClipRect]()

  private var isTextMode: Boolean      = false
  private var isColorTextMode: Boolean = false
  private val colorCache               = scala.collection.mutable.Map[Int, (Float, Float, Float, Float)]()

  initializeBuffers()
  updateProjection(screenWidth, screenHeight)

  // Enable blending for transparency
  gl.glEnable(GL_BLEND)
  gl.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

  private def createRectShader(): Shader = {
    val rectVertexShader =
      "#version 330 core\n" +
        "layout (location = 0) in vec3 aPos;\n" +
        "layout (location = 1) in vec4 aColor;\n" +
        "layout (location = 2) in vec2 aTexCoord;\n" +
        "out vec4 vertexColor;\n" +
        "out vec2 texCoord;\n" +
        "uniform mat4 projection;\n" +
        "void main() {\n" +
        "    gl_Position = projection * vec4(aPos, 1.0);\n" +
        "    vertexColor = aColor;\n" +
        "    texCoord = aTexCoord;\n" +
        "}\n"

    val rectFragmentShader =
      "#version 330 core\n" +
        "in vec4 vertexColor;\n" +
        "in vec2 texCoord;\n" +
        "out vec4 FragColor;\n" +
        "void main() {\n" +
        "    FragColor = vertexColor;\n" +
        "}\n"

    new Shader(rectVertexShader, rectFragmentShader)
  }

  private def createTextShader(): Shader = {
    val rectVertexShader =
      "#version 330 core\n" +
        "layout (location = 0) in vec3 aPos;\n" +
        "layout (location = 1) in vec4 aColor;\n" +
        "layout (location = 2) in vec2 aTexCoord;\n" +
        "out vec4 vertexColor;\n" +
        "out vec2 texCoord;\n" +
        "uniform mat4 projection;\n" +
        "void main() {\n" +
        "    gl_Position = projection * vec4(aPos, 1.0);\n" +
        "    vertexColor = aColor;\n" +
        "    texCoord = aTexCoord;\n" +
        "}\n"

    val textFragmentShader =
      "#version 330 core\n" +
        "in vec4 vertexColor;\n" +
        "in vec2 texCoord;\n" +
        "out vec4 FragColor;\n" +
        "uniform sampler2D textTexture;\n" +
        "void main() {\n" +
        "    float alpha = texture(textTexture, texCoord).r;\n" +
        "    FragColor = vec4(vertexColor.rgb, vertexColor.a * alpha);\n" +
        "}\n"

    new Shader(rectVertexShader, textFragmentShader)
  }

  private def createColorTextShader(): Shader = {
    val rectVertexShader =
      "#version 330 core\n" +
        "layout (location = 0) in vec3 aPos;\n" +
        "layout (location = 1) in vec4 aColor;\n" +
        "layout (location = 2) in vec2 aTexCoord;\n" +
        "out vec4 vertexColor;\n" +
        "out vec2 texCoord;\n" +
        "uniform mat4 projection;\n" +
        "void main() {\n" +
        "    gl_Position = projection * vec4(aPos, 1.0);\n" +
        "    vertexColor = aColor;\n" +
        "    texCoord = aTexCoord;\n" +
        "}\n"

    val colorTextFragmentShader =
      "#version 330 core\n" +
        "in vec4 vertexColor;\n" +
        "in vec2 texCoord;\n" +
        "out vec4 FragColor;\n" +
        "uniform sampler2D textTexture;\n" +
        "void main() {\n" +
        "    vec4 texColor = texture(textTexture, texCoord);\n" +
        "    FragColor = texColor;\n" +
        "}\n"

    new Shader(rectVertexShader, colorTextFragmentShader)
  }

  private def initializeBuffers(): Unit = {
    // Create VAO and VBO
    vao = GLHelpers.glGenVertexArrays()
    vbo = GLHelpers.glGenBuffers()

    gl.glBindVertexArray(vao)
    gl.glBindBuffer(GL_ARRAY_BUFFER, vbo)
    gl.glBufferData(GL_ARRAY_BUFFER, (MAX_BATCH_SIZE * VERTEX_SIZE * 4), null, GL_DYNAMIC_DRAW)

    // Position attribute
    gl.glVertexAttribPointer(0, 3, GL_FLOAT, false, VERTEX_SIZE * 4, 0)
    gl.glEnableVertexAttribArray(0)

    // Color attribute
    gl.glVertexAttribPointer(1, 4, GL_FLOAT, false, VERTEX_SIZE * 4, 3 * 4)
    gl.glEnableVertexAttribArray(1)

    // Texture coordinate attribute
    gl.glVertexAttribPointer(2, 2, GL_FLOAT, false, VERTEX_SIZE * 4, 7 * 4)
    gl.glEnableVertexAttribArray(2)

    gl.glBindBuffer(GL_ARRAY_BUFFER, 0)
    gl.glBindVertexArray(0)

    vertexBuffer = malloc(MAX_BATCH_SIZE * VERTEX_SIZE * 4).asInstanceOf[Ptr[CFloat]]
    vertexCount = 0
    vertexBufferPos = 0
  }

  def updateProjection(width: Int, height: Int): Unit = {
    // Update OpenGL viewport
    gl.glViewport(0, 0, width, height)

    // Orthographic projection matrix (top-left origin)
    projectionMatrix = new Array[Float](16)
    projectionMatrix(0) = 2.0f / width
    projectionMatrix(5) = -2.0f / height
    projectionMatrix(10) = -1.0f
    projectionMatrix(12) = -1.0f
    projectionMatrix(13) = 1.0f
    projectionMatrix(15) = 1.0f
  }

  def begin(): Unit = {
    vertexCount = 0
    vertexBufferPos = 0
  }

  def end(): Unit = {
    flush()
  }

  def flush(): Unit = {
    if (vertexCount == 0) {
      return
    }

    gl.glBindVertexArray(vao)
    gl.glBindBuffer(GL_ARRAY_BUFFER, vbo)
    gl.glBufferSubData(GL_ARRAY_BUFFER, 0, vertexBufferPos * 4, vertexBuffer.asInstanceOf[Ptr[Byte]])

    gl.glDrawArrays(GL_TRIANGLES, 0, vertexCount)

    gl.glBindBuffer(GL_ARRAY_BUFFER, 0)
    gl.glBindVertexArray(0)

    vertexCount = 0
    vertexBufferPos = 0
  }

  private def checkFlush(): Unit = {
    if (vertexCount >= MAX_BATCH_SIZE - 6) {
      flush()
    }
  }

  def drawRect(x: Float, y: Float, width: Float, height: Float, color: Int): Unit = {
    drawRect(x, y, width, height, color, false)
  }

  def drawRect(x: Float, y: Float, width: Float, height: Float, color: Int, outline: Boolean): Unit =
    if (outline) {
      val lineWidth = 5.0f
      // Top
      drawFilledRect(x, y, width, lineWidth, color)
      // Bottom
      drawFilledRect(x, y + height - lineWidth, width, lineWidth, color)
      // Left
      drawFilledRect(x, y, lineWidth, height, color)
      // Right
      drawFilledRect(x + width - lineWidth, y, lineWidth, height, color)
    } else {
      drawFilledRect(x, y, width, height, color)
    }

  private def drawFilledRect(x: Float, y: Float, width: Float, height: Float, color: Int): Unit = {
    checkFlush()

    val r = ((color >> 16) & 0xff) / 255.0f
    val g = ((color >> 8) & 0xff) / 255.0f
    val b = (color & 0xff) / 255.0f
    val a = ((color >> 24) & 0xff) / 255.0f

    // Triangle 1
    addVertex(x, y, 0, r, g, b, a, 0, 0)
    addVertex(x + width, y, 0, r, g, b, a, 1, 0)
    addVertex(x, y + height, 0, r, g, b, a, 0, 1)

    // Triangle 2
    addVertex(x + width, y, 0, r, g, b, a, 1, 0)
    addVertex(x + width, y + height, 0, r, g, b, a, 1, 1)
    addVertex(x, y + height, 0, r, g, b, a, 0, 1)

    vertexCount += 6
  }

  @inline
  private def getRGBA(color: Int): (Float, Float, Float, Float) = {
    colorCache.getOrElseUpdate(
      color, {
        val r = ((color >> 16) & 0xff) / 255.0f
        val g = ((color >> 8) & 0xff) / 255.0f
        val b = (color & 0xff) / 255.0f
        val a = ((color >> 24) & 0xff) / 255.0f
        (r, g, b, a)
      }
    )
  }

  def beginTextBatch(font: GLFont): Unit = {
    if (isTextMode) return

    flush()
    isTextMode = true
    isColorTextMode = false
    textShader.bind()
    textShader.setUniformMatrix4("projection", projectionMatrix)
    textShader.setUniform("textTexture", 0)
    font.getTexture().bind()
  }

  def endTextBatch(font: GLFont): Unit = {
    if (!isTextMode && !isColorTextMode) return

    flush()
    if (isColorTextMode) {
      font.getColorTexture().unbind()
      colorTextShader.unbind()
    } else {
      font.getTexture().unbind()
      textShader.unbind()
    }
    isTextMode = false
    isColorTextMode = false

    rectShader.bind()
    rectShader.setUniformMatrix4("projection", projectionMatrix)
  }

  private def switchToColorText(font: GLFont): Unit = {
    if (isColorTextMode) return

    flush()
    if (isTextMode) {
      font.getTexture().unbind()
      textShader.unbind()
    }

    isTextMode = false
    isColorTextMode = true
    colorTextShader.bind()
    colorTextShader.setUniformMatrix4("projection", projectionMatrix)
    colorTextShader.setUniform("textTexture", 0)
    font.getColorTexture().bind()
  }

  private def switchToGrayscaleText(font: GLFont): Unit = {
    if (isTextMode) return

    flush()
    if (isColorTextMode) {
      font.getColorTexture().unbind()
      colorTextShader.unbind()
    }

    isColorTextMode = false
    isTextMode = true
    textShader.bind()
    textShader.setUniformMatrix4("projection", projectionMatrix)
    textShader.setUniform("textTexture", 0)
    font.getTexture().bind()
  }

  def drawText(text: String, x: Float, y: Float, font: GLFont, color: Int): Unit = {
    if (text == null || text.isEmpty) {
      return
    }

    val (r, g, b, a) = getRGBA(color)

    var offsetX = x
    val offsetY = y

    var i = 0
    while (i < text.length) {
      val codepoint = text.codePointAt(i)
      val charInfo  = font.getCharInfo(codepoint)

      if (codepoint != ' ' && codepoint != '\t') {
        if (charInfo.isColor) {
          switchToColorText(font)
        } else {
          switchToGrayscaleText(font)
        }

        val x0 = offsetX + charInfo.x0
        val y0 = offsetY + charInfo.y0
        val x1 = offsetX + charInfo.x1
        val y1 = offsetY + charInfo.y1

        checkFlush()

        // Triangle 1
        addVertex(x0, y0, 0, r, g, b, a, charInfo.s0, charInfo.t0)
        addVertex(x1, y0, 0, r, g, b, a, charInfo.s1, charInfo.t0)
        addVertex(x0, y1, 0, r, g, b, a, charInfo.s0, charInfo.t1)

        // Triangle 2
        addVertex(x1, y0, 0, r, g, b, a, charInfo.s1, charInfo.t0)
        addVertex(x1, y1, 0, r, g, b, a, charInfo.s1, charInfo.t1)
        addVertex(x0, y1, 0, r, g, b, a, charInfo.s0, charInfo.t1)

        vertexCount += 6
      }

      offsetX += charInfo.advance
      i += Character.charCount(codepoint)
    }
  }

  private def addVertex(
      x: Float,
      y: Float,
      z: Float,
      r: Float,
      g: Float,
      b: Float,
      a: Float,
      u: Float,
      v: Float
  ): Unit = {
    vertexBuffer(vertexBufferPos) = x
    vertexBuffer(vertexBufferPos + 1) = y
    vertexBuffer(vertexBufferPos + 2) = z
    vertexBuffer(vertexBufferPos + 3) = r
    vertexBuffer(vertexBufferPos + 4) = g
    vertexBuffer(vertexBufferPos + 5) = b
    vertexBuffer(vertexBufferPos + 6) = a
    vertexBuffer(vertexBufferPos + 7) = u
    vertexBuffer(vertexBufferPos + 8) = v
    vertexBufferPos += VERTEX_SIZE
  }

  def pushClip(x: Float, y: Float, width: Float, height: Float): Unit = {
    flush()
    clipStack.push(new ClipRect(x, y, width, height))
    applyClip()
  }

  def popClip(): Unit = {
    flush()
    if (!clipStack.isEmpty) {
      clipStack.pop()
    }
    if (clipStack.isEmpty) {
      gl.glDisable(GL_SCISSOR_TEST)
    } else {
      applyClip()
    }
  }

  private def applyClip(): Unit = {
    if (!clipStack.isEmpty) {
      val clip = clipStack.top
      gl.glEnable(GL_SCISSOR_TEST)
      // Convert from top-left origin to bottom-left origin for OpenGL
      val screenHeight = (2.0f / -projectionMatrix(5)).toInt
      gl.glScissor(clip.x.toInt, screenHeight - (clip.y + clip.height).toInt, clip.width.toInt, clip.height.toInt)
    }
  }

  def clear(color: Int): Unit = {
    val r = ((color >> 16) & 0xff) / 255.0f
    val g = ((color >> 8) & 0xff) / 255.0f
    val b = (color & 0xff) / 255.0f
    val a = ((color >> 24) & 0xff) / 255.0f
    gl.glClearColor(r, g, b, a)
    gl.glClear(GL_COLOR_BUFFER_BIT)
  }

  def startFrame(): Unit = {
    rectShader.bind()
    rectShader.setUniformMatrix4("projection", projectionMatrix)
    begin()
  }

  def endFrame(): Unit = {
    end()
    rectShader.unbind()
  }

  def cleanup(): Unit = {
    rectShader.cleanup()
    textShader.cleanup()
    colorTextShader.cleanup()
    GLHelpers.glDeleteVertexArrays(vao)
    GLHelpers.glDeleteBuffers(vbo)
    if (vertexBuffer != null) {
      free(vertexBuffer.asInstanceOf[Ptr[Byte]])
    }
  }
}
