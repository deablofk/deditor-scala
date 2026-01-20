package dev.cwby.pkgs.sources

import dev.cwby.pkgs.PackageData

trait ISourceInstaller {

  def install(packageData: PackageData): Unit
}
