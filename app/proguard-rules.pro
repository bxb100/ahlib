-keepattributes Signature
-keepattributes *Annotation*
-keep class cn.ahlib.reservation.data.** { *; }
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    public <init>();
}
-dontwarn javax.annotation.**
