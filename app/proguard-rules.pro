-keepattributes Signature
-keepattributes *Annotation*
-keep class cn.ahlib.reservation.data.** { *; }
-keep class cn.ahlib.reservation.update.GitHubRelease { *; }
-keep class cn.ahlib.reservation.update.GitHubReleaseAsset { *; }
# ML Kit instantiates its ComponentRegistrar implementations via reflection
# (MlKitComponentDiscoveryService manifest metadata). Without this rule R8
# strips their no-arg constructors, component discovery silently fails, and
# the scanner screen crashes on BarcodeScanning.getClient() with an NPE in
# release builds. Do not remove (broke scanning in v1.0.0-main.8).
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    public <init>();
}
-dontwarn javax.annotation.**
