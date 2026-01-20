#include <tree_sitter/api.h>
#include <string.h>

// Wrapper functions that use pointers instead of by-value returns
// This works around Scala Native's issues with large struct by-value returns

void ts_tree_root_node_ptr(const TSTree *self, TSNode *out) {
    TSNode node = ts_tree_root_node(self);
    memcpy(out, &node, sizeof(TSNode));
}

void ts_node_child_ptr(TSNode *self, uint32_t index, TSNode *out) {
    TSNode child = ts_node_child(*self, index);
    memcpy(out, &child, sizeof(TSNode));
}

const char* ts_node_type_ptr(TSNode *self) {
    return ts_node_type(*self);
}

uint32_t ts_node_start_byte_ptr(TSNode *self) {
    return ts_node_start_byte(*self);
}

uint32_t ts_node_end_byte_ptr(TSNode *self) {
    return ts_node_end_byte(*self);
}

uint32_t ts_node_child_count_ptr(TSNode *self) {
    return ts_node_child_count(*self);
}

bool ts_node_is_null_ptr(TSNode *self) {
    return ts_node_is_null(*self);
}

TSQuery *ts_query_new_ptr(const TSLanguage *language, const char *source, uint32_t length, uint32_t *error_offset,
                          TSQueryError *error_type) {
    return ts_query_new(language, source, length, error_offset, error_type);
}

void ts_query_delete_ptr(TSQuery *query) {
    ts_query_delete(query);
}

TSQueryCursor *ts_query_cursor_new_ptr(void) {
    return ts_query_cursor_new();
}

void ts_query_cursor_delete_ptr(TSQueryCursor *cursor) {
    ts_query_cursor_delete(cursor);
}

void ts_query_cursor_exec_ptr(TSQueryCursor *cursor, const TSQuery *query, TSNode *root) {
    ts_query_cursor_exec(cursor, query, *root);
}

bool ts_query_cursor_next_capture_bytes_ptr(TSQueryCursor *cursor, const TSQuery *query, uint32_t *start_byte,
                                            uint32_t *end_byte, const char **capture_name,
                                            uint32_t *capture_name_length) {
    TSQueryMatch match;
    uint32_t capture_index;
    if (!ts_query_cursor_next_capture(cursor, &match, &capture_index)) {
        return false;
    }

    TSQueryCapture capture = match.captures[capture_index];
    *start_byte = ts_node_start_byte(capture.node);
    *end_byte = ts_node_end_byte(capture.node);
    *capture_name = ts_query_capture_name_for_id(query, capture.index, capture_name_length);
    return true;
}
