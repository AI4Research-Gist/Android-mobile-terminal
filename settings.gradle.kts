pluginManagement {
    repositories {
        // Aliyun Mirrors
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        
        // Official repositories first to avoid mirror TLS issues.
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
        // Aliyun Mirrors
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        
        // Official repositories first to avoid mirror TLS issues.
        google()
        mavenCentral()
        // JitPack is still needed for GitHub-hosted deps.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "AI4Research"
include(":app")
