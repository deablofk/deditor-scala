#!/bin/bash
# Build native wrapper shared libraries for the editor

set -e

mkdir -p target

echo "Compiling tree-sitter wrapper..."
gcc -c -fPIC -O2 \
    -I/usr/include \
    -I/usr/local/include \
    editor/src/main/c/treesitter_wrapper.c \
    -o target/treesitter_wrapper.o

echo "Creating shared library..."
gcc -shared -o target/libtreesitter_wrapper.so target/treesitter_wrapper.o -ltree-sitter

echo "Wrapper library built: target/libtreesitter_wrapper.so"

echo "Compiling terminal wrapper..."
gcc -c -fPIC -O2 \
    -I/usr/include \
    -I/usr/local/include \
    editor/src/main/c/terminal_wrapper.c \
    -o target/terminal_wrapper.o

echo "Creating terminal shared library..."
gcc -shared -o target/libterminal_wrapper.so target/terminal_wrapper.o -lvterm

echo "Terminal wrapper library built: target/libterminal_wrapper.so"

echo "Compiling FreeType+HarfBuzz wrapper..."
gcc -c -fPIC -O2 \
    -I/usr/include/freetype2 \
    -I/usr/include/harfbuzz \
    -I/usr/include \
    -I/usr/local/include \
    guitk/src/main/c/freetype_harfbuzz_wrapper.c \
    -o target/freetype_harfbuzz_wrapper.o

echo "Creating FreeType+HarfBuzz shared library..."
gcc -shared -o target/libfreetype_harfbuzz_wrapper.so target/freetype_harfbuzz_wrapper.o -lfreetype -lharfbuzz

echo "FreeType+HarfBuzz wrapper library built: target/libfreetype_harfbuzz_wrapper.so"

echo "Generating Wayland protocol headers..."
wayland-scanner server-header /usr/share/wayland-protocols/stable/xdg-shell/xdg-shell.xml target/xdg-shell-protocol.h

echo "Compiling dway wlroots bridge..."
gcc -c -fPIC -O2 \
    $(pkg-config --cflags wlroots-0.18 wayland-server xkbcommon pixman-1) \
    -DWLR_USE_UNSTABLE \
    -I/usr/include \
    -I/usr/local/include \
    -Itarget \
    dway/src/main/c/dway_wlroots_bridge.c \
    -o target/dway_wlroots_bridge.o

echo "Creating dway wlroots bridge shared library..."
gcc -shared -o target/libdway_wlroots_bridge.so target/dway_wlroots_bridge.o \
    $(pkg-config --libs wlroots-0.18 wayland-server xkbcommon pixman-1)

echo "dway wlroots bridge library built: target/libdway_wlroots_bridge.so"
