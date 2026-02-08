package dev.cwby.dway

import dev.cwby.dway.compositor.Compositor

import scala.scalanative.unsafe.*
import scala.scalanative.libc.stdlib
import scala.scalanative.posix.unistd

object Dway {
  def main(args: Array[String]): Unit = {
    println("[dway] Starting Wayland compositor...")

    if (!Compositor.create()) {
      System.err.println("[dway] Failed to create compositor")
      stdlib.exit(1)
    }

    if (!Compositor.start()) {
      System.err.println("[dway] Failed to start compositor")
      Compositor.destroy()
      stdlib.exit(1)
    }

    val socket = Compositor.getSocket
    println(s"[dway] Wayland display: $socket")

    // Set WAYLAND_DISPLAY for child processes
    Zone {
      setenv(c"WAYLAND_DISPLAY", toCString(socket), 1)
    }

    // Spawn alacritty (or custom command) inside the compositor
    val command = if (args.length > 0) args.mkString(" ") else "alacritty"
    println(s"[dway] Spawning: $command")
    spawnProcess(command)

    Compositor.run()
    Compositor.destroy()
    println("[dway] Compositor shut down.")
  }

  private def spawnAlacritty(): Unit = {
    spawnProcess("alacritty")
  }

  private def spawnProcess(command: String): Unit = {
    val pid = unistd.fork()
    if (pid == 0) {
      Zone {
        execl(c"/bin/sh", c"/bin/sh", c"-c", toCString(command), null)
      }
      unistd._exit(1)
    } else if (pid > 0) {
      System.err.println(s"[dway] Spawned process: $command (pid=$pid)")
    } else {
      System.err.println("[dway] Fork failed")
    }
  }

  @extern private def setenv(name: CString, value: CString, overwrite: CInt): CInt = extern
  @extern private def execl(path: CString, arg0: CString, args: CString*): CInt    = extern
}
