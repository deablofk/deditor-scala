import scala.scalanative.build.*
import scala.scalanative.sbtplugin.ScalaNativePlugin.autoImport.nativeConfig
import scala.sys.process.*

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.7.4"

ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

ThisBuild / scalacOptions ++= Seq(
  "-Wall",
  "-explain"
)

lazy val root = (project in file("."))
  .enablePlugins(ScalaNativePlugin)
  .settings(
    name := "untitled1",
    libraryDependencies += "com.lihaoyi" %%% "upickle" % "4.4.1",
    nativeConfig := {
      val targetDir = baseDirectory.value / "target"
      val termWrapperSrc = baseDirectory.value / "src" / "main" / "c" / "terminal_wrapper.c"
      val termWrapperObj = targetDir / "terminal_wrapper.o"
      val ftWrapperSrc = baseDirectory.value / "src" / "main" / "c" / "freetype_harfbuzz_wrapper.c"
      val ftWrapperObj = targetDir / "freetype_harfbuzz_wrapper.o"
      val tsWrapperSrc = baseDirectory.value / "src" / "main" / "c" / "treesitter_wrapper.c"
      val tsWrapperObj = targetDir / "treesitter_wrapper.o"

      if (!termWrapperObj.exists() || termWrapperObj.lastModified() < termWrapperSrc.lastModified()) {
        val compileCmd = Seq(
          "gcc",
          "-c",
          "-fPIC",
          "-g",
          "-O0",
          "-I/usr/include",
          "-I/usr/local/include",
          termWrapperSrc.getAbsolutePath,
          "-o",
          termWrapperObj.getAbsolutePath
        )
        val log = streams.value.log
        log.info(s"Compiling terminal wrapper object: ${termWrapperObj.getAbsolutePath}")
        val exit = Process(compileCmd, baseDirectory.value).!(log)
        if (exit != 0) {
          sys.error("Failed to compile terminal wrapper C code. Ensure gcc and libvterm headers are installed (vterm.h).")
        }
      }

      if (!ftWrapperObj.exists() || ftWrapperObj.lastModified() < ftWrapperSrc.lastModified()) {
        val compileCmd = Seq(
          "gcc",
          "-c",
          "-fPIC",
          "-g",
          "-O0",
          "-I/usr/include/freetype2",
          "-I/usr/include/harfbuzz",
          "-I/usr/include",
          "-I/usr/local/include",
          ftWrapperSrc.getAbsolutePath,
          "-o",
          ftWrapperObj.getAbsolutePath
        )
        val log = streams.value.log
        log.info(s"Compiling FreeType+HarfBuzz wrapper object: ${ftWrapperObj.getAbsolutePath}")
        val exit = Process(compileCmd, baseDirectory.value).!(log)
        if (exit != 0) {
          sys.error("Failed to compile FreeType+HarfBuzz wrapper C code. Ensure gcc, libfreetype6-dev and libharfbuzz-dev are installed.")
        }
      }

      if (!tsWrapperObj.exists() || tsWrapperObj.lastModified() < tsWrapperSrc.lastModified()) {
        val compileCmd = Seq(
          "gcc",
          "-c",
          "-fPIC",
          "-g",
          "-O0",
          "-I/usr/include",
          "-I/usr/local/include",
          tsWrapperSrc.getAbsolutePath,
          "-o",
          tsWrapperObj.getAbsolutePath
        )
        val log = streams.value.log
        log.info(s"Compiling Tree-sitter wrapper object: ${tsWrapperObj.getAbsolutePath}")
        val exit = Process(compileCmd, baseDirectory.value).!(log)
        if (exit != 0) {
          sys.error("Failed to compile Tree-sitter wrapper C code. Ensure gcc and libtree-sitter headers are installed (tree_sitter/api.h).")
        }
      }

      nativeConfig.value
        .withLinkingOptions(nativeConfig.value.linkingOptions ++ Seq(
          "-L/usr/local/lib",
          s"-L$targetDir",
          s"-Wl,-rpath,$targetDir",
          "-lGL",
          "-lSDL3",
          "-ltree-sitter",
          "-ltreesitter_wrapper",
          "-lvterm",
          "-ldl",
          tsWrapperObj.getAbsolutePath,
          termWrapperObj.getAbsolutePath,
          ftWrapperObj.getAbsolutePath,
          "-lfreetype",
          "-lharfbuzz"
        ))
        .withCompileOptions(nativeConfig.value.compileOptions ++ Seq("-Isrc/main/c"))
        .withMode(Mode.debug)  // Enable debug mode for stack traces
        .withSourceLevelDebuggingConfig(_.enableAll)  // Enable source-level debugging
//        .withLTO(LTO.full)  // Disable LTO for better stack traces
//        .withMode(Mode.releaseFull)  // Use Mode.debug instead
//        .withGC(GC.commix)
//        .withOptimize(true)  // Disable optimization for debugging
    }
  )
