plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "app.radiacode"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.radiacode"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        // Стадия разработки — в ИМЕНИ приложения («Alpha»), поэтому в номере
        // версии её нет: один факт живёт в одном месте, иначе подпись под
        // иконкой читалась бы как «Alpha 0.1.0-alpha».
        versionName = "0.1.0"

        // Имя приложения собирается из версии: подпись под иконкой и строка в
        // «О приложении» не могут разойтись, потому что источник один.
        resValue("string", "app_name", "Alpha 0.1.0")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // Нужен, чтобы тест мог сверить номер версии сборки со списком
        // обновлений: два источника одного факта обязаны совпадать.
        buildConfig = true
    }
}

// Exported Room schemas are committed (app/schemas) so migrations are
// reviewable and JVM-testable without instrumentation.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // Kable 0.35+ exposes kotlin.uuid.Uuid in its public API.
        freeCompilerArgs.add("-opt-in=kotlin.uuid.ExperimentalUuidApi")
    }
}

dependencies {
    implementation(project(":protocol"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kable.core)
    implementation(libs.osmdroid.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    // Migration test: replay MigrationSql on a real SQLite built from the
    // exported schema JSON (app/schemas) — no instrumentation needed.
    testImplementation(libs.sqlite.jdbc)
    testImplementation(libs.org.json)
}

// Repo convention: the fresh debug APK always lands in <repo>/apk/app-debug.apk.
val copyDebugApk by tasks.registering(Copy::class) {
    from(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    into(rootProject.layout.projectDirectory.dir("apk"))
}

afterEvaluate {
    tasks.named("assembleDebug") {
        finalizedBy(copyDebugApk)
    }
    copyDebugApk.configure {
        dependsOn(tasks.named("packageDebug"))
    }
}
