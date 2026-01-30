package dev.cwby.lsp

import dev.cwby.editor.core.TextBuffer
import dev.cwby.findProjectRoot
import dev.cwby.pkgs.PackageData
import dev.cwby.pkgs.PackageManager
import dev.cwby.pkgs.PackageSourceType

import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import scala.collection.mutable

/** LSP Manager - manages LSP server processes and client connections. Equivalent to the original Java LSPManager
  * implementation.
  */
object LSPManager {
  private val LSP_LISTENER                                     = new LSPClientListener()
  private val ATTACHED_LSP: mutable.Map[TextBuffer, LSPClient] = mutable.Map[TextBuffer, LSPClient]()

  private def createLSPClient(process: Process): LSPClient = {
    val client = new LSPClient(
      process.getInputStream,
      process.getOutputStream,
      LSP_LISTENER
    )
    client.startListening()
    client
  }

  private def createCommand(pkg: PackageData, workingDir: File): ProcessBuilder = {
    val command = pkg.source.`type` match {
      case PackageSourceType.COMMAND =>
        pkg.source.executable
      case _ =>
        new File(PackageManager.INTERNALS_DIR + pkg.name + "/" + pkg.source.executable).getAbsolutePath
    }
    println(s"[LSP] Starting command: $command (working dir: ${workingDir.getAbsolutePath})")
    val pb = new ProcessBuilder("sh", "-lc", command)
    pb.directory(workingDir)
    pb
  }

  def initializeServer(buffer: TextBuffer, pkg: PackageData): Unit = {
    val root = findProjectRoot(
      File(buffer.getFilepath()),
      pkg.trigger.projectRoot
    )

    val initThread = new Thread(() => {
      try {
        println(s"[LSP] Starting initialization for buffer: ${buffer.getFilepath()}, hash: ${buffer.hashCode()}")
        val pb      = createCommand(pkg, root)
        val process = pb.start()
        println(s"[LSP] Process alive: ${process.isAlive}")
        lspErrorLogger(process.getErrorStream, pkg.name)
        Thread.sleep(500)
        println(s"[LSP] Process still alive after 500ms: ${process.isAlive}")
        println(s"[LSP] Process started, creating client...")
        val client = createLSPClient(process)
        ATTACHED_LSP.synchronized {
          ATTACHED_LSP.put(buffer, client)
          println(s"[LSP] Client stored for buffer hash: ${buffer.hashCode()}")
        }
        println(s"[LSP] Initializing LSP protocol...")
        client.initialize(root.getAbsolutePath)
        client.sendDidOpen(buffer)
        println(s"[LSP] LSP server fully initialized for ${pkg.name}")
      } catch {
        case e: (IOException | RuntimeException) =>
          System.err.println(s"Failed to initialize LSP server: ${e.getMessage}")
          e.printStackTrace()
      }
    })
    initThread.setDaemon(true)
    initThread.setName(s"LSP-Init-${pkg.name}")
    initThread.start()
  }

  private def lspErrorLogger(errorStream: InputStream, lspName: String): Unit = {
    val errorThread = new Thread(() => {
      try {
        val reader = new BufferedReader(new InputStreamReader(errorStream))
        var line   = reader.readLine()
        while (line != null) {
          System.err.println(s"[$lspName Error] $line")
          line = reader.readLine()
        }
        reader.close()
      } catch {
        case e: IOException =>
          e.printStackTrace()
      }
    })
    errorThread.setDaemon(true)
    errorThread.start()
  }

  def getLSPClient(buffer: TextBuffer): LSPClient = {
    ATTACHED_LSP.synchronized {
      ATTACHED_LSP.getOrElse(buffer, null)
    }
  }

  def closeAllLsp(): Unit = {
    try {
      ATTACHED_LSP.synchronized {
        for (client <- ATTACHED_LSP.values) {
          client.close()
        }
      }
    } catch {
      case _: Exception =>
    }
    System.exit(0)
  }

}
