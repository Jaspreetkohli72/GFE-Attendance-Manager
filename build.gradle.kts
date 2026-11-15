// Top-level build file where you can add configuration options common to all sub-projects/modules.
try {
    val processEnvClass = Class.forName("java.lang.ProcessEnvironment")
    val theEnvField = processEnvClass.getDeclaredField("theEnvironment")
    theEnvField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val env = theEnvField.get(null) as? MutableMap<String, String>
    env?.keys?.toList()?.forEach { k ->
        env[k]?.let { v ->
            if (v.contains("\"")) {
                env[k] = v.replace("\"", "")
            }
        }
    }
    val theCiEnvField = processEnvClass.getDeclaredField("theCaseInsensitiveEnvironment")
    theCiEnvField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val ciEnv = theCiEnvField.get(null) as? MutableMap<String, String>
    ciEnv?.keys?.toList()?.forEach { k ->
        ciEnv[k]?.let { v ->
            if (v.contains("\"")) {
                ciEnv[k] = v.replace("\"", "")
            }
        }
    }
} catch (_: Exception) {
    // Ignore reflection errors if JDK security forbids it
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.devtools.ksp) apply false
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization) apply false
}