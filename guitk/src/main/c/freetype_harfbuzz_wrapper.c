#include <ft2build.h>
#include FT_FREETYPE_H
#include FT_BITMAP_H
#include <hb.h>
#include <hb-ft.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>

typedef struct {
    FT_Library library;
    FT_Face face;
    hb_font_t* hb_font;
    hb_buffer_t* hb_buffer;
    float pixel_size;
} FTHBFont;

typedef struct {
    float advance;
    float width;
    float height;
    float xoffset;
    float yoffset;
    float bearing_x;
    float bearing_y;
    uint32_t glyph_index;
} GlyphMetrics;

typedef struct {
    unsigned char* bitmap;
    int width;
    int height;
    int pitch;
    int is_color;
} GlyphBitmap;

FTHBFont* fthb_create_font(const char* font_path, float pixel_size) {
    FTHBFont* font = (FTHBFont*)malloc(sizeof(FTHBFont));
    if (!font) return NULL;
    
    font->pixel_size = pixel_size;
    font->hb_buffer = NULL;
    font->hb_font = NULL;
    
    if (FT_Init_FreeType(&font->library)) {
        free(font);
        return NULL;
    }
    
    if (FT_New_Face(font->library, font_path, 0, &font->face)) {
        FT_Done_FreeType(font->library);
        free(font);
        return NULL;
    }
    
    // For bitmap fonts (like color emoji), select the best strike
    if (FT_HAS_FIXED_SIZES(font->face)) {
        // Select the strike closest to requested size
        int best_strike = 0;
        int best_diff = abs(font->face->available_sizes[0].height - (int)pixel_size);
        for (int i = 1; i < font->face->num_fixed_sizes; i++) {
            int diff = abs(font->face->available_sizes[i].height - (int)pixel_size);
            if (diff < best_diff) {
                best_diff = diff;
                best_strike = i;
            }
        }
        FT_Select_Size(font->face, best_strike);
    } else {
        FT_Set_Pixel_Sizes(font->face, 0, (FT_UInt)pixel_size);
    }
    
    font->hb_font = hb_ft_font_create(font->face, NULL);
    if (!font->hb_font) {
        FT_Done_Face(font->face);
        FT_Done_FreeType(font->library);
        free(font);
        return NULL;
    }
    
    font->hb_buffer = hb_buffer_create();
    if (!font->hb_buffer) {
        hb_font_destroy(font->hb_font);
        FT_Done_Face(font->face);
        FT_Done_FreeType(font->library);
        free(font);
        return NULL;
    }
    
    return font;
}

void fthb_destroy_font(FTHBFont* font) {
    if (!font) return;
    
    if (font->hb_buffer) hb_buffer_destroy(font->hb_buffer);
    if (font->hb_font) hb_font_destroy(font->hb_font);
    if (font->face) FT_Done_Face(font->face);
    if (font->library) FT_Done_FreeType(font->library);
    free(font);
}

int fthb_has_glyph(FTHBFont* font, uint32_t codepoint) {
    if (!font || !font->face) return 0;
    return FT_Get_Char_Index(font->face, codepoint) != 0;
}

int fthb_get_glyph_metrics(FTHBFont* font, uint32_t codepoint, GlyphMetrics* metrics) {
    if (!font || !font->face || !metrics) return 0;
    
    FT_UInt glyph_index = FT_Get_Char_Index(font->face, codepoint);
    if (glyph_index == 0) return 0;
    
    if (FT_Load_Glyph(font->face, glyph_index, FT_LOAD_DEFAULT)) {
        return 0;
    }
    
    FT_GlyphSlot slot = font->face->glyph;
    
    metrics->glyph_index = glyph_index;
    metrics->advance = slot->advance.x / 64.0f;
    metrics->bearing_x = slot->metrics.horiBearingX / 64.0f;
    metrics->bearing_y = slot->metrics.horiBearingY / 64.0f;
    metrics->width = slot->metrics.width / 64.0f;
    metrics->height = slot->metrics.height / 64.0f;
    
    if (FT_Load_Glyph(font->face, glyph_index, FT_LOAD_RENDER)) {
        return 0;
    }
    
    metrics->xoffset = slot->bitmap_left;
    metrics->yoffset = -slot->bitmap_top;
    
    return 1;
}

