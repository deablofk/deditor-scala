package dev.cwby.editor.commands

import dev.cwby.BufferManager
import dev.cwby.WindowManager
import dev.cwby.editor.core.TextBuffer
import dev.cwby.guitk.components.TiledWindow
import dev.cwby.editor.components.TextComponent

@inline private def ensureComponent(tw: TiledWindow[TextBuffer]): TextComponent = {
  if (tw.component != null)
    tw.component.asInstanceOf[TextComponent]
  else
    TextComponent().setBuffer(BufferManager.addEmptyBuffer())
}

@inline private def split(doSplit: TiledWindow[TextBuffer] => Unit): Boolean = {
  WindowManager.getCurrentWindow match {
    case tw: TiledWindow[TextBuffer] =>
      doSplit(tw)
      val component = ensureComponent(tw)
      tw.leftChild.component = component
      tw.rightChild.component = component
      WindowManager.setCurrentWindow(tw.rightChild)
    case _ => ()
  }

  true
}

def runSplit(args: Array[String]): Boolean =
  split(_.splitHorizontally())

def runVerticalSplit(args: Array[String]): Boolean =
  split(_.splitVertically())
