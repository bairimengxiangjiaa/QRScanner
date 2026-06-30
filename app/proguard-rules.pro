# ==================== QRScanner ProGuard Rules ====================

# ---- ML Kit Barcode Scanning ----
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.odml.** { *; }

# ---- CameraX ----
-keep class androidx.camera.** { *; }

# ---- Hilt / Dagger ----
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.lifecycle.HiltViewModel
-keepclassmembers class * { @dagger.hilt.android.lifecycle.HiltViewModel <init>(...); }

# ---- Compose ----
-dontwarn androidx.compose.**
-keep class androidx.compose.runtime.** { *; }

# ---- Kotlin Metadata ----
-keep class kotlin.Metadata { *; }
-keepclassmembers class ** {
    @kotlin.Metadata *;
}

# ---- App Models (used by reflection / serialization) ----
-keep class com.example.qrscanner.domain.model.** { *; }

# ---- Keep generic signatures for reflection ----
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
