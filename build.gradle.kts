
val idofrontVersion: String by project

plugins {
    id("com.mineinabyss.conventions.kotlin")
    id("com.mineinabyss.conventions.papermc")
    id("com.mineinabyss.conventions.nms")
    id("com.mineinabyss.conventions.publication")
    id("com.mineinabyss.conventions.copyjar")
    kotlin("plugin.serialization")
}

repositories {
    maven("https://jitpack.io")
}

dependencies {
    compileOnly(libs.kotlin.stdlib)
    compileOnly(libs.kotlinx.serialization.kaml) {
        exclude(group = "org.jetbrains.kotlin")
    }
    compileOnly(libs.minecraft.skedule)
    compileOnly("com.mineinabyss:bonehurtingjuice:1.2.4")

    implementation("com.mineinabyss:idofront:$idofrontVersion")
    implementation(libs.idofront.nms)
}

tasks {
    shadowJar {
        archiveBaseName.set("StaminaClimb")
    }
}
