plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "com.shihuaidexianyu.money.benchmark"
    compileSdk = 36

    defaultConfig {
        minSdk = 31
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.output.enable"] = "true"
        testInstrumentationRunnerArguments["listener"] =
            "androidx.benchmark.macro.junit4.SideEffectRunListener"
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

// A debug-named test variant resolves :app's debug APK, not its optimized fixture APK.
// Keep the matching benchmark variant so connectedCheck installs the correct target.
androidComponents {
    beforeVariants { builder -> builder.enable = builder.buildType == "benchmark" }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.junit)
    implementation(libs.androidx.uiautomator)
}
