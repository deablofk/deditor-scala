package dev.cwby.pkgs

import scala.collection.mutable.ListBuffer
import scala.compiletime.uninitialized

object PackageData {
  class Source {
    var `type`: PackageSourceType = uninitialized
    var packageOrUrl: String      = uninitialized
    var executable: String        = uninitialized
  }

  class Trigger {
    var filetypes: ListBuffer[String]   = uninitialized
    var projectRoot: ListBuffer[String] = uninitialized
  }
}

class PackageData {
  var name: String                                       = uninitialized
  var description: String                                = uninitialized
  var homepage: String                                   = uninitialized
  var licenses: ListBuffer[String]                       = uninitialized
  var languages: ListBuffer[String]                      = uninitialized
  @transient var categories: ListBuffer[PackageCategory] = uninitialized
  var source: PackageData.Source                         = uninitialized
  var isInstalled: Boolean                               = uninitialized
  var trigger: PackageData.Trigger                       = uninitialized
}
