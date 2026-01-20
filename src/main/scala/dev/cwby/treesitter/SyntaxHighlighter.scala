package dev.cwby.treesitter

import dev.cwby.bindings.TreeSitter
import dev.cwby.bindings.TreeSitterLib._
import dev.cwby.getConfig
import dev.cwby.treesitter.TreeSitterGrammarManager

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.scalanative.libc.stdlib
import scala.scalanative.unsafe._
import scala.scalanative.unsigned._

class Node(val nodePtr: Ptr[TSNode]) {
  def getType(): String = {
    Zone {
      val typePtr = ts_node_type_ptr(nodePtr)
      if (typePtr == null) {
        return "<null>"
      }
      fromCString(typePtr)
    }
  }

  def getStartByte(): Int = ts_node_start_byte_ptr(nodePtr).toInt

  def getEndByte(): Int = ts_node_end_byte_ptr(nodePtr).toInt

  def getChildren(): List[Node] = {
    val count = ts_node_child_count_ptr(nodePtr).toInt
    (0 until count).map { i =>
      val childPtr = stdlib.malloc(sizeof[TSNode]).asInstanceOf[Ptr[TSNode]]
      ts_node_child_ptr(nodePtr, i.toUInt, childPtr)
      new Node(childPtr)
    }.toList
  }

  def isNull(): Boolean = ts_node_is_null_ptr(nodePtr)

  def free(): Unit = {
    stdlib.free(nodePtr.asInstanceOf[Ptr[Byte]])
  }
}

class Language(val tsLanguage: TSLanguage)

class Parser(language: Language) {
  private val parser = ts_parser_new()
  ts_parser_set_language(parser, language.tsLanguage)

  def parse(code: String): Option[Tree] = {
    Zone {
      val cStr = toCString(code)
      val tree = ts_parser_parse_string(parser, null, cStr, code.length.toUInt)
      if (tree != null) Some(new Tree(tree)) else None
    }
  }

  def close(): Unit = {
    ts_parser_delete(parser)
  }
}

class Tree(val tsTree: TSTree) {
  def getRootNode(): Node = {
    // Allocate space for TSNode struct in unmanaged memory
    val nodePtr = stdlib.malloc(sizeof[TSNode]).asInstanceOf[Ptr[TSNode]]
    // Use wrapper function to get root node via pointer
    ts_tree_root_node_ptr(tsTree, nodePtr)
    new Node(nodePtr)
  }

  def delete(): Unit = {
    ts_tree_delete(tsTree)
  }
}

object SyntaxHighlighter {
  private val parsedCache: mutable.Map[String, Node]       = mutable.Map[String, Node]()
  private val treeCache: mutable.Map[String, Tree]         = mutable.Map[String, Tree]()
  private val languageCache: mutable.Map[String, Language] = mutable.Map[String, Language]()
  private val queryCache: mutable.Map[String, TSQuery]     = mutable.Map.empty
  private val MAX_CACHE_ENTRIES                            = 200 // Increased cache size
  private val LRU_CACHE_TRACKER                            = mutable.ListBuffer[String]()

  def loadTSLanguage(libraryPath: String, language: String): Language = {
    if (languageCache.contains(language)) {
      return languageCache(language)
    }

    val tsLanguage     = TreeSitter.loadLanguage(libraryPath, language)
    val loadedLanguage = new Language(tsLanguage)
    languageCache(language) = loadedLanguage
    loadedLanguage
  }

  def parse(code: String, fileType: String): Node = {
    if (parsedCache.contains(code)) {
      // Move to end of LRU list
      LRU_CACHE_TRACKER -= code
      LRU_CACHE_TRACKER += code
      return parsedCache(code)
    }

    // Clean up cache if needed
    if (parsedCache.size >= MAX_CACHE_ENTRIES) {
      val toRemove = LRU_CACHE_TRACKER.take(50).toList
      toRemove.foreach { key =>
        treeCache.get(key).foreach(_.delete())
        parsedCache.get(key).foreach(_.free())
        treeCache.remove(key)
        parsedCache.remove(key)
        LRU_CACHE_TRACKER -= key
      }
    }

    val tsLanguage = TreeSitterGrammarManager.resolveLanguageForFileType(fileType, getConfig.treesitterParsers)
    val filePath   = TreeSitterGrammarManager
      .resolveParserPath(tsLanguage, getConfig.treesitterParsers)
      .getOrElse(throw new RuntimeException("No Tree-sitter parser available for: " + tsLanguage))
    val language = loadTSLanguage(filePath, s"tree_sitter_$tsLanguage")
    val parser   = new Parser(language)

    val tree    = parser.parse(code)
    val treeObj = tree.getOrElse({
      parser.close()
      throw new RuntimeException("Failed to parse code")
    })
    val rootNode = treeObj.getRootNode()

    // CRITICAL: Keep tree alive! TSNode pointers are only valid while tree exists
    treeCache(code) = treeObj
    parsedCache(code) = rootNode
    LRU_CACHE_TRACKER += code
    parser.close()
    rootNode
  }

