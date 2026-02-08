package dev.cwby.guitk.bindings.opengl

import scala.scalanative.unsafe.*

object GLConstants {
  inline val GL_VENDOR              = 0x1f00
  inline val GL_RENDERER            = 0x1f01
  inline val GL_VERSION             = 0x1f02
  inline val GL_DEPTH_TEST          = 0x0b71
  inline val GL_COLOR_BUFFER_BIT    = 0x00004000
  inline val GL_DEPTH_BUFFER_BIT    = 0x00000100
  inline val GL_TEXTURE_2D          = 0x0de1
  inline val GL_TEXTURE_WRAP_S      = 0x2802
  inline val GL_TEXTURE_WRAP_T      = 0x2803
  inline val GL_TEXTURE_MIN_FILTER  = 0x2801
  inline val GL_TEXTURE_MAG_FILTER  = 0x2800
  inline val GL_CLAMP_TO_EDGE       = 0x812f
  inline val GL_NEAREST             = 0x2600
  inline val GL_LINEAR              = 0x2601
  inline val GL_RED                 = 0x1903
  inline val GL_RGBA                = 0x1908
  inline val GL_UNSIGNED_BYTE       = 0x1401
  inline val GL_BLEND               = 0x0be2
  inline val GL_SRC_ALPHA           = 0x0302
  inline val GL_ONE_MINUS_SRC_ALPHA = 0x0303
  inline val GL_ARRAY_BUFFER        = 0x8892
  inline val GL_DYNAMIC_DRAW        = 0x88e8
  inline val GL_FLOAT               = 0x1406
  inline val GL_VERTEX_SHADER       = 0x8b31
  inline val GL_FRAGMENT_SHADER     = 0x8b30
  inline val GL_COMPILE_STATUS      = 0x8b81
  inline val GL_LINK_STATUS         = 0x8b82
  inline val GL_VALIDATE_STATUS     = 0x8b83
  inline val GL_INFO_LOG_LENGTH     = 0x8b84
  inline val GL_TRIANGLES           = 0x0004
  inline val GL_SCISSOR_TEST        = 0x0c11
  inline val GL_UNPACK_ALIGNMENT    = 0x0cf5
  inline val GL_FALSE               = 0
  inline val GL_TRUE                = 1
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
