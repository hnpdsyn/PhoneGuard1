# SimGuard ProGuard Rules
-keep class com.phonGuard.simguard.core.** { *; }
-keepclassmembers class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }