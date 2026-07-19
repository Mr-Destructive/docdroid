# Cactus / JNI
-keep class com.docdroid.engine.CactusJNI { *; }
-keep class com.docdroid.engine.NeedleEngine { *; }

# Jackson JSON serialization
-keep class com.fasterxml.jackson.** { *; }
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.* <fields>;
    @com.fasterxml.jackson.annotation.* <init>(...);
}
-keep class kotlin.Metadata { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn javax.xml.**
-dontwarn org.xml.**

# Keep data classes used by Jackson
-keep class com.docdroid.harness.ToolResult { *; }
-keep class com.docdroid.model.Message { *; }
-keep class com.docdroid.engine.** { *; }

# Compose
-dontwarn androidx.compose.**
