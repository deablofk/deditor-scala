package dev.cwby.guitk.components


final val DIRECTION_LEFT: Int  = 0
final val DIRECTION_RIGHT: Int = 1
final val DIRECTION_UP: Int    = 2
final val DIRECTION_DOWN: Int  = 3

final val SPLIT_NONE: Int       = 0
final val SPLIT_VERTICAL: Int   = 1
final val SPLIT_HORIZONTAL: Int = 2

final class TiledWindow[Buffer](
    initialX: Float,
    initialY: Float,
    initialWidth: Float,
    initialHeight: Float,
    var father: TiledWindow[Buffer],
    var splitRatio: Float = 0.5f,
    var leftChild: TiledWindow[Buffer] = null,
    var rightChild: TiledWindow[Buffer] = null,
    var splitType: Int = SPLIT_NONE,
    private var callbacks: WindowCallbacks[Buffer] = WindowCallbacks.empty[Buffer]
) extends Window[Buffer]("", initialX, initialY, initialWidth, initialHeight) {

  inline def isLeaf: Boolean = leftChild == null && rightChild == null

  def splitVertically(): Unit = doVerticalSplit(width * splitRatio)

  def splitHorizontally(): Unit = doHorizontalSplit(height * splitRatio)

  def splitVertically(leftWidth: Float): Unit = doVerticalSplit(leftWidth)

  def splitHorizontally(topHeight: Float): Unit = doHorizontalSplit(topHeight)

  inline private def doVerticalSplit(leftWidth: Float): Unit = {
    if leftWidth <= 0 || leftWidth >= width then
      throw IllegalArgumentException("Left width must be between 0 and the total width.")

    splitRatio = leftWidth / width
    splitType = SPLIT_VERTICAL

    leftChild = TiledWindow(x, y, leftWidth, height, this)
    rightChild = TiledWindow(x + leftWidth, y, width - leftWidth, height, this)

    updateSize(x, y, width, height)
  }

  inline private def doHorizontalSplit(topHeight: Float): Unit = {
    if topHeight <= 0 || topHeight >= height then
      throw IllegalArgumentException("Top height must be between 0 and the total height.")

    splitRatio = topHeight / height
    splitType = SPLIT_HORIZONTAL

    leftChild = TiledWindow(x, y, width, topHeight, this)
    rightChild = TiledWindow(x, y + topHeight, width, height - topHeight, this)

    updateSize(x, y, width, height)
  }

  def updateSize(newX: Float, newY: Float, newWidth: Float, newHeight: Float): Unit = {
    x = newX
    y = newY
    width = newWidth
    height = newHeight

    if isLeaf then return

    if splitType == SPLIT_VERTICAL then
      val leftWidth  = width * splitRatio
      val rightWidth = width - leftWidth

      leftChild.updateSize(x, y, leftWidth, height)
      rightChild.updateSize(x + leftWidth, y, rightWidth, height)
    else if splitType == SPLIT_HORIZONTAL then
      val topHeight    = height * splitRatio
      val bottomHeight = height - topHeight

      leftChild.updateSize(x, y, width, topHeight)
      rightChild.updateSize(x, y + topHeight, width, bottomHeight)
  }

  private def findNeighbor(direction: Int): TiledWindow[Buffer] = {
    var node = this
    while node.father != null do
      val parent  = node.father
      val sibling = if node eq parent.leftChild then parent.rightChild else parent.leftChild

      if sibling != null then
        val found = (direction == DIRECTION_LEFT && sibling.x + sibling.width == node.x) ||
          (direction == DIRECTION_RIGHT && sibling.x == node.x + node.width) ||
          (direction == DIRECTION_UP && sibling.y + sibling.height == node.y) ||
          (direction == DIRECTION_DOWN && sibling.y == node.y + node.height)

        if found then return findLeaf(sibling)

      node = node.father

    null
  }

  inline private def findLeaf(node: TiledWindow[Buffer]): TiledWindow[Buffer] = {
    var current = node

    while !current.isLeaf do
      val dxL = current.leftChild.x - this.x
      val dyL = current.leftChild.y - this.y
      val dxR = current.rightChild.x - this.x
      val dyR = current.rightChild.y - this.y

      val leftDist2  = dxL * dxL + dyL * dyL
      val rightDist2 = dxR * dxR + dyR * dyR

      val closer  = if leftDist2 < rightDist2 then current.leftChild else current.rightChild
      val farther = if closer eq current.leftChild then current.rightChild else current.leftChild

      val xAligned =
        (this.x >= closer.x && this.x < closer.x + closer.width) || (closer.x >= this.x && closer.x < this.x + this.width)
      val yAligned =
        (this.y >= closer.y && this.y < closer.y + closer.height) || (closer.y >= this.y && closer.y < this.y + this.height)

      current = if xAligned || yAligned then closer else farther

    current
  }

  inline private def move(direction: Int): TiledWindow[Buffer] = {
    val neighbor = findNeighbor(direction)

    if neighbor != null then callbacks.onWindowFocused(neighbor)

    neighbor
  }

  inline def moveLeft(): TiledWindow[Buffer] = move(DIRECTION_LEFT)

  inline def moveRight(): TiledWindow[Buffer] = move(DIRECTION_RIGHT)

  inline def moveUp(): TiledWindow[Buffer] = move(DIRECTION_UP)

  inline def moveDown(): TiledWindow[Buffer] = move(DIRECTION_DOWN)

  inline private def reattachSibling(sibling: TiledWindow[Buffer], parent: TiledWindow[Buffer]): Unit = {
    val grandParent = parent.father

    sibling.father = grandParent

    if grandParent == null then callbacks.onWindowFocused(sibling)
    else if grandParent.leftChild eq parent then grandParent.leftChild = sibling
    else grandParent.rightChild = sibling

    sibling.updateSize(parent.x, parent.y, parent.width, parent.height)
    callbacks.onWindowFocused(findLeaf(sibling))
  }

  inline private def clearParentReference(parent: TiledWindow[Buffer]): Unit = {
    val grandParent = parent.father

    if grandParent != null then
      if grandParent.leftChild eq parent then grandParent.leftChild = null
      else grandParent.rightChild = null

    if grandParent != null then callbacks.onWindowFocused(grandParent)
  }

  def setCallbacks(cb: WindowCallbacks[Buffer]): Unit = {
    this.callbacks = cb
    if leftChild != null then leftChild.setCallbacks(cb)
    if rightChild != null then rightChild.setCallbacks(cb)
  }

  override def onClose(): Unit = {
    val parent = this.father

    if parent == null then
      callbacks.onRootWindowClosed()
      return

    val sibling = if this == parent.leftChild then parent.rightChild else parent.leftChild

    if sibling != null then reattachSibling(sibling, parent)
    else clearParentReference(parent)
  }

  override def toString: String = {
    val sb = StringBuilder()

    sb.append(
      s"RegionNode: [x=$x, y=$y, width=$width, height=$height, splitType=$splitType, isLeaf=$isLeaf]"
    )

    if leftChild != null then
      sb.append(if splitType == SPLIT_HORIZONTAL then "  T-> " else " L-> ")
      sb.append(leftChild.toString)

    if rightChild != null then
      sb.append(if splitType == SPLIT_HORIZONTAL then "  D-> " else " R-> ")
      sb.append(rightChild.toString)

    sb.toString()
  }

}
