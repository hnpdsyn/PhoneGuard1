# AppLock ProGuard Rules
-keep class com.phonGuard.applock.core.** { *; }
-keepclassmembers class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }