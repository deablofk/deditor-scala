package dev.cwby.pkgs.sources

import dev.cwby.pkgs.PackageData

import java.io.File
import java.io.IOException
import java.io.PrintWriter

class CommandSourceInstaller extends ISourceInstaller {

  override def install(packageData: PackageData): Unit = {
    if (packageData.name == "metals") {
      installMetals(packageData)
      return
    }

    val command = Option(packageData.source.executable).getOrElse("").trim
    if (command.isEmpty) {
      throw new RuntimeException("No executable configured for package: " + packageData.name)
    }

    verifyCommandOnPath(packageData.name, command)
  }

  private def verifyCommandOnPath(packageName: String, command: String): Unit = {
    try {
      val process  = new ProcessBuilder("sh", "-lc", "command -v " + command + " >/dev/null 2>&1").start()
      val exitCode = process.waitFor()
      if (exitCode != 0) {
        throw new RuntimeException("Command not found on PATH for package: " + packageName + " (" + command + ")")
      }
    } catch {
      case e: IOException =>
        throw new RuntimeException("Failed to verify command for package: " + packageName + " (" + command + ")", e)
      case e: InterruptedException =>
        Thread.currentThread().interrupt()
        throw new RuntimeException(
          "Command verification interrupted for package: " + packageName + " (" + command + ")",
          e
        )
    }
  }

  private def installMetals(packageData: PackageData): Unit = {
    val internalsDir = new File("config/internals/metals")
    internalsDir.mkdirs()

    val csGz          = new File(internalsDir, "cs.gz")
    val csBin         = new File(internalsDir, "cs")
    val metalsWrapper = new File(internalsDir, "metals")

    val url = Option(packageData.source.packageOrUrl).getOrElse("").trim
    if (url.isEmpty) {
      throw new RuntimeException("No download URL configured for package: metals")
    }

    download(url, csGz)
    gunzip(csGz, csBin)
    chmodX(csBin)
    writeMetalsWrapper(metalsWrapper)
    chmodX(metalsWrapper)

    // Ensure the package executable points at the installed wrapper
    packageData.source.executable = metalsWrapper.getAbsolutePath
  }

  private def download(url: String, targetFile: File): Unit = {
    try {
      val cmd = "(" +
        "command -v curl >/dev/null 2>&1 && curl -L -o '" + targetFile.getAbsolutePath + "' '" + url + "'" +
        ") || (" +
        "command -v wget >/dev/null 2>&1 && wget -O '" + targetFile.getAbsolutePath + "' '" + url + "'" +
        ")"
      val process  = new ProcessBuilder("sh", "-lc", cmd).start()
      val exitCode = process.waitFor()
      if (exitCode != 0) {
        throw new RuntimeException("Failed to download: " + url)
      }
    } catch {
      case e: IOException =>
        throw new RuntimeException("Failed to download: " + url, e)
      case e: InterruptedException =>
        Thread.currentThread().interrupt()
        throw new RuntimeException("Download interrupted: " + url, e)
    }
  }

  private def gunzip(sourceGz: File, output: File): Unit = {
    if (!sourceGz.exists()) {
      throw new RuntimeException("Expected file not found: " + sourceGz.getAbsolutePath)
    }

    try {
      val cmd      = "gzip -dc '" + sourceGz.getAbsolutePath + "' > '" + output.getAbsolutePath + "'"
      val process  = new ProcessBuilder("sh", "-lc", cmd).start()
      val exitCode = process.waitFor()
      if (exitCode != 0) {
        throw new RuntimeException("Failed to decompress: " + sourceGz.getAbsolutePath)
      }
    } catch {
      case e: IOException =>
        throw new RuntimeException("Failed to decompress: " + sourceGz.getAbsolutePath, e)
      case e: InterruptedException =>
        Thread.currentThread().interrupt()
        throw new RuntimeException("Decompression interrupted: " + sourceGz.getAbsolutePath, e)
    }
  }

  private def chmodX(file: File): Unit = {
    try {
      val process  = new ProcessBuilder("sh", "-lc", "chmod +x '" + file.getAbsolutePath + "'").start()
      val exitCode = process.waitFor()
      if (exitCode != 0) {
        throw new RuntimeException("Failed to chmod +x: " + file.getAbsolutePath)
      }
    } catch {
      case e: IOException =>
        throw new RuntimeException("Failed to chmod +x: " + file.getAbsolutePath, e)
      case e: InterruptedException =>
        Thread.currentThread().interrupt()
        throw new RuntimeException("chmod interrupted: " + file.getAbsolutePath, e)
    }
  }

  private def writeMetalsWrapper(wrapper: File): Unit = {
    try {
      val writer = new PrintWriter(wrapper)
      writer.println("#!/usr/bin/env sh")
      writer.println("DIR=\"$(CDPATH= cd -- \"$(dirname -- \"$0\")\" && pwd)\"")
      writer.println("exec \"$DIR/cs\" launch metals -- --stdio")
      writer.close()
    } catch {
      case e: IOException =>
        throw new RuntimeException("Failed to write metals wrapper: " + wrapper.getAbsolutePath, e)
    }
  }
}
