# ML Keep
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.odml.** { *; }

# CameraX
-keep class androidx.camera.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.lifecycle.HiltViewModel
