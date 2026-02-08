package dev.cwby.lsp

import upickle.default._

/** LSP Client Listener - handles notifications from the LSP server. Equivalent to lsp4j LanguageClient interface.
  */
class LSPClientListener {

  def telemetryEvent(data: ujson.Value): Unit = {
    println(s"TelemetryEvent: $data")
  }

  def publishDiagnostics(params: PublishDiagnosticsParams): Unit = {
    println(s"Diagnostics Received: ${params.getUri()}")
    for (diagnostic <- params.getDiagnostics()) {
      println(s" - ${diagnostic.getMessage()}")
    }
  }

  def showMessage(params: MessageParams): Unit = {
    println(s"Server Message: ${params.getMessage()}")
  }

  def showMessageRequest(params: ShowMessageRequestParams): Option[MessageActionItem] = {
    println(s"Message Request: ${params.getMessage()}")
    None
  }

  def logMessage(params: MessageParams): Unit = {
    println(s"Log: ${params.getMessage()}")
  }

  def handleNotification(method: String, params: ujson.Value): Unit = {
    method match {
      case "telemetry/event" =>
        telemetryEvent(params)
      case "textDocument/publishDiagnostics" =>
        publishDiagnostics(read[PublishDiagnosticsParams](params))
      case "window/showMessage" =>
        showMessage(read[MessageParams](params))
      case "window/logMessage" =>
        logMessage(read[MessageParams](params))
      case _ =>
        println(s"Unknown notification: $method")
    }
  }
}
