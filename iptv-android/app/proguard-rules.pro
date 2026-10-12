# Media3, Coil and Compose ship their own consumer rules. These silence optional references.
-dontwarn org.checkerframework.**
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# The player loads the FFmpeg audio decoder by name at runtime, so shrinking must not remove it.
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keep class androidx.media3.decoder.** { *; }
