package dev.cwby.pkgs

import dev.cwby.pkgs.sources.CommandSourceInstaller
import dev.cwby.pkgs.sources.GenericSourceInstaller
import dev.cwby.pkgs.sources.ISourceInstaller

import java.io.File
import java.io.IOException
import java.io.PrintWriter
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.{Set => MutableSet}
import scala.io.Source
import scala.util.Using

object PackageManager:
  val PACKAGES_DIR: String                                              = "config/packages/"
  val INTERNALS_DIR: String                                             = "config/internals/"
  private val sources: mutable.Map[PackageSourceType, ISourceInstaller] =
    mutable.Map[PackageSourceType, ISourceInstaller]()
  private val packages: mutable.Map[String, PackageData] = mutable.Map[String, PackageData]()
  private val installedPackages: MutableSet[String]      = readLocalRegistry()

  sources(PackageSourceType.GENERIC) = new GenericSourceInstaller()
  sources(PackageSourceType.COMMAND) = new CommandSourceInstaller()
  initializeAvailablePackages()

  private def createJdtlsPackage(): PackageData = {
    val packageData = new PackageData()
    packageData.name = "jdtls"
    packageData.description = "Java language server."
    packageData.homepage = "https://github.com/eclipse/eclipse.jdt.ls"

    val licenses = ListBuffer[String]()
    licenses += "EPL-2.0"
    packageData.licenses = licenses

    val languages = ListBuffer[String]()
    languages += "Java"
    packageData.languages = languages

    val categories = ListBuffer[PackageCategory]()
    categories += PackageCategory.LSP
    packageData.categories = categories

    val source = new PackageData.Source()
    source.`type` = PackageSourceType.GENERIC
    source.packageOrUrl =
      "https://www.eclipse.org/downloads/download.php?file=/jdtls/milestones/1.52.0/jdt-language-server-1.52.0-202510301627.tar.gz"
    source.executable = "bin/jdtls -data /home/cwby/.cache/jdtls-workspace"
    packageData.source = source

    val trigger   = new PackageData.Trigger()
    val filetypes = ListBuffer[String]()
    filetypes += "java"
    trigger.filetypes = filetypes

    val projectRoot = ListBuffer[String]()
    projectRoot += "pom.xml"
    projectRoot += "build.gradle"
    projectRoot += "build.gradle.kts"
    trigger.projectRoot = projectRoot
    packageData.trigger = trigger

    packageData.isInstalled = installedPackages.contains(packageData.name)

    packageData
  }

  private def createMetalsPackage(): PackageData = {
    val packageData = new PackageData()
    packageData.name = "metals"
    packageData.description = "Scala language server (Metals)."
    packageData.homepage = "https://scalameta.org/metals/"

    val licenses = ListBuffer[String]()
    licenses += "Apache-2.0"
    packageData.licenses = licenses

    val languages = ListBuffer[String]()
    languages += "Scala"
    packageData.languages = languages

    val categories = ListBuffer[PackageCategory]()
    categories += PackageCategory.LSP
    packageData.categories = categories

    val source = new PackageData.Source()
    source.`type` = PackageSourceType.COMMAND
    source.packageOrUrl = "https://github.com/coursier/launchers/raw/master/cs-x86_64-pc-linux.gz"
    source.executable = new File(INTERNALS_DIR + "metals/metals").getAbsolutePath
    packageData.source = source

    val trigger   = new PackageData.Trigger()
    val filetypes = ListBuffer[String]()
    filetypes += "scala"
    filetypes += "sbt"
    trigger.filetypes = filetypes

    val projectRoot = ListBuffer[String]()
    projectRoot += "build.sbt"
    projectRoot += "build.sc"
    projectRoot += "project/build.properties"
    projectRoot += "project/plugins.sbt"
    trigger.projectRoot = projectRoot
    packageData.trigger = trigger

    packageData.isInstalled = installedPackages.contains(packageData.name)

    packageData
  }

  def initializeAvailablePackages(): Unit = {
    // Inline package definitions based on config files
    val jdtls = createJdtlsPackage()
    packages(jdtls.name) = jdtls

    val metals = createMetalsPackage()
    packages(metals.name) = metals
  }

  def getPackages(): Iterable[PackageData] = {
    packages.values
  }

  def filterCategory(category: PackageCategory): List[PackageData] = {
    packages.values
      .filter(packageData => installedPackages.contains(packageData.name) && packageData.categories.contains(category))
      .toList
  }

  def installPackage(name: String): Unit = {
    val packageData = packages.get(name)
    if (packageData.isEmpty || packageData.get.isInstalled) {
      println("Package " + name + " not found or already installed")
      return
    }

    val pkg = packageData.get
    if (sources.contains(pkg.source.`type`)) {
      val installer = sources(pkg.source.`type`)
      try {
        installer.install(pkg)
        pkg.isInstalled = true
        installedPackages += pkg.name
        savePackages()
      } catch {
        case e: RuntimeException =>
          println(e.getMessage)
      }
    }
  }

  private def savePackages(): Unit = {
    val installedList = ListBuffer[String]()
    for (packageData <- packages.values) {
      if (packageData.isInstalled) {
        installedList += packageData.name
      }
    }

    val file = new File("config/registry.toml")
    try {
      val writer = new PrintWriter(file)
      writer.println("installed = [\"" + installedList.mkString("\", \"") + "\"]")
      writer.close()
    } catch {
      case e: IOException =>
        throw new RuntimeException(e)
    }
  }

  private def readLocalRegistry(): MutableSet[String] = {
    val registry = MutableSet[String]()

    val registryFile = new File("config/registry.toml")
    if (!registryFile.exists()) {
      return registry
    }

    val content = Using.resource(Source.fromFile(registryFile))(_.getLines().mkString("\n"))
    val quoted  = "\\\"([^\\\"]+)\\\"".r
    quoted.findAllMatchIn(content).foreach(m => registry += m.group(1))
    registry
  }
