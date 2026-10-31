import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    kotlin("plugin.serialization")
}
kotlin {
    jvm()
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "PlotusApp"
            isStatic = true
        }
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "plotus"
        browser {
            commonWebpackConfig {
                outputFileName = "plotus.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    port = 8081
                }
            }
        }
        binaries.executable()
    }
    jvmToolchain(17)
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }
        jvmMain.dependencies { implementation(compose.desktop.currentOs) }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
compose.resources { packageOfResClass = "com.plotus.resources" }
compose.desktop { application { mainClass = "com.plotus.MainKt" } }

// The optional browser viewport is built locally and shipped with the Wasm app.
// Native targets retain their shared Compose screens without this dependency.
val planViewerDirectory = rootProject.layout.projectDirectory.dir("apps/plan-viewer")
val installPlanViewer by tasks.registering(Exec::class) {
    workingDir(planViewerDirectory)
    commandLine("npm", "ci", "--no-audit", "--no-fund")
    inputs.files(planViewerDirectory.file("package.json"), planViewerDirectory.file("package-lock.json"))
    outputs.file(planViewerDirectory.file("node_modules/.package-lock.json"))
    outputs.upToDateWhen { planViewerDirectory.file("node_modules/.bin/vite").asFile.exists() }
}
val buildPlanViewer by tasks.registering(Exec::class) {
    dependsOn(installPlanViewer)
    workingDir(planViewerDirectory)
    commandLine("npm", "run", "build")
    inputs.files(fileTree(planViewerDirectory) { exclude("node_modules/**", "dist/**") })
    outputs.dir(planViewerDirectory.dir("dist"))
}
tasks.named<Copy>("wasmJsProcessResources") {
    dependsOn(buildPlanViewer)
    from(planViewerDirectory.dir("dist")) { into("plan-viewer") }
}
