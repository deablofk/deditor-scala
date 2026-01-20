package dev.cwby.treesitter

import dev.cwby.config.TreeSitterParsersConfig
import dev.cwby.expandHome

import java.io.File
import scala.collection.mutable

object TreeSitterGrammarManager {

  def resolveLanguageForFileType(fileType: String, config: TreeSitterParsersConfig): String = {
    if (fileType == null || fileType.trim.isEmpty) return ""
    val ft = {
      val t = fileType.trim
      if (t.startsWith(".")) t.substring(1) else t
    }

    val cfg = Option(config).flatMap(c => Option(c.grammars)).getOrElse(mutable.ArrayBuffer.empty)
    var i   = 0
    while (i < cfg.length) {
      val g = cfg(i)
      if (g != null && g.language != null && g.language.trim.nonEmpty) {
        if (g.filetypes != null && g.filetypes.contains(ft)) {
          return g.language.trim
        }
      }
      i += 1
    }

    ""
  }

  private def repoUrlFor(language: String): String = {
    s"https://github.com/tree-sitter/tree-sitter-$language"
  }

  private def repoUrlForLanguage(language: String, config: TreeSitterParsersConfig): String = {
    if (language == null || language.isEmpty) return repoUrlFor(language)
    val gs = Option(config).map(_.grammars).getOrElse(mutable.ArrayBuffer.empty)
    var i  = 0
    while (i < gs.length) {
      val g = gs(i)
      if (g != null && g.language != null) {
        if (g.language.trim == language && g.repo != null && g.repo.trim.nonEmpty) {
          return g.repo.trim
        }
      }
      i += 1
    }

    repoUrlFor(language)
  }

  private def isEnabled(language: String, config: TreeSitterParsersConfig): Boolean = {
    if (language == null || language.isEmpty) return false
    val cfg = Option(config).map(_.grammars).getOrElse(mutable.ArrayBuffer.empty)
    var i   = 0
    while (i < cfg.length) {
      val g = cfg(i)
      if (g != null && g.language != null && g.language.trim == language) return true
      i += 1
    }
    false
  }

  def ensureRepo(language: String, config: TreeSitterParsersConfig): Option[File] = {
    if (language == null || language.isEmpty) return None

    ensureDirs()
    val repoDir = new File(reposDir, language)
    if (repoDir.exists() && new File(repoDir, ".git").exists()) {
      return Some(repoDir)
    }

    val mode    = Option(config).map(_.mode).getOrElse("on_demand")
    val enabled = isEnabled(language, config)
    if (mode != "off" && enabled && verifyCommandOnPath("git")) {
      Some(cloneOrUpdateRepo(language, repoUrlForLanguage(language, config)))
    } else {
      None
    }
  }

  private def installRootDir: File = {
    new File(expandHome("~/.config/deditor/treesitter"))
  }

  private def reposDir: File = {
    new File(installRootDir.getAbsolutePath + "/repos")
  }

  private def parsersDir: File = {
    new File(installRootDir.getAbsolutePath + "/parsers")
  }

  private def ensureDirs(): Unit = {
    if (!reposDir.exists()) reposDir.mkdirs()
    if (!parsersDir.exists()) parsersDir.mkdirs()
  }

  private def verifyCommandOnPath(command: String): Boolean = {
    try {
      val process = new ProcessBuilder("sh", "-lc", "command -v " + command + " >/dev/null 2>&1").start()
      process.waitFor() == 0
    } catch {
      case _: Throwable => false
    }
  }

  private def cloneOrUpdateRepo(language: String, url: String): File = {
    ensureDirs()
    val repoDir = new File(reposDir, language)

    if (repoDir.exists() && new File(repoDir, ".git").exists()) {
      val pb = new ProcessBuilder("sh", "-lc", "git -C '" + repoDir.getAbsolutePath + "' pull --ff-only")
      val p  = pb.start()
      p.waitFor()
      repoDir
    } else {
      if (repoDir.exists()) {
        // If directory exists but isn't a git repo, don't try to overwrite it
        repoDir
      } else {
        val pb = new ProcessBuilder("sh", "-lc", "git clone --depth 1 '" + url + "' '" + repoDir.getAbsolutePath + "'")
        val p  = pb.start()
        p.waitFor()
        repoDir
      }
    }
  }

  private def buildSharedLibrary(repoDir: File, language: String): Option[File] = {
    ensureDirs()
    val out = new File(parsersDir, language + ".so")

    val cmd = "set -e; " +
      "cd '" + repoDir.getAbsolutePath + "'; " +
      "files='src/parser.c'; " +
      "if [ -f src/scanner.c ]; then files=\"$files src/scanner.c\"; fi; " +
      "cc -fPIC -shared -O2 -I./src -o '" + out.getAbsolutePath + "' $files"

    try {
      val pb   = new ProcessBuilder("sh", "-lc", cmd)
      val p    = pb.start()
      val code = p.waitFor()
      if (code == 0 && out.exists()) Some(out) else None
    } catch {
      case _: Throwable => None
    }
  }

  def resolveParserPath(language: String, config: TreeSitterParsersConfig): Option[String] = {
    if (language == null || language.isEmpty) return None

    ensureDirs()
    val local = new File(parsersDir, language + ".so")
    if (local.exists()) return Some(local.getAbsolutePath)

    val userDir = System.getProperty("user.dir")
    if (userDir != null && userDir.nonEmpty) {
      val legacy = new File(userDir + "/config/highlight/" + language + ".so")
      if (legacy.exists()) return Some(legacy.getAbsolutePath)
    }

    val mode    = Option(config).map(_.mode).getOrElse("on_demand")
    val enabled = isEnabled(language, config)

    if (mode != "off" && enabled && verifyCommandOnPath("git") && verifyCommandOnPath("cc")) {
      val repoDir = cloneOrUpdateRepo(language, repoUrlForLanguage(language, config))
      buildSharedLibrary(repoDir, language).map(_.getAbsolutePath)
    } else {
      None
    }
  }

  def preinstallEnabled(config: TreeSitterParsersConfig): Unit = {
    if (config == null) return

    val mode = Option(config.mode).getOrElse("on_demand")
    if (mode != "preinstall") return

    val it = config.grammars.iterator
    while (it.hasNext) {
      val g = it.next()
      if (g != null && g.language != null && g.language.trim.nonEmpty) {
        resolveParserPath(g.language.trim, config)
      }
    }
  }
}
