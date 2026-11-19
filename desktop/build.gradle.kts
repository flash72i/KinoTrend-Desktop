import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.21"
    id("org.jetbrains.compose") version "1.8.2"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
}

group = "app.kinotrend"
version = "2.3.8"

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("org.json:json:20240303")
    implementation("net.java.dev.jna:jna-platform:5.14.0")
    implementation("org.openjfx:javafx-base:21.0.5:win")
    implementation("org.openjfx:javafx-graphics:21.0.5:win")
    implementation("org.openjfx:javafx-media:21.0.5:win")
    implementation("org.openjfx:javafx-swing:21.0.5:win")
}

compose.desktop {
    application {
        mainClass = "app.kinotrend.desktop.MainKt"

        // Боковые кнопки мыши (назад/вперёд) AWT отдаёт только с этим флагом.
        jvmArgs += listOf("-Dsun.awt.enableExtraMouseButtons=true")

        nativeDistributions {
            modules("jdk.unsupported.desktop", "jdk.unsupported", "java.logging")
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "KinoTrend"
            packageVersion = "2.3.8"
            windows {
                iconFile.set(project.file("icon.ico"))
            }
        }
    }
}