int fthb_render_glyph(FTHBFont* font, uint32_t codepoint, GlyphBitmap* out_bitmap, GlyphMetrics* out_metrics) {
    if (!font || !font->face || !out_bitmap) return 0;
    
    FT_UInt glyph_index = FT_Get_Char_Index(font->face, codepoint);
    if (glyph_index == 0) return 0;
    
    // Try loading with color first (for embedded bitmaps)
    FT_Int32 load_flags = FT_LOAD_COLOR;
    if (FT_Load_Glyph(font->face, glyph_index, load_flags)) {
        return 0;
    }
    
    FT_GlyphSlot slot = font->face->glyph;
    
    // If not a bitmap, render it
    if (slot->format != FT_GLYPH_FORMAT_BITMAP) {
        if (FT_Render_Glyph(slot, FT_RENDER_MODE_NORMAL)) {
            return 0;
        }
    }
    
    FT_Bitmap* bitmap = &slot->bitmap;
    
    if (bitmap->width == 0 || bitmap->rows == 0) {
        out_bitmap->bitmap = NULL;
        out_bitmap->width = 0;
        out_bitmap->height = 0;
        out_bitmap->pitch = 0;
        out_bitmap->is_color = 0;
        
        if (out_metrics) {
            out_metrics->glyph_index = glyph_index;
            out_metrics->advance = slot->advance.x / 64.0f;
            out_metrics->bearing_x = slot->metrics.horiBearingX / 64.0f;
            out_metrics->bearing_y = slot->metrics.horiBearingY / 64.0f;
            out_metrics->width = 0;
            out_metrics->height = 0;
            out_metrics->xoffset = slot->bitmap_left;
            out_metrics->yoffset = -slot->bitmap_top;
        }
        return 1;
    }
    
    int is_color = (bitmap->pixel_mode == FT_PIXEL_MODE_BGRA);
    size_t bytes_per_pixel = is_color ? 4 : 1;
    size_t buffer_size = bitmap->width * bitmap->rows * bytes_per_pixel;
    unsigned char* buffer = (unsigned char*)malloc(buffer_size);
    if (!buffer) return 0;
    
    if (bitmap->pixel_mode == FT_PIXEL_MODE_BGRA) {
        for (unsigned int y = 0; y < bitmap->rows; y++) {
            unsigned char* src = bitmap->buffer + y * bitmap->pitch;
            unsigned char* dst = buffer + y * bitmap->width * 4;
            for (unsigned int x = 0; x < bitmap->width; x++) {
                unsigned char b = src[x * 4 + 0];
                unsigned char g = src[x * 4 + 1];
                unsigned char r = src[x * 4 + 2];
                unsigned char a = src[x * 4 + 3];
                dst[x * 4 + 0] = r;
                dst[x * 4 + 1] = g;
                dst[x * 4 + 2] = b;
                dst[x * 4 + 3] = a;
            }
        }
    } else if (bitmap->pixel_mode == FT_PIXEL_MODE_GRAY) {
        for (unsigned int y = 0; y < bitmap->rows; y++) {
            memcpy(buffer + y * bitmap->width,
                   bitmap->buffer + y * bitmap->pitch,
                   bitmap->width);
        }
    } else if (bitmap->pixel_mode == FT_PIXEL_MODE_MONO) {
        for (unsigned int y = 0; y < bitmap->rows; y++) {
            for (unsigned int x = 0; x < bitmap->width; x++) {
                unsigned char byte = bitmap->buffer[y * bitmap->pitch + (x >> 3)];
                unsigned char bit = (byte >> (7 - (x & 7))) & 1;
                buffer[y * bitmap->width + x] = bit ? 255 : 0;
            }
        }
    } else {
        free(buffer);
        return 0;
    }
    
    out_bitmap->bitmap = buffer;
    out_bitmap->width = bitmap->width;
    out_bitmap->height = bitmap->rows;
    out_bitmap->pitch = bitmap->width * bytes_per_pixel;
    out_bitmap->is_color = is_color;
    
    if (out_metrics) {
        out_metrics->glyph_index = glyph_index;
        out_metrics->advance = slot->advance.x / 64.0f;
        out_metrics->bearing_x = slot->metrics.horiBearingX / 64.0f;
        out_metrics->bearing_y = slot->metrics.horiBearingY / 64.0f;
        out_metrics->width = bitmap->width;
        out_metrics->height = bitmap->rows;
        out_metrics->xoffset = slot->bitmap_left;
        out_metrics->yoffset = -slot->bitmap_top;
    }
    
    return 1;
}

