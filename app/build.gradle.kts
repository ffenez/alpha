plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "app.alpha"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.alpha"
        minSdk = 26
        targetSdk = 35
        versionCode = 109
        // Стадия разработки — в ИМЕНИ приложения («Alpha»), поэтому в номере
        // версии её нет: один факт живёт в одном месте, иначе подпись под
        // иконкой читалась бы как «Alpha 0.1.0-alpha».
        versionName = "0.63.0"

        // Под иконкой — только имя: версия там ничего не решает, а место на
        // домашнем экране узкое, и длинная подпись обрезается. Номер версии
        // живёт в «О приложении», где его и ищут.
        resValue("string", "app_name", "Alpha")
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

    testOptions {
        unitTests {
            // Robolectric-смоук (app/src/test/.../smoke) рисует настоящие
            // экраны: ему нужны ресурсы APK (шрифты, строки, иконки).
            isIncludeAndroidResources = true
        }
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
    // Robolectric smoke: every screen opens on a simulated device — the class
    // of defects invisible to plain JVM tests (Android XML parser quirks,
    // nested scroll measurement, NaN reaching a Canvas). See
    // app/src/test/kotlin/app/alpha/smoke.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    // debug-, а не testImplementation: Robolectric читает манифест debug-сборки,
    // и ComponentActivity для createComposeRule обязана быть объявлена именно там.
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Смоук гоняется ОТДЕЛЬНО от быстрых JVM-тестов: Robolectric поднимает целый
// Android-класслоадер, и его минуты не должны стоять на пути секундных тестов.
// `./gradlew :app:smokeTest` — только смоук; обычный `test` его исключает.
val smokeRequested = gradle.startParameter.taskNames.any {
    it.substringAfterLast(":") == "smokeTest"
}
// `./gradlew :app:spectrumValidation` — научная валидация спектра на реальном
// файле: прогоняет цепочку XML → пики → значимость и пишет
// `app/build/reports/spectrum_validation.json` (SPECTRUM_VALIDATION.md §16).
val validationRequested = gradle.startParameter.taskNames.any {
    it.substringAfterLast(":") == "spectrumValidation"
}
tasks.withType<Test>().configureEach {
    filter {
        isFailOnNoMatchingTests = false
        if (validationRequested) {
            includeTestsMatching("app.alpha.analysis.SpectrumValidationTest")
            includeTestsMatching("app.alpha.analysis.PeakScienceTest")
        } else if (smokeRequested) {
            includeTestsMatching("app.alpha.smoke.*")
        } else {
            excludeTestsMatching("app.alpha.smoke.*")
        }
    }
    // Robolectric + Compose + native graphics не живут в дефолтных 512 МБ.
    if (smokeRequested) maxHeapSize = "4g"
}

tasks.register("spectrumValidation") {
    group = "verification"
    description = "Scientific validation of the spectrum pipeline on the real XML fixture."
    dependsOn("testDebugUnitTest")
    doLast {
        val report = layout.buildDirectory.file("reports/spectrum_validation.json").get().asFile
        logger.lifecycle("отчёт: ${report.absolutePath}")
    }
}

tasks.register("smokeTest") {
    group = "verification"
    description = "Robolectric smoke: every screen opens and does not crash."
    dependsOn("testDebugUnitTest")
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
