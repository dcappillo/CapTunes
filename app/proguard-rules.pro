# Rules for the release build's code shrinker (R8).
# The libraries Cap Tunes uses (Media3, Coil, OkHttp, kotlinx.serialization, Compose)
# ship their own rules, so only app-specific ones belong here.

# Keep the generated serializers for the app's own data classes (server replies, saved queue).
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class app.captunes.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.captunes.**$$serializer { *; }

# Readable crash reports: keep file names and line numbers.
-keepattributes SourceFile, LineNumberTable
