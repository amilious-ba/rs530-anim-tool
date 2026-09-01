plugins {
    kotlin("jvm") version "2.0.21"
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "rs530anim"
version = "0.1.0-step1"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.apache.commons:commons-compress:1.27.1")
}

javafx {
    version = "21.0.5"
    modules = listOf("javafx.controls", "javafx.graphics")
}

application {
    mainClass.set("rs530anim.MainKt")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
    if (project.findProperty("runArgs") == null) {
        args = listOf("view", "1456")
    }
}
