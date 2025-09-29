# iText koruma kuralları
-keep class com.itextpdf.** { *; }
-dontwarn com.itextpdf.**

# FFmpegKit koruma kuralları
-keep class com.arthenica.ffmpegkit.** { *; }
-dontwarn com.arthenica.ffmpegkit.**

# Basit koruma kuralları
-keep class com.itextpdf.** { *; }
-keep class com.github.arthenica.** { *; }
-keep class org.apache.poi.** { *; }
-keep class net.lingala.zip4j.** { *; }

# Kotlin coroutines
-keep class kotlinx.coroutines.** { *; }

# Apache POI koruma kuralları
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep class schemasMicrosoftComVml.** { *; }
-keep class org.openxmlformats.schemas.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn schemasMicrosoftComVml.**
-dontwarn org.openxmlformats.schemas.**

# Diğer kütüphane kuralları
-keep class net.lingala.zip4j.** { *; }
-dontwarn net.lingala.zip4j.**

-keep class io.coil.** { *; }
-dontwarn io.coil.**

# Kotlin coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**