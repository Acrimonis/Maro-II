# Keep rules for a shrunk release build. Shrinking is OFF in build.gradle.kts until a release build
# has been exercised on a device; these rules are what that build will need.

# ── kotlinx.serialization ─────────────────────────────────────────────────────
# Serializers are looked up reflectively by the generated companion `serializer()` functions.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class ykws.android.maro.**$$serializer { *; }
-keepclassmembers class ykws.android.maro.** { *** Companion; }
-keepclasseswithmembers class ykws.android.maro.** { kotlinx.serialization.KSerializer serializer(...); }

# ── protobuf-javalite ─────────────────────────────────────────────────────────
-keep class com.google.protobuf.** { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }

# ── osmdroid ──────────────────────────────────────────────────────────────────
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**
