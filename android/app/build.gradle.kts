plugins { id("com.android.application") }
android {
    namespace = "net.chateaulore.roguesgallery"
    compileSdk = 37
    defaultConfig {
        applicationId = "net.chateaulore.roguesgallery"
        minSdk = 26
        targetSdk = 37
        versionCode = 13
        versionName = "1.0.2"
    }
    val releaseKeystore = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull
    if (!releaseKeystore.isNullOrBlank()) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").get()
            }
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("release")
    }
}
dependencies { testImplementation("junit:junit:4.13.2") }
