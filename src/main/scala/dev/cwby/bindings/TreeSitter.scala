package dev.cwby.bindings
import scala.scalanative.unsafe._
import scala.scalanative.unsigned._

@link("tree-sitter")
@link("treesitter_wrapper")
@extern
object TreeSitterLib {
  type TSParser      = Ptr[Byte]
  type TSTree        = Ptr[Byte]
  type TSLanguage    = Ptr[Byte]
  type TSNode        = CStruct6[UInt, UInt, UInt, UInt, Ptr[Byte], Ptr[Byte]]
  type TSQuery       = Ptr[Byte]
  type TSQueryCursor = Ptr[Byte]
  type TSQueryError  = CInt

  def ts_parser_new(): TSParser                                                                        = extern
  def ts_parser_delete(parser: TSParser): Unit                                                         = extern
  def ts_parser_set_language(parser: TSParser, language: TSLanguage): Boolean                          = extern
  def ts_parser_parse_string(parser: TSParser, oldTree: TSTree, string: CString, length: UInt): TSTree = extern

  def ts_tree_delete(tree: TSTree): Unit = extern

  // Use wrapper functions that take pointers to work around Scala Native struct-by-value issues
  def ts_tree_root_node_ptr(tree: TSTree, out: Ptr[TSNode]): Unit               = extern
  def ts_node_type_ptr(node: Ptr[TSNode]): CString                              = extern
  def ts_node_start_byte_ptr(node: Ptr[TSNode]): UInt                           = extern
  def ts_node_end_byte_ptr(node: Ptr[TSNode]): UInt                             = extern
  def ts_node_child_count_ptr(node: Ptr[TSNode]): UInt                          = extern
  def ts_node_child_ptr(node: Ptr[TSNode], index: UInt, out: Ptr[TSNode]): Unit = extern
  def ts_node_is_null_ptr(node: Ptr[TSNode]): Boolean                           = extern

  def ts_query_new_ptr(
      language: TSLanguage,
      source: CString,
      length: UInt,
      error_offset: Ptr[UInt],
      error_type: Ptr[TSQueryError]
  ): TSQuery                                    = extern
  def ts_query_delete_ptr(query: TSQuery): Unit = extern

  def ts_query_cursor_new_ptr(): TSQueryCursor                                                 = extern
  def ts_query_cursor_delete_ptr(cursor: TSQueryCursor): Unit                                  = extern
  def ts_query_cursor_exec_ptr(cursor: TSQueryCursor, query: TSQuery, root: Ptr[TSNode]): Unit = extern
  def ts_query_cursor_next_capture_bytes_ptr(
      cursor: TSQueryCursor,
      query: TSQuery,
      start_byte: Ptr[UInt],
      end_byte: Ptr[UInt],
      capture_name: Ptr[CString],
      capture_name_length: Ptr[UInt]
  ): Boolean = extern
}

object TreeSitter {
  import TreeSitterLib.*

  def loadLanguage(libraryPath: String, symbolName: String): TSLanguage = {
    Zone {
      val handle = dlfcn.dlopen(toCString(libraryPath), dlfcn.RTLD_NOW)
      if (handle == null) {
        throw new RuntimeException(s"Failed to load library: $libraryPath")
      }
      val symbol = dlfcn.dlsym(handle, toCString(symbolName))
      if (symbol == null) {
        throw new RuntimeException(s"Failed to find symbol: $symbolName")
      }
      val languageFunc = CFuncPtr.fromPtr[CFuncPtr0[TSLanguage]](symbol)
      languageFunc()
    }
  }
}

@link("dl")
@extern
object dlfcn {
  def dlopen(filename: CString, flag: Int): Ptr[Byte]      = extern
  def dlsym(handle: Ptr[Byte], symbol: CString): Ptr[Byte] = extern
  def dlclose(handle: Ptr[Byte]): Int                      = extern

  final val RTLD_NOW = 2
}
