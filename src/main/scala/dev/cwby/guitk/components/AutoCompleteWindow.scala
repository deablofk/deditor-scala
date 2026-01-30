package dev.cwby.guitk.components

import dev.cwby.BufferManager
import dev.cwby.editor.core.TextBuffer
import dev.cwby.guitk.text.FontManager
import dev.cwby.editor.components.TextComponent
import dev.cwby.lsp.CompletionItem

import scala.collection.mutable.ListBuffer
import scala.compiletime.uninitialized

class AutoCompleteWindow(x: Float, y: Float, width: Float, height: Float) extends FloatingWindow(x, y, width, height) {

  private var suggestions: ListBuffer[CompletionItem] = ListBuffer[CompletionItem]()
  private var preferredWidth: Float                   = width
  var buffer: TextBuffer                              = uninitialized

  this.component = new TextComponent().setBuffer(BufferManager.addEmptyBuffer())
  buffer = this.getComponent.asInstanceOf[TextComponent].getBuffer
  this.visible = false

  def getPreferredWidth(): Float = {
    preferredWidth
  }

  def setSuggestions(suggestions: ListBuffer[CompletionItem]): Unit = {
    this.suggestions = suggestions
    this.visible = suggestions.nonEmpty
    var maxWidth = 0.0f
    suggestions.foreach(ci => {
      val label = ci.getLabel()
      if (label != null && label.nonEmpty) {
        maxWidth = Math.max(maxWidth, FontManager.measureText(label))
      }
    })
    val padding = Math.max(12.0f, FontManager.getAvgWidth() * 2.0f)
    preferredWidth = Math.max(0.0f, maxWidth + padding)
    buffer.setLines(suggestions.map(x => new StringBuilder(x.getLabel())).toArray)
    buffer.moveCursor(0, 0)
  }

  def select(): CompletionItem = {
    if (visible && suggestions.nonEmpty) {
      hide()
      val index = Math.min(buffer.cursorY, suggestions.length - 1)
      val ci    = suggestions(index)
      buffer.moveCursor(0, 0)
      ci
    } else {
      null
    }
  }
}
