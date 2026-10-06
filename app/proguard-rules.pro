# kotlinx-serialization ships consumer rules; keep a safety net for the
# generated serializers of our network models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Retrofit interface methods use generics + annotations.
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
