package dev.cwby

import java.io.File
import java.nio.file.Files
import scala.collection.mutable.ListBuffer

def expandHome(path: String): String = {
  if path.startsWith("~/") then System.getProperty("user.home") + path.drop(1)
  else path
}

def findProjectRoot(file: File, rootIdentifiers: ListBuffer[String]): File = {
  var dir = if file.isDirectory then file else file.getParentFile

  while dir != null do
    if rootIdentifiers.exists { indicator => Files.exists(dir.toPath.resolve(indicator)) } then return dir

    dir = dir.getParentFile

  file
}

def getFileExtension(filePath: String): String = {
  val fileName = File(filePath).getName
  val dotIndex = fileName.lastIndexOf('.')

  if dotIndex > 0 then fileName.substring(dotIndex + 1)
  else ""
}
