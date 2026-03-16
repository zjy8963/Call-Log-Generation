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
# 保留应用入口
-keep public class com.uselesswater.multicallloggeneration.MainActivity
-keep public class com.uselesswater.multicallloggeneration.ActivationActivity

# 保留Compose相关
-keep class androidx.compose.** { *; }
-keep class androidx.lifecycle.** { *; }

# 保留网络请求相关（百度OCR、激活验证）
-keep class org.json.** { *; }
-keep class java.net.** { *; }
-keep class javax.crypto.** { *; }

# 保留数据类
-keepclassmembers class com.uselesswater.multicallloggeneration.** {
    *;
}

# 保留日志（可选，发布时可移除）
-keep class android.util.Log { *; }

# 混淆后保留行号信息（方便崩溃分析）
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable