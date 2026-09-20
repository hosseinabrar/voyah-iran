# Root entry point is launched by app_process outside Android component discovery.
-keep class com.abrar.voyahiran.core.bridge.BridgeServer {
    public static void main(java.lang.String[]);
}
# The installer and fixed shell commands address these components by literal name.
-keepnames class com.abrar.voyahiran.** extends android.app.Activity
-keepnames class com.abrar.voyahiran.** extends android.app.Service
-keepnames class com.abrar.raha.feedback.RahaOverlayService
# Native speech interfaces use JNI/JNA names and reflective native structures.
# Preserve vendor bindings, not our recognizer or execution implementation.
-keep class com.sun.jna.** { *; }
# JNA ships optional desktop AWT helpers. Android/Vosk never calls them.
# Suppress only these unavailable desktop types, not other missing dependencies.
-dontwarn java.awt.Component
-dontwarn java.awt.GraphicsEnvironment
-dontwarn java.awt.HeadlessException
-dontwarn java.awt.Window
-keep class org.vosk.** { *; }
-keep class com.k2fsa.sherpa.onnx.** { *; }
# Keep stack traces retraceable with the private per-release mapping file.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# DPT protects Raha by mapped class ownership. Do not inline/merge recognizer
# implementations into bridge/framework classes that must run before DPT loads.
# Names can still be obfuscated. This is NOT a package-wide keep of the app.
-keep,allowobfuscation class com.abrar.raha.RahaCommandRecognizer** { *; }
-keep,allowobfuscation class com.abrar.raha.RahaPhase*Recognizer** { *; }
-keep,allowobfuscation class com.abrar.raha.Raha*Vocabulary** { *; }

-keep class com.abrar.voyahiran.core.bridge.AppInventory { public static void main(java.lang.String[]); }

-keepnames class com.abrar.voyahiran.feature.media.SteeringMediaListener