void fthb_free_glyph_bitmap(GlyphBitmap* bitmap) {
    if (bitmap && bitmap->bitmap) {
        free(bitmap->bitmap);
        bitmap->bitmap = NULL;
    }
}

void fthb_get_font_metrics(FTHBFont* font, float* ascent, float* descent, float* line_gap) {
    if (!font || !font->face) return;
    
    FT_Size_Metrics metrics = font->face->size->metrics;
    
    if (ascent) *ascent = metrics.ascender / 64.0f;
    if (descent) *descent = metrics.descender / 64.0f;
    if (line_gap) *line_gap = (metrics.height - metrics.ascender + metrics.descender) / 64.0f;
}

typedef struct {
    uint32_t codepoint;
    uint32_t cluster;
    float x_advance;
    float y_advance;
    float x_offset;
    float y_offset;
} ShapedGlyph;

int fthb_shape_text(FTHBFont* font, const char* text, int text_length, const char* language, ShapedGlyph* out_glyphs, int max_glyphs) {
    if (!font || !font->hb_buffer || !text || !out_glyphs) return 0;
    
    hb_buffer_clear_contents(font->hb_buffer);
    hb_buffer_set_direction(font->hb_buffer, HB_DIRECTION_LTR);
    hb_buffer_set_script(font->hb_buffer, HB_SCRIPT_COMMON);
    
    if (language && language[0]) {
        hb_buffer_set_language(font->hb_buffer, hb_language_from_string(language, -1));
    }
    
    hb_buffer_add_utf8(font->hb_buffer, text, text_length, 0, text_length);
    
    hb_shape(font->hb_font, font->hb_buffer, NULL, 0);
    
    unsigned int glyph_count;
    hb_glyph_info_t* glyph_info = hb_buffer_get_glyph_infos(font->hb_buffer, &glyph_count);
    hb_glyph_position_t* glyph_pos = hb_buffer_get_glyph_positions(font->hb_buffer, &glyph_count);
    
    if (glyph_count > (unsigned int)max_glyphs) {
        glyph_count = max_glyphs;
    }
    
    for (unsigned int i = 0; i < glyph_count; i++) {
        out_glyphs[i].codepoint = glyph_info[i].codepoint;
        out_glyphs[i].cluster = glyph_info[i].cluster;
        out_glyphs[i].x_advance = glyph_pos[i].x_advance / 64.0f;
        out_glyphs[i].y_advance = glyph_pos[i].y_advance / 64.0f;
        out_glyphs[i].x_offset = glyph_pos[i].x_offset / 64.0f;
        out_glyphs[i].y_offset = glyph_pos[i].y_offset / 64.0f;
    }
    
    return glyph_count;
}

