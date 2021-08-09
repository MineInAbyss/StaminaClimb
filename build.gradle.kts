plugins {
    java
    `maven-publish`
//    id("com.github.johnrengelman.shadow") version "7.0.0"
//    kotlin("jvm")
    kotlin("plugin.serialization")
//    id("com.mineinabyss.shared-gradle") version "0.0.6"
    id("com.mineinabyss.conventions.kotlin") version "1.5.21-14"
    id("com.mineinabyss.conventions.copyjar") version "1.5.21-14"
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/groups/public/")
    maven("https://repo.mineinabyss.com/")
}

val serverVersion: String by project

dependencies {
    compileOnly("org.spigotmc:spigot-api:$serverVersion")

    compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.2.1")
    compileOnly("com.charleskorn.kaml:kaml:0.34.0") {
        exclude(group = "org.jetbrains.kotlin")
    }

    implementation("com.mineinabyss:idofront:0.6.14")
}

tasks {
    shadowJar {
        relocate("com.mineinabyss.idofront", "${project.group}.${project.name}.idofront".toLowerCase())
        relocate("io.github.slimjar.app", "io.github.slimjar.app.${project.group}.${project.name}".toLowerCase())
    }
}

publishing {
    mineInAbyss(project) {
        from(components["java"])
    }
}
