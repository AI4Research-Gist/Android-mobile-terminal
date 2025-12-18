pluginManagement {
    repositories {
        // 阿里云镜像 - Google Maven（优先）
        maven {
            url = uri("https://maven.aliyun.com/repository/google")
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // 阿里云镜像 - Gradle Plugin Portal
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        // 阿里云镜像 - Maven Central
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        // 阿里云镜像 - JCenter
        maven { url = uri("https://maven.aliyun.com/repository/jcenter") }
        // 阿里云镜像 - Public（综合仓库）
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        
        // 备用：官方仓库（当镜像无法访问时）
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
        // 阿里云镜像 - Google Maven（优先）
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        // 阿里云镜像 - Maven Central
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        // 阿里云镜像 - JCenter
        maven { url = uri("https://maven.aliyun.com/repository/jcenter") }
        // 阿里云镜像 - Public（综合仓库）
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        
        // JitPack（用于 GitHub 依赖，如 compose-markdown）
        maven { url = uri("https://jitpack.io") }
        
        // 备用：官方仓库
        google()
        mavenCentral()
    }
}

rootProject.name = "AI4Research"
include(":app")
 