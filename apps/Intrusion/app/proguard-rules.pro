# Intrusion ProGuard Rules
-keep class com.phonGuard.intrusion.core.** { *; }
-keepclassmembers class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }