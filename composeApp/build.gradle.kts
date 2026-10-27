import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
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
        }
        jvmMain.dependencies { implementation(compose.desktop.currentOs) }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
compose.resources { packageOfResClass = "com.plotus.resources" }
compose.desktop { application { mainClass = "com.plotus.MainKt" } }
