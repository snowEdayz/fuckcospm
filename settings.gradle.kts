pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            // Xposed API 官方仓库（de.robv.android.xposed:api:82）
            url = uri("https://api.xposed.info/")
        }
    }
}

rootProject.name = "fuckcospm"
include(":app")
