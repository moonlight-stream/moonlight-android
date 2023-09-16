# Don't obfuscate code
-dontobfuscate

# Idk what this does
-keepattributes *Annotation*


# Our code
-keep class com.limelight.binding.input.evdev.* {*;}

# Moonlight common
-keep class com.limelight.nvstream.jni.* {*;}

# Okio
-keep class sun.misc.Unsafe {*;}
-dontwarn java.nio.file.*
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn okio.**

# BouncyCastle
-keep class org.bouncycastle.jcajce.provider.asymmetric.* {*;}
-keep class org.bouncycastle.jcajce.provider.asymmetric.util.* {*;}
-keep class org.bouncycastle.jcajce.provider.asymmetric.rsa.* {*;}
-keep class org.bouncycastle.jcajce.provider.digest.** {*;}
-keep class org.bouncycastle.jcajce.provider.symmetric.** {*;}
-keep class org.bouncycastle.jcajce.spec.* {*;}
-keep class org.bouncycastle.jce.** {*;}
# Shaga added
-keep class org.bouncycastle.jsse.BCSSLParameters {*;}
-keep class org.bouncycastle.jsse.BCSSLSocket {*;}
-keep class org.bouncycastle.jsse.provider.BouncyCastleJsseProvider {*;}
-dontwarn javax.naming.**

# jMDNS
-dontwarn javax.jmdns.impl.DNSCache
-dontwarn org.slf4j.**

# Conscript
-keep class org.conscrypt.Conscrypt$Version {*;}
-keep class org.conscrypt.Conscrypt {*;}
-keep class org.conscrypt.ConscryptHostnameVerifier {*;}

# OpenJsse
-keep class org.openjsse.javax.net.ssl.SSLParameters {*;}
-keep class org.openjsse.javax.net.ssl.SSLSocket {*;}
-keep class org.openjsse.net.ssl.OpenJSSE {*;}

# Additional try
-keep class okhttp3.internal.platform.** { *; }
-keep class com.squareup.okhttp3.** { *; }
-keep class org.bouncycastle.** { *; }
-keep class org.conscrypt.** { *; }
-keep class org.openjsse.** { *; }

# These rules shouldn't be applied:
# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE