import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.io.File
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.lifeos.vrqtzm"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    fun parseEnvFile(file: File): Map<String, String> {
      if (!file.exists()) return emptyMap()
      val result = mutableMapOf<String, String>()
      file.readLines().forEach { rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("#")) return@forEach
        val cleanLine = if (line.startsWith("export ")) line.removePrefix("export ").trim() else line
        val eqIdx = cleanLine.indexOf('=')
        if (eqIdx <= 0) return@forEach
        val k = cleanLine.substring(0, eqIdx).trim()
        var v = cleanLine.substring(eqIdx + 1).trim()
        if (v.length >= 2 && ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'")))) {
          v = v.substring(1, v.length - 1).trim()
        }
        result[k] = v
      }
      return result
    }

    fun parsePropertiesFile(file: File): Map<String, String> {
      if (!file.exists()) return emptyMap()
      val props = Properties()
      file.inputStream().use { props.load(it) }
      return props.stringPropertyNames().associateWith { props.getProperty(it).orEmpty().trim() }
    }

    fun cleanSecretCandidate(raw: String?): String {
      val trimmed = raw?.trim().orEmpty()
      return if (trimmed.length >= 2 &&
        ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
          (trimmed.startsWith("'") && trimmed.endsWith("'")))
      ) {
        trimmed.substring(1, trimmed.length - 1).trim()
      } else {
        trimmed
      }
    }

    fun isPlaceholderOrEmpty(cleaned: String): Boolean {
      return cleaned.isBlank() ||
        cleaned.equals("null", ignoreCase = true) ||
        cleaned.equals("undefined", ignoreCase = true) ||
        cleaned.equals("none", ignoreCase = true) ||
        cleaned.equals("your_api_key_here", ignoreCase = true) ||
        cleaned.startsWith("YOUR_", ignoreCase = true) ||
        (cleaned.startsWith("<") && cleaned.endsWith(">")) ||
        (cleaned.startsWith("[") && cleaned.endsWith("]"))
    }

    fun isMaskedSecret(cleaned: String): Boolean {
      return cleaned.contains("...") || cleaned.contains("***") || cleaned.contains("•••")
    }

    val dotEnvMap = parseEnvFile(rootProject.file(".env"))
    val localPropsMap = parsePropertiesFile(rootProject.file("local.properties"))

    fun resolveSecretValue(vararg keys: String): String {
      val candidates = mutableListOf<String>()
      for (key in keys) {
        candidates += cleanSecretCandidate(providers.environmentVariable(key).orNull)
        candidates += cleanSecretCandidate(System.getenv(key))
        candidates += cleanSecretCandidate(providers.gradleProperty(key).orNull)
        candidates += cleanSecretCandidate(dotEnvMap[key])
        candidates += cleanSecretCandidate(localPropsMap[key])
      }
      val valid = candidates.firstOrNull { !isPlaceholderOrEmpty(it) && !isMaskedSecret(it) }
      if (valid != null) return valid
      // Preserve a masked value only if no real key exists anywhere so runtime diagnostics can report *_KEY_MASKED
      return candidates.firstOrNull { !isPlaceholderOrEmpty(it) } ?: ""
    }

    fun String.escapeBuildConfig(): String =
      replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "").replace("\r", "")

    buildConfigField(
      "String",
      "OPENROUTER_API_KEY",
      "\"${resolveSecretValue("OPENROUTER_API_KEY").escapeBuildConfig()}\""
    )
    buildConfigField(
      "String",
      "GEMINI_API_KEY",
      "\"${resolveSecretValue("GEMINI_API_KEY", "GOOGLE_API_KEY").escapeBuildConfig()}\""
    )
    buildConfigField(
      "String",
      "GOOGLE_WEB_CLIENT_ID",
      "\"${resolveSecretValue("GOOGLE_WEB_CLIENT_ID").escapeBuildConfig()}\""
    )
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
  ignoreList.add("OPENROUTER_API_KEY")
  ignoreList.add("GEMINI_API_KEY")
  ignoreList.add("GOOGLE_WEB_CLIENT_ID")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(platform(libs.supabase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.glance.appwidget)
  implementation(libs.androidx.glance.material3)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  // implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.ktor.client.android)
  implementation(libs.supabase.auth)
  implementation(libs.supabase.functions)
  implementation(libs.supabase.postgrest)
  implementation(libs.supabase.realtime)
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  // implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  // "ksp"(libs.moshi.kotlin.codegen)
}

// Keep androidx.lifecycle compatible with compileSdk 36.1 (supabase-kt 3.9.0 transitively pulls 2.11.0 requiring API 37).
configurations.configureEach {
  resolutionStrategy.eachDependency {
    if (requested.group == "androidx.lifecycle") {
      useVersion("2.9.4")
    }
  }
}
