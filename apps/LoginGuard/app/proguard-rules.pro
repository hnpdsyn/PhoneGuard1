# LoginGuard ProGuard Rules
-keep class com.phonGuard.loginguard.core.** { *; }
-keepclassmembers class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }