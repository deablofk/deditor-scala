#!/bin/bash
# Build the tree-sitter wrapper library

set -e

# Create target directory if it doesn't exist
mkdir -p target

echo "Compiling tree-sitter wrapper..."
gcc -c -fPIC -O2 \
    -I/usr/include \
    -I/usr/local/include \
    src/main/c/treesitter_wrapper.c \
    -o target/treesitter_wrapper.o

echo "Creating shared library..."
gcc -shared -o target/libtreesitter_wrapper.so target/treesitter_wrapper.o -ltree-sitter

echo "Wrapper library built: target/libtreesitter_wrapper.so"
echo "Make sure to link it with your application"

echo "Compiling terminal wrapper..."
gcc -c -fPIC -O2 \
    -I/usr/include \
    -I/usr/local/include \
    src/main/c/terminal_wrapper.c \
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
    src/main/c/freetype_harfbuzz_wrapper.c \
    -o target/freetype_harfbuzz_wrapper.o

echo "Creating FreeType+HarfBuzz shared library..."
gcc -shared -o target/libfreetype_harfbuzz_wrapper.so target/freetype_harfbuzz_wrapper.o -lfreetype -lharfbuzz

echo "FreeType+HarfBuzz wrapper library built: target/libfreetype_harfbuzz_wrapper.so"
