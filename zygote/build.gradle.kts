import com.v7878.zygisk.gradle.ZygoteLoader

plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.com.github.aerathstuff.zygoteloader)
}

val appPackageName: String by rootProject.extra

android {
    namespace = "$appPackageName.zygote"

    defaultConfig {
        applicationId = namespace
        multiDexEnabled = false
    }
}

zygisk {
    // inject to system_server
    packages(ZygoteLoader.PACKAGE_SYSTEM_SERVER)

    // module properties
    id = "hma_oss_zygisk"
    name = "HMA-OSS Zygisk"
    author = "frknkrc44"
    description = "A Zygisk backend for HMA-OSS"
    entrypoint = "org.frknkrc44.hma_oss.zygote.ZygoteEntry"
    archiveName = "${rootProject.name}-ZYGISK-${android.defaultConfig.versionName}"
    isAddVariantToArchiveName = true
}

dependencies {
    implementation(projects.common)

    implementation(libs.androidx.annotation.jvm)
    implementation(libs.io.github.vova7878.androidvmtools)
    implementation(libs.io.github.vova7878.r8annotations)
    implementation(libs.dev.rikka.hidden.compat)

    compileOnly(libs.dev.rikka.hidden.stub)
}