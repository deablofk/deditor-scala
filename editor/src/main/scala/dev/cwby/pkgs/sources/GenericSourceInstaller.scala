package dev.cwby.pkgs.sources

import dev.cwby.pkgs.PackageData
import dev.cwby.pkgs.PackageManager

import java.io.File
import java.io.IOException

class GenericSourceInstaller extends ISourceInstaller {

  override def install(packageData: PackageData): Unit = {
    val file = new File(PackageManager.INTERNALS_DIR + packageData.name)
    file.mkdirs()
    try {
      // Stub implementation - java.net.URL not available in Scala Native
      val urlString = packageData.source.packageOrUrl
      val fileName  = extractFileNameFromUrl(urlString)
      val filepath  = file.getPath + "/" + fileName
      downloadPackage(urlString, filepath)
      extractTarGz(filepath, fileName)
      removePackageFile(filepath)
    } catch {
      case e: IOException =>
        e.printStackTrace()
    }
  }

  private def extractFileNameFromUrl(urlString: String): String = {
    // Extract filename from URL string
    val lastSlash = urlString.lastIndexOf('/')
    if lastSlash >= 0 then urlString.substring(lastSlash + 1) else urlString
  }

  @throws[IOException]
  private def downloadPackage(urlString: String, filePath: String): Unit = {
    // Stub implementation - would need to use curl or wget via ProcessBuilder
    // or implement HTTP client using POSIX sockets
    println(s"Downloading from $urlString to $filePath (stub)")
    // TODO: Implement using curl/wget subprocess or native sockets
  }

  @throws[IOException]
  private def extractTarGz(filePath: String, fileToExtract: String): Unit = {
    val processBuilder = new ProcessBuilder("tar", "-xf", fileToExtract)
    processBuilder.directory(new File(filePath).getParentFile)
    try {
      val process  = processBuilder.start()
      val exitCode = process.waitFor()

      if (exitCode != 0) {
        println("Extraction failed with exit code: " + exitCode)
      } else {
        println("Extraction successful.")
        removePackageFile(fileToExtract)
      }
    } catch {
      case e: InterruptedException =>
        Thread.currentThread().interrupt()
        throw new IOException("Extraction was interrupted.", e)
    }
  }

  private def removePackageFile(filepath: String): Unit = {
    val file = new File(filepath)
    if (file.exists()) {
      file.delete()
    }
  }
}
