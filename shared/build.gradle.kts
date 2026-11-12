plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    androidLibrary {
        namespace = "com.kelly3d.ohnoitsneilgame.shared"
        compileSdk = 37
        minSdk = 29
    }
    jvm {
        val main = compilations.getByName("main")
        tasks.register<JavaExec>("runMapEditor") {
            group = "game development"
            description = "Launch the desktop tile-map editor"
            dependsOn(main.compileTaskProvider)
            classpath(main.output.allOutputs, main.runtimeDependencyFiles)
            mainClass.set("com.kelly3d.ohnoitsneilgame.editor.MapEditorKt")
            workingDir(rootProject.projectDir)
        }
        tasks.register<JavaExec>("exportAmigaHd") {
            group = "game development"
            description = "Upscale the decoded Amiga rooms and sprites to 4x into art/sprites/amiga/hd"
            dependsOn(main.compileTaskProvider)
            classpath(main.output.allOutputs, main.runtimeDependencyFiles)
            mainClass.set("com.kelly3d.ohnoitsneilgame.editor.AmigaHdArtKt")
            workingDir(rootProject.projectDir)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
