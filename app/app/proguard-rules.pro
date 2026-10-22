# Keep generated serializers for the published JSON payloads.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.nankki.skydaily.** {
    *** Companion;
}
-keepclasseswithmembers class com.nankki.skydaily.** {
    kotlinx.serialization.KSerializer serializer(...);
}
