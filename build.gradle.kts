plugins {
    alias(libs.plugins.kotlin.multiplatform).apply(false)
    alias(libs.plugins.compose.multiplatform).apply(false)
    alias(libs.plugins.kotlin.serialization).apply(false)
    alias(libs.plugins.compose.compiler).apply(false)
}

// Root proje build dizini - Turkce karakter sorununu onlemek icin ASCII yol
layout.buildDirectory.set(file("C:/KuranBuild/root"))
