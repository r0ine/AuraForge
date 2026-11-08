plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
    id("com.gradleup.shadow") version "9.6.1"
}

group = "com.r0ine"
version = "1.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
}

kotlin {
    jvmToolchain(21)
}

tasks {
    processResources {
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("plugin.yml") { expand(props) }
    }

    jar {
        enabled = false
    }

    shadowJar {
        archiveClassifier.set("")
        relocate("kotlin", "com.r0ine.auraforge.libs.kotlin")
        relocate("kotlinx", "com.r0ine.auraforge.libs.kotlinx")
        relocate("org.intellij.lang.annotations", "com.r0ine.auraforge.libs.annotations.intellij")
        relocate("org.jetbrains.annotations", "com.r0ine.auraforge.libs.annotations.jetbrains")
        mergeServiceFiles()
        exclude("META-INF/*.kotlin_module", "META-INF/versions/*/module-info.class", "DebugProbesKt.bin")
    }

    assemble {
        dependsOn(shadowJar)
    }
}
