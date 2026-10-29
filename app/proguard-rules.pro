# Navigation type-safe routes are @Serializable; kotlinx.serialization ships its own consumer rules.

# ML Kit finds its component registrars and model downloader by reflection; keep them whole so the
# Russian model download is never broken by R8 full mode.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_translate.** { *; }
-keep class com.google.android.gms.internal.mlkit_common.** { *; }
-keep class com.google.firebase.components.** { *; }
