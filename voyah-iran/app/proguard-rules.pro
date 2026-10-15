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

# Retain recognizer implementations used to construct runtime vocabularies.
# Names can still be obfuscated; application-wide boundaries follow below.
-keep,allowobfuscation class com.abrar.raha.RahaCommandRecognizer** { *; }
-keep,allowobfuscation class com.abrar.raha.RahaNavigationRecognizer** { *; }
-keep,allowobfuscation class com.abrar.raha.RahaPhase*Recognizer** { *; }
-keep,allowobfuscation class com.abrar.raha.Raha*Vocabulary** { *; }

-keep class com.abrar.voyahiran.core.bridge.AppInventory { public static void main(java.lang.String[]); }

-keepnames class com.abrar.voyahiran.feature.media.SteeringMediaListener

# Preserve protection ownership boundaries without preserving internal names.
# R8 may remove unused code and rename classes/members. Disabling optimization
# also prevents generated lambda holders from merging across the root/DPT boundary;
# package keep rules alone do not cover R8's newly generated shared holders.
-dontoptimize
-keep,allowshrinking,allowobfuscation class com.abrar.** { *; }
# JNA resolves these custom native interfaces and their method names reflectively.
-keep interface * extends com.sun.jna.Library { *; }

# The offline passenger document calls this method by its literal JavaScript name.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
