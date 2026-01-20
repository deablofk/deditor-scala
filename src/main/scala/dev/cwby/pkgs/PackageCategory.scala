package dev.cwby.pkgs

enum PackageCategory(private val category: String) {
  case LSP       extends PackageCategory("LSP")
  case FORMATTER extends PackageCategory("FORMATTER")

  def getCategory(): String = {
    category
  }
}
