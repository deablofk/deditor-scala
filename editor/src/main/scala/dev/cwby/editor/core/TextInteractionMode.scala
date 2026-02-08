package dev.cwby.editor.core

enum TextInteractionMode(private val name: String) {

  case INSERT       extends TextInteractionMode("INSERT-MODE")
  case SELECT       extends TextInteractionMode("SELECT-MODE")
  case SELECT_LINE  extends TextInteractionMode("SELECT-LINE-MODE")
  case SELECT_BLOCK extends TextInteractionMode("SELECT-BLOCK-MODE")
  case COMMAND      extends TextInteractionMode("COMMAND-MODE")
  case SEARCH       extends TextInteractionMode("SEARCH-MODE")
  case NAVIGATION   extends TextInteractionMode("NAVIGATION-MODE")
  case ANY          extends TextInteractionMode("ANY-MODE")

  override def toString: String = name
}
