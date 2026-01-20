package dev.cwby.graphics.layout.component

import dev.cwby.graphics.opengl.Renderer2D

trait IComponent {
  def render(renderer: Renderer2D, x: Float, y: Float, width: Float, height: Float): Unit
}
