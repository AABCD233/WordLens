# 当前 release 也没开混淆，这里只留必要规则，方便以后自己打开 minify。
# ML Kit 通过反射创建识别器
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-dontwarn com.google.mlkit.**

# SQLite 资产词典通过列名取值，不依赖反射，无需额外规则
