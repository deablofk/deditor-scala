# deditor-root

![Scala](https://img.shields.io/badge/Scala-3.7.4-DC322F?style=flat&logo=scala&logoColor=white)
![Scala Native](https://img.shields.io/badge/Scala_Native-0.5.9-DC322F?style=flat&logo=scala&logoColor=white)
![SDL3](https://img.shields.io/badge/SDL3-latest-blue?style=flat)
![OpenGL](https://img.shields.io/badge/OpenGL-4.1-5586A4?style=flat&logo=opengl&logoColor=white)
![FreeType](https://img.shields.io/badge/FreeType-2-blue?style=flat)
![HarfBuzz](https://img.shields.io/badge/HarfBuzz-latest-orange?style=flat)
![Tree-sitter](https://img.shields.io/badge/Tree--sitter-latest-green?style=flat)
![libvterm](https://img.shields.io/badge/libvterm-latest-yellow?style=flat)
![sbt](https://img.shields.io/badge/sbt-1.11.7-blue?style=flat&logo=sbt&logoColor=white)

A monorepo containing three Scala Native projects that share a common GUI toolkit. The projects are developed in parallel here for convenience and will be split into separate repositories once they reach maturity.

---

## Repository Structure

```
deditor-root/
  build.sbt              Multi-project build definition
  build_wrapper.sh        Native C wrapper build script
  guitk/                  Standalone GUI toolkit library
  editor/                 Modal text editor (Deditor)
  dway/                   Wayland compositor (early stage)
  config/                 Editor runtime configuration (TOML)
  project/                sbt build metadata
```

### Dependency Graph

```
guitk (standalone)
  ^         ^
  |         |
editor    dway
```

Both `editor` and `dway` depend on `guitk`. They do not depend on each other.

---

## Projects

### guitk

A standalone, reusable GUI toolkit built on SDL3 and OpenGL. It provides window management, 2D rendering, text shaping, and an event system. It has zero knowledge of any application built on top of it.

**Package:** `dev.cwby.guitk`

| Module | Description |
|---|---|
| `platform/Engine` | SDL3 window lifecycle, OpenGL context creation, event loop, and callback-driven architecture. Creates a borderless resizable window with an OpenGL 3.3+ context. |
| `renderer/Renderer2D` | Batched 2D renderer with rectangle and text drawing. Manages shaders, vertex buffers, clipping stack, and projection. Supports grayscale and color (emoji) text modes. |
| `renderer/Shader` | OpenGL shader program wrapper handling compilation, linking, validation, and uniform binding. |
| `renderer/Texture` | OpenGL texture wrapper supporting RGBA and single-channel formats with sub-region updates. |
| `text/Font` | FreeType + HarfBuzz font wrapper via a C bridge (`freetype_harfbuzz_wrapper.c`). Handles glyph metrics, bitmap rendering, text shaping, and system fallback font discovery. |
| `text/TextShaper` | Combines `Font` and `GlyphCache` to shape text runs and measure widths, with fallback font support. |
| `text/FontManager` | Singleton managing the active font, line height, average character width, and font size changes. Requires explicit initialization after the OpenGL context is available. |
| `text/GlyphCache` | Texture atlas packer with LRU eviction. Handles glyph caching, color emoji detection, and batched texture uploads. |
| `components/Window` | Base window primitive managing visibility, child components, and open/close event dispatching. |
| `components/TiledWindow` | Extends `Window` with horizontal/vertical splitting, neighbor traversal, and layout recalculation. |
| `components/FloatingWindow` | Extends `Window` for overlay windows with size factor and show/close callbacks. |
| `components/IComponent` | Trait defining a renderable component (`render(Renderer2D, ...)`). |
| `components/WindowCallbacks` | Trait for window lifecycle callbacks (closed, focused, root closed). |
| `events/EventDispatcher` | Typed publish-subscribe event system with a global singleton dispatcher. |
| `events/WindowEvent` | Sealed trait hierarchy of window events (open, close, resize, focus, move, visibility). |
| `input/IKeyHandler` | Trait for handling SDL key events and text input. |
| `registry/ComponentRegistry` | Manages registration and lookup of `IComponent` instances by name and ID. |
| `registry/WindowRegistry` | Manages registration and lookup of `Window` instances by name and ID, with tiled/floating filtering. |
| `bindings/opengl/OpenGL` | OpenGL constants and `@extern` bindings. |
| `bindings/sdl/SimpleDirectMediaLayer` | SDL3 constants and `@extern` bindings (init, video, GL, events, clipboard, keyboard). |

**Native dependencies:**

| Library | Purpose |
|---|---|
| SDL3 | Window management, input, clipboard |
| OpenGL | GPU-accelerated 2D rendering |
| FreeType | Font loading and glyph rasterization |
| HarfBuzz | Text shaping and complex script layout |

---

### editor (Deditor)

A native modal text editor with Vim-style keybindings, LSP support, Tree-sitter syntax highlighting, and an integrated terminal emulator. Built entirely on `guitk`.

**Package:** `dev.cwby`

**Entry point:** `dev.cwby.Deditor.main`

#### Core

| Module | Description |
|---|---|
| `editor/core/TextBuffer` | Text buffer backed by an array of `StringBuilder` lines. Manages cursor state, file metadata, and chunk-based file loading via `FileChunkLoader`. |
| `editor/core/BufferState` | Immutable state record holding lines, cursor position, undo/redo stacks (up to 1000 entries), search state, and file info. |
| `editor/core/BufferOperations` | Text manipulation operations (insert, delete, yank, paste, indent, join lines). |
| `editor/core/BufferCursor` | Cursor movement logic (character, word, line, page, document boundaries). |
| `editor/core/FileChunkLoader` | Loads files incrementally by byte size or line offset for handling large files. |
| `editor/core/TextInteractionMode` | Enum defining editor modes: `INSERT`, `NAVIGATION`, `SELECT`, `SELECT_LINE`, `SELECT_BLOCK`, `COMMAND`, `SEARCH`. |

#### Input

| Module | Description |
|---|---|
| `editor/input/KeyHandler` | Abstract class extending `guitk.input.IKeyHandler`. Converts SDL key events to string representations and dispatches through a `KeybindingTrie`. |
| `editor/input/GlobalKeyHandler` | Concrete key handler registering global keybindings and routing input based on the active mode. |
| `editor/input/VimKeybindingRegistry` | Registers the full set of Vim-style keybindings across all modes (navigation, insert, select, command, search). |
| `editor/input/VimTextOperations` | Implements Vim text operations (delete word, change line, yank, paste, visual selections). |
| `editor/input/InputHandler` | Handles mouse events for cursor positioning using font metrics. |
| `editor/input/KeybindingTrie` | Trie data structure for multi-key sequence matching. |

#### Rendering

| Module | Description |
|---|---|
| `editor/renderer/EditorRenderer` | Top-level render orchestrator. Draws tiled windows with borders, the status line (mode, file path, cursor position), floating windows, and the autocomplete popup. |
| `editor/components/TextComponent` | Renders a `TextBuffer` with line numbers, syntax highlighting, cursor, selection highlighting, and search match highlighting. |
| `editor/components/AutoCompleteWindow` | Floating window displaying LSP completion suggestions with keyboard navigation. |
| `editor/components/TelescopeWindow` | Fuzzy file finder and grep interface with live preview pane. Supports file and grep modes. |
| `editor/components/DiredWindow` | Directory browser (Emacs dired-style) for file navigation within the project. |
| `editor/components/PkgManWindow` | Package manager UI for browsing and installing editor packages (LSP servers, grammars). |
| `editor/window/EditorWindowExtensions` | Extension methods on `guitk.Window` for keeping the cursor visible (vertical and horizontal scrolling). |

#### Commands

Registered in `CommandHandler` and accessible via command mode (`:` prefix):

| Command | Aliases | Description |
|---|---|---|
| `quit` | `q` | Close the editor |
| `edit` | `e` | Open a file |
| `save` | `w` | Save the current buffer |
| `vs` | | Vertical split |
| `s` | | Horizontal split |
| `terminal` | `term` | Open an integrated terminal |
| `telescope-files` | | Fuzzy file finder |
| `telescope-grep` | | Project-wide grep |
| `dired` | | Directory browser |
| `pkgman` | | Package manager |

#### LSP

| Module | Description |
|---|---|
| `lsp/LSPClient` | JSON-RPC client communicating with LSP servers over stdin/stdout. Handles initialization, text synchronization, completion, and hover. |
| `lsp/LSPManager` | Manages LSP server processes and client connections. Auto-attaches LSP clients to buffers based on file type. Integrates with the package manager for LSP server installation. |
| `lsp/LSPClientListener` | Handles LSP server notifications (diagnostics, telemetry). |
| `lsp/LSPStubs` | Data types for LSP protocol messages (CompletionItem, Position, Range, etc.) with uPickle serialization. |

#### Terminal

| Module | Description |
|---|---|
| `terminal/TerminalSession` | PTY-based terminal emulator using libvterm. Spawns a child process, reads/writes to the master FD, and renders terminal state via VTerm screen callbacks. |
| `terminal/TerminalComponent` | Renders the terminal screen as a `guitk.IComponent`, mapping VTerm cells to colored text. |
| `terminal/TerminalWindow` | Floating window wrapping a `TerminalSession` with keyboard input forwarding. |

#### Syntax Highlighting

| Module | Description |
|---|---|
| `treesitter/SyntaxHighlighter` | Parses source code with Tree-sitter, runs highlight queries, and produces `HighlightSpan` arrays mapping byte ranges to colors from the editor theme. |
| `treesitter/TreeSitterGrammarManager` | Manages Tree-sitter grammar shared libraries. Handles grammar discovery, dynamic loading via `dlopen`, and preinstallation of configured grammars. |
| `treesitter/HighlightSpan` | Data class representing a colored region of text (start byte, end byte, color). |

#### Configuration

Configuration is read from TOML files at `~/.config/deditor/config.toml`, `~/.deditor/config.toml`, or `config/config.toml`.

| Section | Fields |
|---|---|
| `cursor` | `blink` (ms), `color`, `select` (selection color) |
| `font` | `family` (default: Iosevka Nerd Font Mono), `size` (default: 32) |
| `theme` | `background`, `numberColor` |
| `treesitter` | Color map for syntax node types |
| `treesitterParsers` | `mode` (on_demand), grammar definitions with repo URLs and file type associations |

A custom TOML parser (`dev.cwby.toml.Toml`) is included since no Scala Native-compatible TOML library is available.

#### Other

| Module | Description |
|---|---|
| `clipboard/ClipboardManager` | Dual clipboard system (internal + system via SDL3). |
| `pkgs/PackageManager` | Package registry and installer supporting generic (download + extract) and command-based sources. Used for installing LSP servers and Tree-sitter grammars. |
| `WindowManager` | Manages tiled and floating window state, focus tracking, and window lifecycle callbacks. |
| `BufferManager` | Manages `TextBuffer` instances and coordinates with LSP on file open. |
| `Environment` | Utility functions for path expansion (`~`), project root discovery, and file extension extraction. |

**Native dependencies (in addition to guitk):**

| Library | Purpose |
|---|---|
| Tree-sitter | Incremental parsing for syntax highlighting |
| libvterm | Virtual terminal emulation |
| upickle | JSON serialization for LSP protocol |

---

### dway

A Wayland compositor project in its early stages. Currently a minimal scaffold that initializes the `guitk` engine.

**Package:** `dev.cwby.dway`

**Entry point:** `dev.cwby.dway.Dway.main`

**Status:** Skeleton -- bootstraps the `guitk` engine and prints a hello message. No compositor logic implemented yet.

---

## Prerequisites

- **JDK 21+**
- **sbt 1.11+**
- **LLVM/Clang** (required by Scala Native)
- **gcc** (for compiling C wrappers)
- **SDL3** development libraries
- **FreeType 2** development libraries
- **HarfBuzz** development libraries
- **Tree-sitter** development libraries (editor only)
- **libvterm** development libraries (editor only)

### System packages (Arch Linux)

```bash
sudo pacman -S sdl3 freetype2 harfbuzz tree-sitter libvterm clang
```

### System packages (Ubuntu/Debian)

```bash
sudo apt install libsdl3-dev libfreetype6-dev libharfbuzz-dev libtree-sitter-dev libvterm-dev clang
```

---

## Building

### 1. Build native wrappers

The C wrappers bridge Scala Native `@extern` bindings to FreeType/HarfBuzz, Tree-sitter, and libvterm. They must be built before linking.

```bash
bash build_wrapper.sh
```

This produces shared libraries in `target/`:
- `libtreesitter_wrapper.so`
- `libterminal_wrapper.so`
- `libfreetype_harfbuzz_wrapper.so`

### 2. Build and run

```bash
# Compile all projects
sbt compile

# Build and run the editor
sbt editor/nativeLink
./editor/target/scala-3.7.4/deditor

# Build and run dway
sbt dway/nativeLink
./dway/target/scala-3.7.4/dway

# Build a specific project only
sbt guitk/compile
sbt editor/compile
sbt dway/compile
```

---

## Monorepo Rationale

The three projects live in a single repository to simplify parallel development while `guitk` stabilizes. Once `guitk` and `dway` are mature enough, they will be moved to their own repositories and consumed as published dependencies. The multi-project sbt setup makes this migration straightforward -- each project already has its own source tree, dependencies, and build configuration.
