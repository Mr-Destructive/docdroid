-dontwarn com.chaquo.python.**
-keep class com.chaquo.python.** { *; }
-keep class com.docdroid.python.** { *; }
-keepclassmembers class * {
    @com.chaquo.python.PyObject <methods>;
}
