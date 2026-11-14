# ProGuard / R8 rules for MTZ Tool

# Shizuku
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }

# apksig is invoked on-device for generated icon packs. Keep its internal
# implementation classes so R8 cannot strip signing engines in release builds.
-keep class com.android.apksig.** { *; }
-dontwarn com.android.apksig.**
