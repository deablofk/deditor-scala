package dev.cwby.toml

import java.io.File
import scala.collection.mutable

object TomlType {
  final val STRING = 1
  final val INT    = 2
  final val FLOAT  = 3
  final val BOOL   = 4
  final val ARRAY  = 5
  final val TABLE  = 6
}

final class TomlValue {
  var kind: Int                               = 0
  var s: String                               = ""
  var i: Long                                 = 0L
  var f: Double                               = 0.0
  var b: Boolean                              = false
  var arr: mutable.ArrayBuffer[TomlValue]     = null
  var tbl: mutable.HashMap[String, TomlValue] = null
}

extension (v: TomlValue) {

  @inline def get(key: String): TomlValue = {
    if (v.kind != TomlType.TABLE || v.tbl == null) then null
    else v.tbl.getOrElse(key, null)
  }

  @inline def has(key: String): Boolean = {
    v.kind == TomlType.TABLE && v.tbl != null && v.tbl.contains(key)
  }

  @inline def asString: String = {
    if (v.kind == TomlType.STRING) v.s else ""
  }

  @inline def asLong: Long = {
    if (v.kind == TomlType.INT) v.i else 0L
  }

  @inline def asDouble: Double = {
    if (v.kind == TomlType.FLOAT) v.f else 0.0
  }

  @inline def asBoolean: Boolean = {
    if (v.kind == TomlType.BOOL) v.b else false
  }

}

object TokenKind {
  final val EOF     = 0
  final val IDENT   = 1
  final val STRING  = 2
  final val INT     = 3
  final val FLOAT   = 4
  final val BOOL    = 5
  final val EQUALS  = 6
  final val DOT     = 7
  final val COMMA   = 8
  final val LBRACK  = 9
  final val RBRACK  = 10
  final val NEWLINE = 11
}

final class Token {
  var kind: Int    = 0
  var text: String = ""
  var i: Long      = 0L
  var f: Double    = 0.0
  var b: Boolean   = false
}

@inline def newString(value: String): TomlValue = {
  val t = TomlValue()
  t.kind = TomlType.STRING
  t.s = value
  t
}

@inline def newInt(value: Long): TomlValue = {
  val t = TomlValue()
  t.kind = TomlType.INT
  t.i = value
  t
}

@inline def newFloat(value: Double): TomlValue = {
  val t = TomlValue()
  t.kind = TomlType.FLOAT
  t.f = value
  t
}

@inline def newBool(value: Boolean): TomlValue = {
  val t = TomlValue()
  t.kind = TomlType.BOOL
  t.b = value
  t
}

@inline def newArray(): TomlValue = {
  val t = TomlValue()
  t.kind = TomlType.ARRAY
  t.arr = mutable.ArrayBuffer.empty
  t
}

@inline def newTable(): TomlValue = {
  val t = TomlValue()
  t.kind = TomlType.TABLE
  t.tbl = mutable.HashMap.empty
  t
}

final class Lexer(input: String) {
  var pos: Int = 0
  var tokens   = mutable.ArrayBuffer[Token]()

  @inline def peek: Char = {
    if (pos >= input.length) 0.toChar else input.charAt(pos)
  }

  @inline def next(): Char = {
    val c = peek
    pos += 1
    c
  }

  def tokenize(): mutable.ArrayBuffer[Token] = {
    while (true) {
      val c = peek

      if (c == 0) {
        emit(TokenKind.EOF)
        return tokens
      }

      if (c == ' ' || c == '\t' || c == '\r') {
        next()
      } else if (c == '\n') {
        next()
        emit(TokenKind.NEWLINE)
      } else if (c == '#') {
        while (peek != '\n' && peek != 0) next()
      } else if (c == '=') {
        next()
        emit(TokenKind.EQUALS)
      } else if (c == '.') {
        next()
        emit(TokenKind.DOT)
      } else if (c == ',') {
        next()
        emit(TokenKind.COMMA)
      } else if (c == '[') {
        next()
        emit(TokenKind.LBRACK)
      } else if (c == ']') {
        next()
        emit(TokenKind.RBRACK)
      } else if (c == '"') {
        readString()
      } else if (c.isDigit || c == '-') {
        readNumber()
      } else if (c.isLetter) {
        readIdent()
      } else {
        next() // skip unknown
      }
    }
    tokens
  }

