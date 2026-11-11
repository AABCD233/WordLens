# ---- ML Kit 文字识别：内部用了反射和 JNI，必须整体保留 ----
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text** { *; }
-keep class com.google.android.gms.internal.mlkit_common** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# ---- 我们自己的数据类（JSON 编解码按字段名拼字符串，避免被优化掉） ----
-keep class com.wordlens.app.data.model.** { *; }
-keep class com.wordlens.app.data.db.** { *; }

# ---- 关掉日志（release 更省） ----
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# 保留行号，崩溃栈还能看
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
