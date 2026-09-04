# PhoneGuard ProGuard Rules
-keep class com.phonGuard.model.** { *; }
-keep class com.phonGuard.core.** { *; }
-keepclassmembers class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }