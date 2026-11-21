# kotlinx.serialization models (library ships its own rules; these keep our DTOs and routes intact).
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keep,includedescriptorclasses class com.vknxworks.arrctrl.**$$serializer { *; }
-keepclassmembers class com.vknxworks.arrctrl.** {
    *** Companion;
}
-keepclasseswithmembers class com.vknxworks.arrctrl.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

-dontwarn javax.annotation.**

# Push from ntfy: the receiver is created by Android from its class name.
-keep class com.vknxworks.arrctrl.data.notify.PushReceiver { *; }
-keep class com.vknxworks.arrctrl.data.notify.RaiseToForegroundService { *; }

# Release builds drop debug and info logging entirely (ArrCtrl itself doesn't log; this also covers libraries).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# WorkManager keeps its job list in a Room 2.6.1 database, and Room creates the generated
# WorkDatabase_Impl by reflection through its no-argument constructor. Room 2.6.1's own rule
# (-keep class * extends androidx.room.RoomDatabase) names no members, and the R8 in Android
# Gradle Plugin 9 no longer keeps a constructor for such a rule, so without this line the app
# would crash at start (NoSuchMethodException: WorkDatabase_Impl.<init>). Same rule Room 2.7 ships.
-keep class * extends androidx.room.RoomDatabase { void <init>(); }
