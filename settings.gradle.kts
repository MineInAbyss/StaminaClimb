pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.mineinabyss.com/releases")
        maven("https://repo.mineinabyss.com/snapshots")
        maven("https://repo.papermc.io/repository/maven-public/")
        mavenLocal()
        google()
    }
}

dependencyResolutionManagement {
    val idofrontVersion: String by settings

    repositories {
        maven("https://repo.mineinabyss.com/releases")
        maven("https://repo.mineinabyss.com/snapshots")
        mavenLocal()
    }

    versionCatalogs {
        create("idofrontLibs") {
            from("com.mineinabyss:catalog:$idofrontVersion")
            version("minecraft-server", "26.3.build.18-alpha")
            version("java", "25")
            version("kotlin", "2.4.20")
            version("creative", "1.15.1")
            version("idofront", "2.0")
            version("gearyPaper", "0.34")
        }
    }
}

rootProject.name = "stamina-climb"
