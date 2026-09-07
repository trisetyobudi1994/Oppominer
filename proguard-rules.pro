# Add project specific ProGuard rules here.
-keep class com.oppominer.service.** { *; }
-keepclassmembers class * {
    native <methods>;
}
