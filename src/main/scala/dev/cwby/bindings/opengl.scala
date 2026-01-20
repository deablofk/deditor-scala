package dev.cwby.bindings

import scala.scalanative.unsafe._

object GLConstants {
  val GL_VENDOR: CInt              = 0x1f00
  val GL_RENDERER: CInt            = 0x1f01
  val GL_VERSION: CInt             = 0x1f02
  val GL_DEPTH_TEST: CInt          = 0x0b71
  val GL_COLOR_BUFFER_BIT: CInt    = 0x00004000
  val GL_DEPTH_BUFFER_BIT: CInt    = 0x00000100
  val GL_TEXTURE_2D: CInt          = 0x0de1
  val GL_TEXTURE_WRAP_S: CInt      = 0x2802
  val GL_TEXTURE_WRAP_T: CInt      = 0x2803
  val GL_TEXTURE_MIN_FILTER: CInt  = 0x2801
  val GL_TEXTURE_MAG_FILTER: CInt  = 0x2800
  val GL_CLAMP_TO_EDGE: CInt       = 0x812f
  val GL_NEAREST: CInt             = 0x2600
  val GL_LINEAR: CInt              = 0x2601
  val GL_RED: CInt                 = 0x1903
  val GL_RGBA: CInt                = 0x1908
  val GL_UNSIGNED_BYTE: CInt       = 0x1401
  val GL_BLEND: CInt               = 0x0be2
  val GL_SRC_ALPHA: CInt           = 0x0302
  val GL_ONE_MINUS_SRC_ALPHA: CInt = 0x0303
  val GL_ARRAY_BUFFER: CInt        = 0x8892
  val GL_DYNAMIC_DRAW: CInt        = 0x88e8
  val GL_FLOAT: CInt               = 0x1406
  val GL_VERTEX_SHADER: CInt       = 0x8b31
  val GL_FRAGMENT_SHADER: CInt     = 0x8b30
  val GL_COMPILE_STATUS: CInt      = 0x8b81
  val GL_LINK_STATUS: CInt         = 0x8b82
  val GL_VALIDATE_STATUS: CInt     = 0x8b83
  val GL_INFO_LOG_LENGTH: CInt     = 0x8b84
  val GL_TRIANGLES: CInt           = 0x0004
  val GL_SCISSOR_TEST: CInt        = 0x0c11
  val GL_UNPACK_ALIGNMENT: CInt    = 0x0cf5
  val GL_FALSE: CInt               = 0
  val GL_TRUE: CInt                = 1
}

@extern
object gl {
  def glClearColor(red: CFloat, green: CFloat, blue: CFloat, alpha: CFloat): Unit = extern

  def glClear(mask: CInt): Unit = extern

  def glGetString(name: CInt): CString = extern

  def glEnable(target: CInt): Unit = extern

  def glDisable(target: CInt): Unit = extern

  def glGetError(): CInt = extern

  def glGenTextures(n: CInt, textures: Ptr[CInt]): Unit = extern

  def glBindTexture(target: CInt, texture: CInt): Unit = extern

  def glTexParameteri(target: CInt, pname: CInt, param: CInt): Unit = extern

  def glTexImage2D(
      target: CInt,
      level: CInt,
      internalformat: CInt,
      width: CInt,
      height: CInt,
      border: CInt,
      format: CInt,
      `type`: CInt,
      pixels: Ptr[Byte]
  ): Unit = extern

  def glTexSubImage2D(
      target: CInt,
      level: CInt,
      xoffset: CInt,
      yoffset: CInt,
      width: CInt,
      height: CInt,
      format: CInt,
      `type`: CInt,
      pixels: Ptr[Byte]
  ): Unit = extern

  def glDeleteTextures(n: CInt, textures: Ptr[CInt]): Unit = extern

  def glBlendFunc(sfactor: CInt, dfactor: CInt): Unit = extern

  def glGenVertexArrays(n: CInt, arrays: Ptr[CInt]): Unit = extern

  def glGenBuffers(n: CInt, buffers: Ptr[CInt]): Unit = extern

  def glBindVertexArray(array: CInt): Unit = extern

