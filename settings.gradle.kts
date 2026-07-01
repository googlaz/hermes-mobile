pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Необходимый репозиторий для Tailscale Android SDK (если они хостят свои либы там)
        maven { url = java.net.URI("https://jitpack.io") }
    }
}

rootProject.name = "hermes-agent-app"
include(":app")