  def highlight(root: Node, code: String): mutable.Map[Int, Int] = {
    val styles     = mutable.Map[Int, Int]()
    val highlights = traverseTree(root, code)
    for (span <- highlights) {
      for (i <- span.start until span.end) {
        styles(i) = span.color
      }
    }
    styles
  }

  def highlight(code: String, fileType: String): mutable.Map[Int, Int] = {
    val root = parse(code, fileType)
    highlightWithQueriesOrFallback(root, code, fileType)
  }

  private def highlightWithQueriesOrFallback(root: Node, code: String, fileType: String): mutable.Map[Int, Int] = {
    try {
      val tsLanguage = TreeSitterGrammarManager.resolveLanguageForFileType(fileType, getConfig.treesitterParsers)
      val query      = getHighlightsQuery(tsLanguage)
      query match {
        case Some(q) => highlightWithQuery(root, code, q, tsLanguage)
        case None    => highlight(root, code)
      }
    } catch {
      case _: Throwable => highlight(root, code)
    }
  }

  private def getHighlightsQuery(tsLanguage: String): Option[TSQuery] = {
    if (tsLanguage == null || tsLanguage.isEmpty) return None

    queryCache.get(tsLanguage) match {
      case Some(q) => Some(q)
      case None    =>
        TreeSitterGrammarManager.ensureRepo(tsLanguage, getConfig.treesitterParsers)
        val queryPath = resolveHighlightsQueryPath(tsLanguage)
        if (queryPath.isEmpty) return None

        val source = Files.readAllBytes(queryPath.get.toPath)
        if (source == null || source.isEmpty) return None

        val filePath = TreeSitterGrammarManager
          .resolveParserPath(tsLanguage, getConfig.treesitterParsers)
          .getOrElse(return None)
        val language = loadTSLanguage(filePath, s"tree_sitter_$tsLanguage")

        Zone {
          val cStr        = toCString(new String(source, StandardCharsets.UTF_8))
          val errorOffset = stackalloc[UInt]()
          val errorType   = stackalloc[TSQueryError]()
          !errorOffset = 0.toUInt
          !errorType = 0

          val q = ts_query_new_ptr(language.tsLanguage, cStr, source.length.toUInt, errorOffset, errorType)
          if (q == null) {
            None
          } else {
            queryCache(tsLanguage) = q
            Some(q)
          }
        }
    }
  }

  private def resolveHighlightsQueryPath(tsLanguage: String): Option[File] = {
    val home = System.getProperty("user.home")
    if (home == null || home.isEmpty) return None

    val paths = Seq(
      new File(home + "/.config/deditor/treesitter/repos/" + tsLanguage + "/queries/highlights.scm"),
      new File(
        home + "/.config/deditor/treesitter/repos/" + tsLanguage + "/tree-sitter-" + tsLanguage + "/queries/highlights.scm"
      )
    )

    paths.find(f => f.exists() && f.isFile)
  }

  private def highlightWithQuery(root: Node, code: String, query: TSQuery, language: String): mutable.Map[Int, Int] = {
    val styles     = mutable.Map[Int, Int]()
    val priorities = mutable.Map[Int, Int]()
    val byteToChar = buildUtf8ByteToCharIndex(code)

    Zone {
      val cursor = ts_query_cursor_new_ptr()
      if (cursor == null) return styles

      try {
        ts_query_cursor_exec_ptr(cursor, query, root.nodePtr)

        val startByte      = stackalloc[UInt]()
        val endByte        = stackalloc[UInt]()
        val captureNamePtr = stackalloc[CString]()
        val captureNameLen = stackalloc[UInt]()

        var has =
          ts_query_cursor_next_capture_bytes_ptr(cursor, query, startByte, endByte, captureNamePtr, captureNameLen)
        while (has) {
          val sB = (!startByte).toInt
          val eB = (!endByte).toInt

          val s = byteOffsetToCharIndex(byteToChar, sB)
          val e = byteOffsetToCharIndex(byteToChar, eB)

          val capPtr = !captureNamePtr
          val cName  =
            if (capPtr != null) {
              val raw = fromCString(capPtr)
              if (raw != null) raw else ""
            } else {
              ""
            }

          val color    = getColorForCapture(cName, language)
          val priority = getCapturePriority(cName)
          if (e > s) {
            var i = s
            while (i < e && i < code.length) {
              val currentPriority = priorities.getOrElse(i, 0)
              if (priority >= currentPriority) {
                styles(i) = color
                priorities(i) = priority
              }
              i += 1
            }
          }

          has =
            ts_query_cursor_next_capture_bytes_ptr(cursor, query, startByte, endByte, captureNamePtr, captureNameLen)
        }
      } finally {
        ts_query_cursor_delete_ptr(cursor)
      }
    }

    styles
  }

