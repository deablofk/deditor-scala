package dev.cwby.pkgs

enum PackageSourceType(private val `type`: String) {

  case GENERIC extends PackageSourceType("GENERIC")
  case NPM     extends PackageSourceType("NPM")
  case DEB     extends PackageSourceType("DEB")
  case RPM     extends PackageSourceType("RPM")
  case COMMAND extends PackageSourceType("COMMAND")

  def getType(): String = {
    `type`
  }
}