  def glBindBuffer(target: CInt, buffer: CInt): Unit = extern

  def glBufferData(target: CInt, size: CLong, data: Ptr[Byte], usage: CInt): Unit = extern

  def glBufferSubData(target: CInt, offset: CLong, size: CLong, data: Ptr[Byte]): Unit = extern

  def glVertexAttribPointer(
      index: CInt,
      size: CInt,
      `type`: CInt,
      normalized: Boolean,
      stride: CInt,
      pointer: CLong
  ): Unit = extern

  def glEnableVertexAttribArray(index: CInt): Unit = extern

  def glDeleteVertexArrays(n: CInt, arrays: Ptr[CInt]): Unit = extern

  def glDeleteBuffers(n: CInt, buffers: Ptr[CInt]): Unit = extern

  def glCreateProgram(): CInt = extern

  def glCreateShader(`type`: CInt): CInt = extern

  def glShaderSource(shader: CInt, count: CInt, string: Ptr[CString], length: Ptr[CInt]): Unit = extern

  def glCompileShader(shader: CInt): Unit = extern

  def glGetShaderiv(shader: CInt, pname: CInt, params: Ptr[CInt]): Unit = extern

  def glGetShaderInfoLog(shader: CInt, bufSize: CInt, length: Ptr[CInt], infoLog: Ptr[CChar]): Unit = extern

  def glAttachShader(program: CInt, shader: CInt): Unit = extern

  def glLinkProgram(program: CInt): Unit = extern

  def glGetProgramiv(program: CInt, pname: CInt, params: Ptr[CInt]): Unit = extern

  def glGetProgramInfoLog(program: CInt, bufSize: CInt, length: Ptr[CInt], infoLog: Ptr[CChar]): Unit = extern

  def glValidateProgram(program: CInt): Unit = extern

  def glUseProgram(program: CInt): Unit = extern

  def glDeleteShader(shader: CInt): Unit = extern

  def glDeleteProgram(program: CInt): Unit = extern

  def glDetachShader(program: CInt, shader: CInt): Unit = extern

  def glGetUniformLocation(program: CInt, name: CString): CInt = extern

  def glUniform1i(location: CInt, v0: CInt): Unit = extern

  def glUniform1f(location: CInt, v0: CFloat): Unit = extern

  def glUniform2f(location: CInt, v0: CFloat, v1: CFloat): Unit = extern

  def glUniform3f(location: CInt, v0: CFloat, v1: CFloat, v2: CFloat): Unit = extern

  def glUniform4f(location: CInt, v0: CFloat, v1: CFloat, v2: CFloat, v3: CFloat): Unit = extern

  def glUniformMatrix4fv(location: CInt, count: CInt, transpose: Boolean, value: Ptr[CFloat]): Unit = extern

  def glDrawArrays(mode: CInt, first: CInt, count: CInt): Unit = extern

  def glViewport(x: CInt, y: CInt, width: CInt, height: CInt): Unit = extern

  def glScissor(x: CInt, y: CInt, width: CInt, height: CInt): Unit = extern

  def glPixelStorei(pname: CInt, param: CInt): Unit = extern
}

object GLHelpers {

  def glGenTextures(): CInt = {
    val texture = stackalloc[CInt](1)
    gl.glGenTextures(1, texture)
    !texture
  }

  def glDeleteTextures(texture: CInt): Unit = {
    val tex = stackalloc[CInt](1)
    !tex = texture
    gl.glDeleteTextures(1, tex)
  }

  def glGenVertexArrays(): CInt = {
    val vao = stackalloc[CInt](1)
    gl.glGenVertexArrays(1, vao)
    !vao
  }

  def glGenBuffers(): CInt = {
    val vbo = stackalloc[CInt](1)
    gl.glGenBuffers(1, vbo)
    !vbo
  }

  def glDeleteVertexArrays(vao: CInt): Unit = {
    val v = stackalloc[CInt](1)
    !v = vao
    gl.glDeleteVertexArrays(1, v)
  }

  def glDeleteBuffers(vbo: CInt): Unit = {
    val v = stackalloc[CInt](1)
    !v = vbo
    gl.glDeleteBuffers(1, v)
  }
}
