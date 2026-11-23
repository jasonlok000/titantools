plugins { id("com.android.library") }

android {
    namespace = "com.everyonesniche.titantools.res"
    compileSdk = 36
    defaultConfig {
        minSdk = 34
        consumerProguardFiles("proguard-rules.pro")
    }
    // The helper apps, built by tools/build.sh into android/assets/helpers.
    sourceSets.getByName("main") { assets.srcDir("assets") }
}
