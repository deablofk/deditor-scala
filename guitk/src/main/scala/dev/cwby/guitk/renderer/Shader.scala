package dev.cwby.guitk.renderer

import dev.cwby.guitk.bindings.opengl.GLConstants._
import dev.cwby.guitk.bindings.opengl.gl._

import scala.scalanative.unsafe._

class Shader(vertexSource: String, fragmentSource: String) {
  private val programId: Int        = glCreateProgram()
  private val vertexShaderId: Int   = createShader(vertexSource, GL_VERTEX_SHADER)
  private val fragmentShaderId: Int = createShader(fragmentSource, GL_FRAGMENT_SHADER)

  if (programId == 0) {
    throw new RuntimeException("Could not create shader program")
  }

  glAttachShader(programId, vertexShaderId)
  glAttachShader(programId, fragmentShaderId)
  glLinkProgram(programId)

  Zone {
    val status = stackalloc[CInt](1)
    glGetProgramiv(programId, GL_LINK_STATUS, status)
    if ((!status) == GL_FALSE) {
      val log = stackalloc[CChar](1024)
      glGetProgramInfoLog(programId, 1024, null, log)
      throw new RuntimeException("Error linking shader: " + fromCString(log.asInstanceOf[CString]))
    }
  }

  glValidateProgram(programId)
  Zone {
    val status = stackalloc[CInt](1)
    glGetProgramiv(programId, GL_VALIDATE_STATUS, status)
    if ((!status) == GL_FALSE) {
      val log = stackalloc[CChar](1024)
      glGetProgramInfoLog(programId, 1024, null, log)
      println("Warning validating shader: " + fromCString(log.asInstanceOf[CString]))
    }
  }

  private def createShader(source: String, shaderType: Int): Int = {
    val shaderId = glCreateShader(shaderType)
    if (shaderId == 0) {
      throw new RuntimeException("Error creating shader. Type: " + shaderType)
    }

    Zone {
      val cstr   = toCString(source)
      val strPtr = stackalloc[CString](1)
      !strPtr = cstr
      glShaderSource(shaderId, 1, strPtr, null)
    }
    glCompileShader(shaderId)

    Zone {
      val status = stackalloc[CInt](1)
      glGetShaderiv(shaderId, GL_COMPILE_STATUS, status)
      if ((!status) == GL_FALSE) {
        val log = stackalloc[CChar](1024)
        glGetShaderInfoLog(shaderId, 1024, null, log)
        throw new RuntimeException("Error compiling shader: " + fromCString(log.asInstanceOf[CString]))
      }
    }

    shaderId
  }

  def bind(): Unit = {
    glUseProgram(programId)
  }

  def unbind(): Unit = {
    glUseProgram(0)
  }

  def setUniform(name: String, value: Int): Unit = {
    Zone {
      val location = glGetUniformLocation(programId, toCString(name))
      if (location != -1) {
        glUniform1i(location, value)
      }
    }
  }

  def setUniform(name: String, value: Float): Unit = {
    Zone {
      val location = glGetUniformLocation(programId, toCString(name))
      if (location != -1) {
        glUniform1f(location, value)
      }
    }
  }

  def setUniform(name: String, x: Float, y: Float): Unit = {
    Zone {
      val location = glGetUniformLocation(programId, toCString(name))
      if (location != -1) {
        glUniform2f(location, x, y)
      }
    }
  }

  def setUniform(name: String, x: Float, y: Float, z: Float): Unit = {
    Zone {
      val location = glGetUniformLocation(programId, toCString(name))
      if (location != -1) {
        glUniform3f(location, x, y, z)
      }
    }
  }

  def setUniform(name: String, x: Float, y: Float, z: Float, w: Float): Unit = {
    Zone {
      val location = glGetUniformLocation(programId, toCString(name))
      if (location != -1) {
        glUniform4f(location, x, y, z, w)
      }
    }
  }

  def setUniformMatrix4(name: String, matrix: Array[Float]): Unit = {
    Zone {
      val location = glGetUniformLocation(programId, toCString(name))
      if (location != -1) {
        val matrixPtr = stackalloc[CFloat](16)
        var i         = 0
        while i < 16 do
          matrixPtr(i) = matrix(i)
          i += 1
        glUniformMatrix4fv(location, 1, false, matrixPtr)
      }
    }
  }

  def cleanup(): Unit = {
    unbind()
    if (programId != 0) {
      glDetachShader(programId, vertexShaderId)
      glDetachShader(programId, fragmentShaderId)
      glDeleteShader(vertexShaderId)
      glDeleteShader(fragmentShaderId)
      glDeleteProgram(programId)
    }
  }

  def getProgramId(): Int = {
    programId
  }
}
