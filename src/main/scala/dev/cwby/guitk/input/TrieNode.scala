package dev.cwby.guitk.input

import dev.cwby.editor.core.TextBuffer
import dev.cwby.guitk.components.Window

import scala.collection.mutable
import scala.compiletime.uninitialized

class TrieNode {
  var children: mutable.Map[String, TrieNode] = mutable.Map[String, TrieNode]()
  var action: (Window, TextBuffer) => Unit    = uninitialized
  var lastPressTime: Long                     = uninitialized

  this.action = null
  this.lastPressTime = System.currentTimeMillis()

  def search(keybinding: String): TrieNode = {
    children.getOrElse(keybinding, null)
  }

  override def toString(): String = {
    "TrieNode{" +
      "children=" + children +
      ", action=" + action +
      ", lastPressTime=" + lastPressTime +
      '}'
  }
}