  private def getCapturePriority(captureName: String): Int = {
    val base = if (captureName.startsWith("@")) captureName.substring(1) else captureName

    if (base.startsWith("text.")) return 100
    if (base.startsWith("markup.")) return 90
    if (base.startsWith("string")) return 80
    if (base.startsWith("comment")) return 70
    if (base.startsWith("keyword")) return 60
    if (base.startsWith("type")) return 50
    if (base.startsWith("function")) return 40
    if (base.startsWith("variable")) return 30
    if (base.startsWith("constant")) return 30
    if (base.startsWith("punctuation")) return 10
    if (base.startsWith("operator")) return 10

    20
  }

  private def getColorForCapture(captureName: String, language: String): Int = {
    val mapTheme = getConfig.treesitter
    val default  = mapTheme.getOrElse("default", 0xffffffff)
    if (captureName == null || captureName.isEmpty) return default

    val base = if (captureName.startsWith("@")) captureName.substring(1) else captureName

    if (language == "markdown") {
      if (!base.startsWith("markup.") && !base.startsWith("punctuation.") && !base.startsWith("text.")) {
        val isCodeCapture = base == "type" || base == "variable" || base == "constant" ||
          base == "function" || base.startsWith("function.") || base == "keyword" ||
          base == "number" || base == "string" || base == "comment"
        if (isCodeCapture) return default
      }
    }

    var key: String | Null = base
    while (key != null && key.nonEmpty) {
      mapTheme.get(key) match {
        case Some(c) => return c
        case None    =>
      }
      val idx = key.lastIndexOf('.')
      if (idx < 0) key = null else key = key.substring(0, idx)
    }

    mapTheme.getOrElse("@" + base, default)
  }

  private def buildUtf8ByteToCharIndex(s: String): Array[Int] = {
    if (s == null || s.isEmpty) return Array(0)
    val bytes = s.getBytes(StandardCharsets.UTF_8)
    val out   = new Array[Int](bytes.length + 1)

    var charIndex = 0
    var byteIndex = 0
    out(0) = 0

    while (charIndex < s.length && byteIndex < bytes.length) {
      val cp        = s.codePointAt(charIndex)
      val charCount = Character.charCount(cp)
      val utf8Len   =
        if (cp <= 0x7f) 1
        else if (cp <= 0x7ff) 2
        else if (cp <= 0xffff) 3
        else 4

      val nextByte = Math.min(bytes.length, byteIndex + utf8Len)
      val nextChar = charIndex + charCount
      out(nextByte) = nextChar

      byteIndex = nextByte
      charIndex = nextChar
    }

    if (byteIndex < out.length) {
      out(out.length - 1) = s.length
    }
    out
  }

  private def byteOffsetToCharIndex(byteToChar: Array[Int], byteOffset: Int): Int = {
    if (byteToChar == null || byteToChar.isEmpty) return 0
    if (byteOffset <= 0) return 0
    if (byteOffset >= byteToChar.length) return byteToChar(byteToChar.length - 1)

    var i = byteOffset
    while (i >= 0) {
      val v = byteToChar(i)
      if (v != 0 || i == 0) return v
      i -= 1
    }
    0
  }

  def traverseTree(node: Node, code: String): ListBuffer[HighlightSpan] = {
    val spans = ListBuffer[HighlightSpan]()

    if (node.isNull()) {
      return spans
    }

    val nodeType = node.getType()
    val start    = node.getStartByte()
    val end      = node.getEndByte()
    val color    = getColorForType(nodeType)

    spans.addOne(HighlightSpan(start, end, color))

    for (child <- node.getChildren()) {
      spans.addAll(traverseTree(child, code))
    }

    spans
  }

  private def getColorForType(nodeType: String): Int = {
    val mapTheme = getConfig.treesitter
    mapTheme.getOrElse(nodeType, mapTheme.getOrElse("default", 0xffffffff))
  }

  // Optimized method for simple text (like file paths) that doesn't need full parsing
  def getSimpleHighlighting(code: String): mutable.Map[Int, Int] = {
    val styles       = mutable.Map[Int, Int]()
    val defaultColor = getConfig.treesitter.getOrElse("default", 0xffffffff)
    // For simple text, just use default color for everything
    for (i <- 0 until code.length) {
      styles(i) = defaultColor
    }
    styles
  }
}
