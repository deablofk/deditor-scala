package dev.cwby.editor.input

import dev.cwby.editor.core.TextBuffer
import dev.cwby.editor.core.TextInteractionMode
import dev.cwby.guitk.components.Window

import scala.collection.mutable

object KeybindingTrie {

  private val ROOTS: mutable.Map[TextInteractionMode, TrieNode] = mutable.Map[TextInteractionMode, TrieNode]()
  private val numberInput: StringBuilder                        = new StringBuilder()

  def getRoot(mode: TextInteractionMode): TrieNode = {
    ROOTS.getOrElseUpdate(mode, new TrieNode())
  }

  private def insertKeybinding(
      mode: TextInteractionMode,
      keybinding: String,
      action: (Window[TextBuffer], TextBuffer) => Unit
  ): Unit = {
    var currentNode = getRoot(mode)
    val keys        = keybinding.split(" ")

    for (key <- keys) {
      if (currentNode.children.contains(key)) {
        currentNode = currentNode.children(key)
      } else {
        val newNode = new TrieNode()
        currentNode.children(key) = newNode
        currentNode = newNode
      }
    }

    currentNode.action = action
  }

  def nmap(keybinding: String, action: (Window[TextBuffer], TextBuffer) => Unit): Unit = {
    insertKeybinding(TextInteractionMode.NAVIGATION, keybinding, action)
  }

  def imap(keybinding: String, action: (Window[TextBuffer], TextBuffer) => Unit): Unit = {
    insertKeybinding(TextInteractionMode.INSERT, keybinding, action)
  }

  def smap(keybinding: String, action: (Window[TextBuffer], TextBuffer) => Unit): Unit = {
    insertKeybinding(TextInteractionMode.SELECT, keybinding, action)
  }

  def cmap(keybinding: String, action: (Window[TextBuffer], TextBuffer) => Unit): Unit = {
    insertKeybinding(TextInteractionMode.COMMAND, keybinding, action)
  }

  def map(keybinding: String, action: (Window[TextBuffer], TextBuffer) => Unit): Unit = {
    insertKeybinding(TextInteractionMode.ANY, keybinding, action)
  }

  def map(mode: TextInteractionMode, keybinding: String, action: (Window[TextBuffer], TextBuffer) => Unit): Unit = {
    insertKeybinding(mode, keybinding, action)
  }

  def appendNumberInput(number: Char): Unit = {
    numberInput.append(number)
  }

  def resetNumberInput(): Unit = {
    numberInput.setLength(0)
  }

  def getNumberInput(): Int = {
    if (numberInput.isEmpty) {
      0
    } else {
      Integer.parseInt(numberInput.toString())
    }
  }
}
