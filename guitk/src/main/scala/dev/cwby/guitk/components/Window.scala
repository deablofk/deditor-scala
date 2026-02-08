package dev.cwby.guitk.components

import dev.cwby.guitk.text.FontManager
import dev.cwby.guitk.events.{EventDispatcher, WindowOpenEvent, WindowCloseEvent}

class Window(
    var title: String,
    var x: Float,
    var y: Float,
    var width: Float,
    var height: Float,
    var component: IComponent = null,
    var visible: Boolean = true,
    var offsetX: Int = 0,
    var offsetY: Int = 0,
    private var visibleLinesCache: Int = 0
):

  def open(): Unit = {
    EventDispatcher.dispatch(WindowOpenEvent(this))
  }

  def close(): Unit = {
    EventDispatcher.dispatch(WindowCloseEvent(this))
    onClose()
  }

  def onTrigger(): Unit = {}

  def onClose(): Unit = {}

  def hide(): Unit = this.visible = false

  def isVisible: Boolean = visible

  def getComponent: IComponent = component

  def setComponent(component: IComponent): Unit = this.component = component

  def getVisibleLines: Int = {
    val calculated = (height / FontManager.getLineHeight()).toInt

    if calculated > 0 then calculated
    else visibleLinesCache
  }

  def setVisibleLines(visibleLines: Int): Unit = {
    this.visibleLinesCache = visibleLines
  }

  def resetScroll(): Unit = {
    offsetX = 0
    offsetY = 0
  }
