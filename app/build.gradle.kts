plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.timemanager"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.timemanager"
        minSdk = 24
        targetSdk = 36
        versionCode = 13
        versionName = "1.4.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    // 👈 解决 Gson 找不到符号的错误，添加这一行！
    implementation("com.google.code.gson:gson:2.10.1")
    // CSV转XLSX导出功能依赖
    // 2024-06-14 10:30 修正：移除重复依赖（已通过libs统一管理，避免版本冲突）
    // implementation("androidx.appcompat:appcompat:1.3.1")
    // implementation("com.google.android.material:material:1.4.0")
    // implementation("androidx.constraintlayout:constraintlayout:2.1.3")
    // 2024-06-14 10:31 修正：移除低版本Gson依赖，避免与2.10.1版本冲突
    // implementation("com.google.code.gson:gson:2.8.8")
}