  private def emit(k: Int): Unit = {
    val t = Token()
    t.kind = k
    tokens += t
  }

  private def readString(): Unit = {
    next()
    val sb = StringBuilder()
    while (peek != 0) {
      val c = next()

      if (c == '"') {
        // end of string
        val t = Token()
        t.kind = TokenKind.STRING
        t.text = sb.toString()
        tokens += t
        return
      }

      if (c == '\\') {
        val esc = next()
        esc match {
          case '"'   => sb.append('"')
          case '\\'  => sb.append('\\')
          case 'n'   => sb.append('\n')
          case 't'   => sb.append('\t')
          case 'r'   => sb.append('\r')
          case other => sb.append(other) // unknown escape, just append
        }
      } else {
        sb.append(c)
      }
    }

    val t = Token()
    t.kind = TokenKind.STRING
    t.text = sb.toString()
    tokens += t
  }

  private def readIdent(): Unit = {
    val sb = StringBuilder()
    while (peek.isLetterOrDigit || peek == '_' || peek == '-') {
      sb.append(next())
    }

    val s = sb.toString
    val t = Token()
    s match {
      case "true" =>
        t.kind = TokenKind.BOOL
        t.b = true
      case "false" =>
        t.kind = TokenKind.BOOL
        t.b = false
      case _ =>
        t.kind = TokenKind.IDENT
        t.text = s
    }

    tokens += t
  }

  private def readNumber(): Unit = {
    val sb  = StringBuilder()
    var dot = false
    while (peek.isDigit || peek == '.' || peek == '-') {
      if (peek == '.') {
        dot = true
      }

      sb.append(next())
    }
    val t = Token()
    if (dot) {
      t.kind = TokenKind.FLOAT
      t.f = sb.toString.toDouble
    } else {
      t.kind = TokenKind.INT
      t.i = sb.toString.toLong
    }

    tokens += t
  }
}

final class Parser(tokens: mutable.ArrayBuffer[Token]) {
  var pos: Int = 0
  val root     = newTable()
  var current  = root

  @inline def peek: Token = tokens(pos)

  @inline def next(): Token = {
    val t = peek;
    pos += 1;
    t
  }

  def parse(): TomlValue = {
    while (peek.kind != TokenKind.EOF) {
      peek.kind match {
        case TokenKind.NEWLINE =>
          next()
        case TokenKind.LBRACK =>
          parseTableHeader()
        case TokenKind.IDENT | TokenKind.STRING =>
          parseKeyValue()
        case _ =>
          next()
      }
    }

    root
  }

  private def parseTableHeader(): Unit = {
    next() // consume first '['

    val isArrayTable =
      if (peek.kind == TokenKind.LBRACK) {
        next() // consume second '['
        true
      } else {
        false
      }

    val segments = parseDottedKeySegments()

    if (isArrayTable) {
      if (peek.kind == TokenKind.RBRACK) next() // consume first ']'
      if (peek.kind == TokenKind.RBRACK) next() // consume second ']'

      val (parent, last) = ensureParentTable(segments)
      val existing       = parent.tbl.getOrElse(last, null)
      val arr            =
        if (existing != null && existing.kind == TomlType.ARRAY && existing.arr != null) {
          existing
        } else {
          val a = newArray()
          parent.tbl.put(last, a)
          a
        }

      val tbl = newTable()
      arr.arr += tbl
      current = tbl
    } else {
      if (peek.kind == TokenKind.RBRACK) next() // consume ']'

      val (parent, last) = ensureParentTable(segments)
      val tbl            = newTable()
      parent.tbl.put(last, tbl)
      current = tbl
    }
  }

