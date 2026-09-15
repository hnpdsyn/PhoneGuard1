# AdbGuard ProGuard Rules
-keep class com.phonGuard.adbguard.core.** { *; }
-keepclassmembers class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }