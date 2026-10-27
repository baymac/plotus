pluginManagement {
    repositories { mavenCentral(); gradlePluginPortal(); google() }
}
dependencyResolutionManagement {
    repositories { mavenCentral(); google() }
}
rootProject.name = "plotus"
include(":shared", ":composeApp", ":server")
project(":server").projectDir = file("services/api")
