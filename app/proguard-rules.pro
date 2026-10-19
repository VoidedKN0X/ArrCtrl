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

# UnifiedPush connector + Tink (Web Push decryption). Tink reads protobuf message fields reflectively.
-keep class org.unifiedpush.android.connector.** { *; }
-keepclassmembers class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite { <fields>; }
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**
