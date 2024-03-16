
val idofrontVersion: String by project

plugins {
    id("com.mineinabyss.conventions.kotlin.jvm")
    id("com.mineinabyss.conventions.papermc")
    id("com.mineinabyss.conventions.nms")
    id("com.mineinabyss.conventions.publication")
    id("com.mineinabyss.conventions.copyjar")
    id("com.mineinabyss.conventions.autoversion")
    alias(idofrontLibs.plugins.kotlinx.serialization)
}

repositories {
    maven("https://repo.mineinabyss.com/snapshots")
    maven("https://jitpack.io")
    mavenLocal()
}

dependencies {
    compileOnly(idofrontLibs.kotlin.stdlib)
    compileOnly(idofrontLibs.kotlinx.serialization.kaml) {
        exclude(group = "org.jetbrains.kotlin")
    }
    compileOnly(idofrontLibs.kotlinx.coroutines)
    compileOnly(idofrontLibs.minecraft.mccoroutine)
    compileOnly(libs.geary.papermc)
    compileOnly(libs.bonehurtingjuice)

    implementation(idofrontLibs.bundles.idofront.core)
    implementation(idofrontLibs.idofront.nms)
}
