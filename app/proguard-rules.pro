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
