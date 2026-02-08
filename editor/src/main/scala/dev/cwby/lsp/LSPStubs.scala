package dev.cwby.lsp

import upickle.default._

case class Position(line: Int, character: Int) derives ReadWriter {
  def getLine(): Int      = line
  def getCharacter(): Int = character
}

object Position {
  def apply(): Position = Position(0, 0)
}

case class Range(start: Position, end: Position) derives ReadWriter {
  def getStart(): Position = start
  def getEnd(): Position   = end
}

object Range {
  def apply(): Range = Range(Position(0, 0), Position(0, 0))
}

case class Location(uri: String = "", range: Range = Range()) derives ReadWriter {
  def getUri(): String  = uri
  def getRange(): Range = range
}

case class TextEdit(range: Range = Range(), newText: String = "") derives ReadWriter {
  def getRange(): Range    = range
  def getNewText(): String = newText
}

enum CompletionItemKind(val value: Int) {
  case Text          extends CompletionItemKind(1)
  case Method        extends CompletionItemKind(2)
  case Function      extends CompletionItemKind(3)
  case Constructor   extends CompletionItemKind(4)
  case Field         extends CompletionItemKind(5)
  case Variable      extends CompletionItemKind(6)
  case Class         extends CompletionItemKind(7)
  case Interface     extends CompletionItemKind(8)
  case Module        extends CompletionItemKind(9)
  case Property      extends CompletionItemKind(10)
  case Unit          extends CompletionItemKind(11)
  case Value         extends CompletionItemKind(12)
  case Enum          extends CompletionItemKind(13)
  case Keyword       extends CompletionItemKind(14)
  case Snippet       extends CompletionItemKind(15)
  case Color         extends CompletionItemKind(16)
  case File          extends CompletionItemKind(17)
  case Reference     extends CompletionItemKind(18)
  case Folder        extends CompletionItemKind(19)
  case EnumMember    extends CompletionItemKind(20)
  case Constant      extends CompletionItemKind(21)
  case Struct        extends CompletionItemKind(22)
  case Event         extends CompletionItemKind(23)
  case Operator      extends CompletionItemKind(24)
  case TypeParameter extends CompletionItemKind(25)
}

object CompletionItemKind {
  given ReadWriter[CompletionItemKind] = readwriter[Int].bimap(
    kind => kind.value,
    value => CompletionItemKind.values.find(_.value == value).getOrElse(CompletionItemKind.Text)
  )
}

case class CompletionItem(
    label: String = "",
    kind: Option[CompletionItemKind] = None,
    detail: Option[String] = None,
    documentation: Option[String] = None,
    insertText: Option[String] = None,
    textEdit: Option[TextEdit] = None
) derives ReadWriter {
  def getLabel(): String            = label
  def getKind(): CompletionItemKind = kind.getOrElse(CompletionItemKind.Text)
  def getTextEdit(): TextEdit       = textEdit.orNull
}

case class CompletionList(
    isIncomplete: Boolean = false,
    items: List[CompletionItem] = Nil
) derives ReadWriter

case class LocationLink(
    originSelectionRange: Option[Range] = None,
    targetUri: String = "",
    targetRange: Range = Range(),
    targetSelectionRange: Range = Range()
) derives ReadWriter

case class Diagnostic(
    range: Range = Range(),
    severity: Option[Int] = None,
    code: Option[String] = None,
    source: Option[String] = None,
    message: String = ""
) derives ReadWriter {
  def getMessage(): String = message
}

case class PublishDiagnosticsParams(
    uri: String = "",
    diagnostics: List[Diagnostic] = Nil
) derives ReadWriter {
  def getUri(): String                   = uri
  def getDiagnostics(): List[Diagnostic] = diagnostics
}

case class MessageParams(
    `type`: Int = 1,
    message: String = ""
) derives ReadWriter {
  def getMessage(): String = message
}

case class ShowMessageRequestParams(
    `type`: Int = 1,
    message: String = "",
    actions: Option[List[MessageActionItem]] = None
) derives ReadWriter {
  def getMessage(): String = message
}

case class MessageActionItem(title: String = "") derives ReadWriter

case class TextDocumentIdentifier(uri: String = "") derives ReadWriter

case class VersionedTextDocumentIdentifier(
    uri: String = "",
    version: Int = 0
) derives ReadWriter

case class TextDocumentItem(
    uri: String = "",
    languageId: String = "",
    version: Int = 0,
    text: String = ""
) derives ReadWriter

case class TextDocumentPositionParams(
    textDocument: TextDocumentIdentifier = TextDocumentIdentifier(),
    position: Position = Position()
) derives ReadWriter

case class CompletionParams(
    textDocument: TextDocumentIdentifier = TextDocumentIdentifier(),
    position: Position = Position()
) derives ReadWriter

case class DefinitionParams(
    textDocument: TextDocumentIdentifier = TextDocumentIdentifier(),
    position: Position = Position()
) derives ReadWriter

case class DidOpenTextDocumentParams(
    textDocument: TextDocumentItem = TextDocumentItem()
) derives ReadWriter

case class TextDocumentContentChangeEvent(
    range: Option[Range] = None,
    text: String = ""
) derives ReadWriter

case class DidChangeTextDocumentParams(
    textDocument: VersionedTextDocumentIdentifier = VersionedTextDocumentIdentifier(),
    contentChanges: List[TextDocumentContentChangeEvent] = Nil
) derives ReadWriter

case class ClientCapabilities(
    textDocument: Option[ujson.Value] = None,
    workspace: Option[ujson.Value] = None
) derives ReadWriter

case class InitializeParams(
    processId: Option[Int] = None,
    rootPath: Option[String] = None,
    rootUri: Option[String] = None,
    capabilities: ClientCapabilities = ClientCapabilities()
) derives ReadWriter

case class ServerCapabilities(
    textDocumentSync: Option[ujson.Value] = None,
    completionProvider: Option[ujson.Value] = None,
    definitionProvider: Option[Boolean] = None
) derives ReadWriter

case class ServerInfo(
    name: String = "",
    version: Option[String] = None
) derives ReadWriter

case class InitializeResult(
    capabilities: ServerCapabilities = ServerCapabilities(),
    serverInfo: Option[ServerInfo] = None
) derives ReadWriter
