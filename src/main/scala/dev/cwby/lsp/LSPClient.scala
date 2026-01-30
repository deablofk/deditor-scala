package dev.cwby.lsp

import dev.cwby.editor.core.TextBuffer
import upickle.default._

import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ListBuffer
import scala.scalanative.posix.unistd.getpid

/** LSP Client - handles communication with an LSP server over JSON-RPC. Equivalent to lsp4j LanguageServer wrapper.
  */
class LSPClient(
    private val inputStream: InputStream,
    private val outputStream: OutputStream,
    private val listener: LSPClientListener
) {
  private val requestId             = new AtomicInteger(0)
  private val pendingRequests       = new ConcurrentHashMap[Int, CountDownLatch]()
  private val responses             = new ConcurrentHashMap[Int, ujson.Value]()
  private val completionCallbacks   = new ConcurrentHashMap[Int, ListBuffer[CompletionItem] => Unit]()
  private var version               = 1
  var fileSchema: String            = "file://"
  @volatile private var running     = true
  @volatile private var initialized = false

  private val bufferedInput = new BufferedInputStream(inputStream)
  private val writer        = new PrintWriter(outputStream, true)

  def getFileSchema(): String             = fileSchema
  def setFileSchema(schema: String): Unit = fileSchema = schema
  def isInitialized: Boolean              = initialized

  def startListening(): Unit = {
    val listenerThread = new Thread(() => {
      try {
        while (running) {
          val message = readMessage()
          if (message == null) {
            println("[LSP] Stream closed, stopping listener")
            running = false
          } else {
            handleMessage(message)
          }
        }
      } catch {
        case e: Exception =>
          if (running) {
            System.err.println(s"LSP listener error: ${e.getMessage}")
          }
      }
    })
    listenerThread.setDaemon(true)
    listenerThread.setName("LSP-Listener")
    listenerThread.start()
  }

  private def readLine(): String = {
    val sb = new StringBuilder()
    var b  = bufferedInput.read()
    while (b != -1 && b != '\n') {
      if (b != '\r') sb.append(b.toChar)
      b = bufferedInput.read()
    }
    if (b == -1 && sb.isEmpty) null else sb.toString()
  }

  private def readMessage(): String = {
    var contentLength = -1
    var line          = readLine()
    if (line == null) {
      println(s"[LSP-READ] Got null - stream closed/EOF")
      return null
    }
    println(s"[LSP-READ] First header line: $line")

    while (line != null && line.nonEmpty) {
      if (line.startsWith("Content-Length:")) {
        contentLength = line.substring(15).trim.toInt
        println(s"[LSP-READ] Content-Length: $contentLength")
      }
      line = readLine()
    }

    if (contentLength > 0) {
      val content = new Array[Byte](contentLength)
      var read    = 0
      while (read < contentLength) {
        val n = bufferedInput.read(content, read, contentLength - read)
        if (n == -1) return null
        read += n
      }
      val result = new String(content, "UTF-8")
      println(s"[LSP-READ] Message received (${result.length} chars, $contentLength bytes)")
      result
    } else {
      null
    }
  }

  private def handleMessage(content: String): Unit = {
    try {
      val json = ujson.read(content)

      if (json.obj.contains("id") && json.obj.contains("result")) {
        val id = json("id").num.toInt
        println(s"[LSP] Received response for request id=$id")
        responses.put(id, json("result"))
        val latch = pendingRequests.get(id)
        if (latch != null) {
          latch.countDown()
        }
        val callback = completionCallbacks.remove(id)
        if (callback != null) {
          handleCompletionResponse(json("result"), callback)
        }
      } else if (json.obj.contains("id") && json.obj.contains("error")) {
        val id = json("id").num.toInt
        System.err.println(s"LSP Error for request $id: ${json("error")}")
        responses.put(id, ujson.Null)
        val latch = pendingRequests.get(id)
        if (latch != null) {
          latch.countDown()
        }
        val callback = completionCallbacks.remove(id)
        if (callback != null) {
          callback(ListBuffer.empty[CompletionItem])
        }
      } else if (json.obj.contains("method")) {
        val method = json("method").str
        println(s"[LSP] Received notification: $method")
        val params = json.obj.getOrElse("params", ujson.Obj())
        listener.handleNotification(method, params)
      }
    } catch {
      case e: Exception =>
        System.err.println(s"Failed to handle LSP message: ${e.getMessage}")
    }
  }

  private def sendMessage(content: String): Unit = {
    val message = s"Content-Length: ${content.length}\r\n\r\n$content"
    println(s"[LSP-SEND] Sending message (${content.length} bytes): ${content.take(200)}...")
    writer.synchronized {
      writer.print(message)
      writer.flush()
    }
    println(s"[LSP-SEND] Message sent and flushed")
  }

  private def sendRequest[T: ReadWriter](method: String, params: T): Int = {
    val id      = requestId.incrementAndGet()
    val request = ujson.Obj(
      "jsonrpc" -> "2.0",
      "id"      -> id,
      "method"  -> method,
      "params"  -> writeJs(params)
    )
    pendingRequests.put(id, new CountDownLatch(1))
    sendMessage(ujson.write(request))
    id
  }

  private def sendNotification[T: ReadWriter](method: String, params: T): Unit = {
    val notification = ujson.Obj(
      "jsonrpc" -> "2.0",
      "method"  -> method,
      "params"  -> writeJs(params)
    )
    sendMessage(ujson.write(notification))
  }

  private def waitForResponse(id: Int, timeoutSeconds: Int = 30): Option[ujson.Value] = {
    val latch = pendingRequests.get(id)
    if (latch != null && latch.await(timeoutSeconds, TimeUnit.SECONDS)) {
      pendingRequests.remove(id)
      Option(responses.remove(id))
    } else {
      pendingRequests.remove(id)
      responses.remove(id)
      None
    }
  }

  def initialize(rootPath: String): Option[InitializeResult] = {
    println(s"[LSP] Sending initialize request to server...")
    val params = InitializeParams(
      processId = Some(getpid()),
      rootPath = Some(fileSchema + rootPath),
      rootUri = Some(fileSchema + rootPath),
      capabilities = ClientCapabilities()
    )
    val id = sendRequest("initialize", params)
    println(s"[LSP] Waiting for initialize response (id=$id, timeout=60s)...")
    waitForResponse(id, 60) match {
      case Some(result) =>
        println(s"[LSP] Got initialize response, sending initialized notification...")
        sendNotification("initialized", ujson.Obj())
        initialized = true
        try {
          Some(read[InitializeResult](result))
        } catch {
          case e: Exception =>
            System.err.println(s"[LSP] Warning: Failed to parse InitializeResult: ${e.getMessage}")
            Some(InitializeResult())
        }
      case None =>
        System.err.println("[LSP] Failed to initialize LSP server - timeout waiting for response")
        None
    }
  }

  def close(): Unit = {
    try {
      running = false
      val id = sendRequest("shutdown", ujson.Obj())
      waitForResponse(id, 5)
      sendNotification("exit", ujson.Obj())
    } catch {
      case _: Exception =>
    }
  }

  private def handleCompletionResponse(result: ujson.Value, callback: ListBuffer[CompletionItem] => Unit): Unit = {
    try {
      val items = if (result.isNull) {
        ListBuffer.empty[CompletionItem]
      } else if (result.obj.contains("items")) {
        ListBuffer.from(read[CompletionList](result).items)
      } else {
        ListBuffer.from(read[List[CompletionItem]](result))
      }
      callback(items)
    } catch {
      case e: Exception =>
        System.err.println(s"Failed to parse completion response: ${e.getMessage}")
        callback(ListBuffer.empty[CompletionItem])
    }
  }

  def requestCompletionAsync(buffer: TextBuffer, callback: ListBuffer[CompletionItem] => Unit): Unit = {
    val params = CompletionParams(
      textDocument = TextDocumentIdentifier(fileSchema + buffer.getFilepath()),
      position = Position(buffer.cursorY, buffer.cursorX)
    )
    val id = sendRequest("textDocument/completion", params)
    completionCallbacks.put(id, callback)
  }

  def requestCompletion(buffer: TextBuffer): ListBuffer[CompletionItem] = {
    val params = CompletionParams(
      textDocument = TextDocumentIdentifier(fileSchema + buffer.getFilepath()),
      position = Position(buffer.cursorY, buffer.cursorX)
    )

    val id = sendRequest("textDocument/completion", params)
    waitForResponse(id) match {
      case Some(result) =>
        try {
          if (result.isNull) {
            ListBuffer.empty[CompletionItem]
          } else if (result.obj.contains("items")) {
            ListBuffer.from(read[CompletionList](result).items)
          } else {
            ListBuffer.from(read[List[CompletionItem]](result))
          }
        } catch {
          case e: Exception =>
            System.err.println(s"Failed to parse completion response: ${e.getMessage}")
            ListBuffer.empty[CompletionItem]
        }
      case None =>
        ListBuffer.empty[CompletionItem]
    }
  }

  def requestDefinitions(buffer: TextBuffer): ListBuffer[Location] = {
    val params = DefinitionParams(
      textDocument = TextDocumentIdentifier(fileSchema + buffer.getFilepath()),
      position = Position(buffer.cursorY, buffer.cursorX)
    )

    val id = sendRequest("textDocument/definition", params)
    waitForResponse(id) match {
      case Some(result) =>
        try {
          if (result.isNull) {
            ListBuffer.empty[Location]
          } else if (result.arr.nonEmpty && result.arr.head.obj.contains("targetUri")) {
            val links = read[List[LocationLink]](result)
            ListBuffer.from(links.map(l => Location(l.targetUri, l.targetRange)))
          } else {
            ListBuffer.from(read[List[Location]](result))
          }
        } catch {
          case e: Exception =>
            System.err.println(s"Failed to parse definition response: ${e.getMessage}")
            ListBuffer.empty[Location]
        }
      case None =>
        ListBuffer.empty[Location]
    }
  }

  def sendDidChange(buffer: TextBuffer): Unit = {
    val params = DidChangeTextDocumentParams(
      textDocument = VersionedTextDocumentIdentifier(
        uri = fileSchema + buffer.getFilepath(),
        version = { version += 1; version }
      ),
      contentChanges = List(
        TextDocumentContentChangeEvent(
          range = Some(Range(Position(0, 0), Position(buffer.lines.length, 0))),
          text = buffer.getSourceCode()
        )
      )
    )
    sendNotification("textDocument/didChange", params)
  }

  def sendDidOpen(buffer: TextBuffer): Unit = {
    val params = DidOpenTextDocumentParams(
      textDocument = TextDocumentItem(
        uri = fileSchema + buffer.getFilepath(),
        languageId = buffer.getFileType(),
        version = 1,
        text = buffer.getSourceCode()
      )
    )
    sendNotification("textDocument/didOpen", params)
  }
}
