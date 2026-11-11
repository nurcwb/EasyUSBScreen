import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

val turingRuntime by tasks.registering(Zip::class) {
    archiveFileName.set("turing-runtime.zip")
    destinationDirectory.set(layout.buildDirectory.dir("turingRuntime"))
    from("src/turingRuntime")
    from(rootProject.file("themes")) { into("turing/res/themes") }
}

tasks.processResources { from(turingRuntime) }

val appPackageName = "br.com.infonur.easyusbscreen"
val appVersion = "1.0.0"

compose.desktop {
    application {
        mainClass = "br.com.infonur.easyusbscreen.MainKt"

        nativeDistributions {
            modules("java.net.http")
            targetFormats(TargetFormat.Deb)
            packageName = appPackageName
            packageVersion = appVersion
            linux { iconFile.set(project.file("src/packaging/app-icon.png")) }
        }
    }
}

tasks.register<Tar>("packagePortableLinux") {
    group = "distribution"
    description = "Packages the Linux x86-64 desktop application as a portable tar.gz archive."
    dependsOn("createDistributable")
    archiveFileName.set("EasyUSBScreen-$appVersion-linux-x86_64.tar.gz")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    compression = Compression.GZIP
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true

    from(layout.buildDirectory.dir("compose/binaries/main/app/$appPackageName")) {
        into("EasyUSBScreen")
        eachFile {
            if (file.canExecute()) permissions { unix("755") }
        }
    }
    from("src/packaging/portable") {
        into("EasyUSBScreen")
        filesMatching(listOf("iniciar.sh", "instalar-atalho.sh", "EasyUSBScreen.desktop")) { permissions { unix("755") } }
    }
    from("src/packaging/app-icon.png") {
        into("EasyUSBScreen")
        rename { "easyusbscreen.png" }
    }
    doFirst {
        check(System.getProperty("os.name") == "Linux" && System.getProperty("os.arch") in setOf("amd64", "x86_64")) {
            "O pacote portátil precisa ser gerado em Linux x86-64."
        }
    }
}
