# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ================= AI4Research Specific Rules =================

# 1. WebView JavascriptInterface
# 必须保留，否则 Release 包中 JS 无法调用 Android 方法
-keep class com.example.ai4research.ui.auth.WebAppInterface {
    public *;
}

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 2. DTO Data Classes (for JSON Serialization/Deserialization)
# 必须保留，否则 Retrofit 解析 JSON 会失败
-keep class com.example.ai4research.data.remote.dto.** { *; }

# 3. Retrofit API Interfaces
-keep class com.example.ai4research.data.remote.api.** { *; }

# 4. Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontwarn kotlinx.serialization.**
-keep class kotlinx.serialization.** { *; }

# 5. Hilt / Dagger (通常 Hilt 插件会自动处理，但加固一下)
-keep class com.example.ai4research.AI4ResearchApplication { *; }
