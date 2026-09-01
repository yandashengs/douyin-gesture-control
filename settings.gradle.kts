pluginManagement {
    repositories {
        // 阿里云镜像（国内加速，优先于官方源）
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        // 官方源兜底
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 阿里云镜像：google() + mavenCentral() 的国内加速版
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        // 官方源兜底（MediaPipe Tasks AAR 在 mavenCentral）
        google()
        mavenCentral()
    }
}

rootProject.name = "DouyinGestureControl"
include(":app")
