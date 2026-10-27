pluginManagement {
    repositories { mavenCentral(); gradlePluginPortal(); google() }
}
dependencyResolutionManagement {
    repositories { mavenCentral(); google() }
}
rootProject.name = "plotus"
include(":shared", ":composeApp", ":server")
project(":server").projectDir = file("services/api")
project(":composeApp").projectDir = file("apps/client")
project(":shared").projectDir = file("packages/shared")
