package dev.cwby.dway

import dev.cwby.guitk.platform.Engine

object Dway {
  def main(args: Array[String]): Unit = {
    Engine.run { renderer =>
      println("Hello from Dway!")
    }
    Engine.shutdown()
  }
}
