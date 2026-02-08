import scala.scalanative.build.*
import scala.scalanative.sbtplugin.ScalaNativePlugin.autoImport.nativeConfig

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.7.4"

ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

ThisBuild / scalacOptions ++= Seq(
  "-Wall",
  "-explain"
)

lazy val root = (project in file("."))
  .aggregate(editor)
  .settings(
    name := "deditor-root",
    publish / skip := true
  )

lazy val guitk = (project in file("guitk"))
  .enablePlugins(ScalaNativePlugin)
  .settings(
    name := "guitk",
    nativeConfig := {
      nativeConfig.value
        .withMode(Mode.debug)
        .withSourceLevelDebuggingConfig(_.enableAll)
    }
  )

lazy val editor = (project in file("editor"))
  .enablePlugins(ScalaNativePlugin)
  .dependsOn(guitk)
  .settings(
    name := "deditor",
    libraryDependencies += "com.lihaoyi" %%% "upickle" % "4.4.1",
    nativeConfig := {
      val wrapperLibDir = (ThisBuild / baseDirectory).value / "target"

      nativeConfig.value
        .withLinkingOptions(nativeConfig.value.linkingOptions ++ Seq(
          "-L/usr/local/lib",
          s"-L$wrapperLibDir",
          s"-Wl,-rpath,$wrapperLibDir",
          "-lGL",
          "-lSDL3",
          "-lfreetype",
          "-lharfbuzz",
          "-lfreetype_harfbuzz_wrapper",
          "-ltree-sitter",
          "-lvterm",
          "-ldl"
        ))
        .withMode(Mode.debug)
        .withSourceLevelDebuggingConfig(_.enableAll)
//        .withLTO(LTO.full)
//        .withMode(Mode.releaseFull)
//        .withGC(GC.commix)
//        .withOptimize(true)
    }
  )
