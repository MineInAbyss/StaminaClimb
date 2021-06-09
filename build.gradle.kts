import com.mineinabyss.sharedSetup

plugins {
    java
    `maven-publish`
    id("com.github.johnrengelman.shadow") version "7.0.0"
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("io.github.slimjar") version "1.2.0"
    id("com.mineinabyss.shared-gradle") version "0.0.6"
}

sharedSetup()

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/groups/public/")
    maven("https://repo.mineinabyss.com/")
}

val serverVersion: String by project

dependencies {
    compileOnly("org.spigotmc:spigot-api:$serverVersion")

    slim("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    slim("org.jetbrains.kotlinx:kotlinx-serialization-json:1.2.1")
    slim("com.charleskorn.kaml:kaml:0.34.0") {
        exclude(group = "org.jetbrains.kotlin")
    }

    implementation("com.mineinabyss:idofront:0.6.14")
}

publishing {
    mineInAbyss(project) {
        from(components["java"])
    }
}