  private def parseDottedKeySegments(): Array[String] = {
    val out       = mutable.ArrayBuffer[String]()
    val first     = next()
    val firstName = first.text
    if (firstName != null && firstName.nonEmpty) out += firstName

    while (peek.kind == TokenKind.DOT) {
      next() // consume '.'
      val t    = next()
      val name = t.text
      if (name != null && name.nonEmpty) out += name
    }

    out.toArray
  }

  private def ensureParentTable(segments: Array[String]): (TomlValue, String) = {
    if (segments == null || segments.isEmpty) {
      return (root, "")
    }

    var tbl = root
    var i   = 0
    while (i < segments.length - 1) {
      val seg      = segments(i)
      val existing = tbl.tbl.getOrElse(seg, null)
      if (existing != null && existing.kind == TomlType.TABLE && existing.tbl != null) {
        tbl = existing
      } else {
        val created = newTable()
        tbl.tbl.put(seg, created)
        tbl = created
      }
      i += 1
    }

    (tbl, segments(segments.length - 1))
  }

  private def parseKeyValue(): Unit = {
    val segments = parseDottedKeySegments()

    if (segments.isEmpty) return

    // expect '='
    if (peek.kind == TokenKind.EQUALS) {
      next()
      val value = parseValue()

      // Store dotted keys as flat string keys instead of nested tables
      val key = segments.mkString(".")
      current.tbl.put(key, value)
    }
  }

  private def parseValue(): TomlValue = {
    peek.kind match {
      case TokenKind.STRING =>
        val t = next()
        newString(t.text)
      case TokenKind.INT =>
        val t = next()
        newInt(t.i)
      case TokenKind.FLOAT =>
        val t = next()
        newFloat(t.f)
      case TokenKind.BOOL =>
        val t = next()
        newBool(t.b)
      case TokenKind.LBRACK =>
        parseArray()
      case _ =>
        next()
        newString("")
    }
  }

  private def parseArray(): TomlValue = {
    val arr = newArray()
    next() // consume '['

    while (peek.kind != TokenKind.EOF && peek.kind != TokenKind.RBRACK) {
      peek.kind match {
        case TokenKind.COMMA | TokenKind.NEWLINE =>
          next()
        case _ =>
          val v = parseValue()
          arr.arr += v
      }
    }

    if (peek.kind == TokenKind.RBRACK) {
      next() // consume ']'
    }

    arr
  }
}

object Toml {

  def parse(input: String): TomlValue = {
    val lexer  = Lexer(input)
    val tokens = lexer.tokenize()
    val parser = Parser(tokens)
    parser.parse()
  }

  def parse(file: File): TomlValue = {
    val source = scala.io.Source.fromFile(file)
    try {
      val content = source.mkString
      parse(content)
    } finally {
      source.close()
    }
  }

  @inline def print(v: TomlValue): Unit = {
    println(mkString(v))
  }

  @inline def mkString(v: TomlValue): String = {
    val sb = StringBuilder()
    appendValuePrinter(sb, v)
    sb.toString()
  }

  @inline private def appendValuePrinter(sb: StringBuilder, v: TomlValue): Unit = {
    v.kind match {
      case TomlType.STRING =>
        sb.append('"').append(v.s).append('"')
      case TomlType.INT =>
        sb.append(v.i)
      case TomlType.FLOAT =>
        sb.append(v.f)
      case TomlType.BOOL =>
        sb.append(v.b)

      case TomlType.ARRAY =>
        sb.append('[')
        var i = 0
        while (i < v.arr.length) {
          if (i > 0) sb.append(", ")
          appendValuePrinter(sb, v.arr(i))
          i += 1
        }
        sb.append(']')

      case TomlType.TABLE =>
        sb.append('{')
        var first = true
        val it    = v.tbl.iterator
        while (it.hasNext) {
          val (k, v) = it.next()
          if (!first) sb.append(", ")
          first = false
          sb.append(k).append(" = ")
          appendValuePrinter(sb, v)
        }
        sb.append('}')
      case _ =>
        sb.append("<invalid>")
    }
  }
}