int fthb_render_glyph_by_index(FTHBFont* font, uint32_t glyph_index, GlyphBitmap* out_bitmap, GlyphMetrics* out_metrics) {
    if (!font || !font->face || !out_bitmap) return 0;
    
    // Try loading with color first (for embedded bitmaps)
    FT_Int32 load_flags = FT_LOAD_COLOR;
    if (FT_Load_Glyph(font->face, glyph_index, load_flags)) {
        return 0;
    }
    
    // If not a bitmap, render it
    if (font->face->glyph->format != FT_GLYPH_FORMAT_BITMAP) {
        if (FT_Render_Glyph(font->face->glyph, FT_RENDER_MODE_NORMAL)) {
            return 0;
        }
    }
    
    FT_GlyphSlot slot = font->face->glyph;
    FT_Bitmap* bitmap = &slot->bitmap;
    
    if (bitmap->width == 0 || bitmap->rows == 0) {
        out_bitmap->bitmap = NULL;
        out_bitmap->width = 0;
        out_bitmap->height = 0;
        out_bitmap->pitch = 0;
        out_bitmap->is_color = 0;
        
        if (out_metrics) {
            out_metrics->glyph_index = glyph_index;
            out_metrics->advance = slot->advance.x / 64.0f;
            out_metrics->bearing_x = slot->metrics.horiBearingX / 64.0f;
            out_metrics->bearing_y = slot->metrics.horiBearingY / 64.0f;
            out_metrics->width = 0;
            out_metrics->height = 0;
            out_metrics->xoffset = slot->bitmap_left;
            out_metrics->yoffset = -slot->bitmap_top;
        }
        return 1;
    }
    
    int is_color = (bitmap->pixel_mode == FT_PIXEL_MODE_BGRA);
    size_t bytes_per_pixel = is_color ? 4 : 1;
    size_t buffer_size = bitmap->width * bitmap->rows * bytes_per_pixel;
    unsigned char* buffer = (unsigned char*)malloc(buffer_size);
    if (!buffer) return 0;
    
    if (bitmap->pixel_mode == FT_PIXEL_MODE_BGRA) {
        for (unsigned int y = 0; y < bitmap->rows; y++) {
            unsigned char* src = bitmap->buffer + y * bitmap->pitch;
            unsigned char* dst = buffer + y * bitmap->width * 4;
            for (unsigned int x = 0; x < bitmap->width; x++) {
                unsigned char b = src[x * 4 + 0];
                unsigned char g = src[x * 4 + 1];
                unsigned char r = src[x * 4 + 2];
                unsigned char a = src[x * 4 + 3];
                dst[x * 4 + 0] = r;
                dst[x * 4 + 1] = g;
                dst[x * 4 + 2] = b;
                dst[x * 4 + 3] = a;
            }
        }
    } else if (bitmap->pixel_mode == FT_PIXEL_MODE_GRAY) {
        for (unsigned int y = 0; y < bitmap->rows; y++) {
            memcpy(buffer + y * bitmap->width,
                   bitmap->buffer + y * bitmap->pitch,
                   bitmap->width);
        }
    } else if (bitmap->pixel_mode == FT_PIXEL_MODE_MONO) {
        for (unsigned int y = 0; y < bitmap->rows; y++) {
            for (unsigned int x = 0; x < bitmap->width; x++) {
                unsigned char byte = bitmap->buffer[y * bitmap->pitch + (x >> 3)];
                unsigned char bit = (byte >> (7 - (x & 7))) & 1;
                buffer[y * bitmap->width + x] = bit ? 255 : 0;
            }
        }
    } else {
        free(buffer);
        return 0;
    }
    
    out_bitmap->bitmap = buffer;
    out_bitmap->width = bitmap->width;
    out_bitmap->height = bitmap->rows;
    out_bitmap->pitch = bitmap->width * bytes_per_pixel;
    out_bitmap->is_color = is_color;
    
    if (out_metrics) {
        out_metrics->glyph_index = glyph_index;
        out_metrics->advance = slot->advance.x / 64.0f;
        out_metrics->bearing_x = slot->metrics.horiBearingX / 64.0f;
        out_metrics->bearing_y = slot->metrics.horiBearingY / 64.0f;
        out_metrics->width = bitmap->width;
        out_metrics->height = bitmap->rows;
        out_metrics->xoffset = slot->bitmap_left;
        out_metrics->yoffset = -slot->bitmap_top;
    }
    
    return 1;
}
