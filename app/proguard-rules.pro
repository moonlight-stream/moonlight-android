# Don't obfuscate code
-dontobfuscate

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
-keep class org.bouncycastle.crypto.* {*;}
-keep class org.bouncycastle.crypto.agreement.** {*;}
-keep class org.bouncycastle.crypto.digests.* {*;}
-keep class org.bouncycastle.crypto.ec.* {*;}
-keep class org.bouncycastle.crypto.encodings.* {*;}
-keep class org.bouncycastle.crypto.engines.* {*;}
-keep class org.bouncycastle.crypto.macs.* {*;}
-keep class org.bouncycastle.crypto.modes.* {*;}
-keep class org.bouncycastle.crypto.paddings.* {*;}
-keep class org.bouncycastle.crypto.params.* {*;}
-keep class org.bouncycastle.crypto.prng.* {*;}
-keep class org.bouncycastle.crypto.signers.* {*;}

-keep class org.bouncycastle.jcajce.provider.asymmetric.* {*;}
-keep class org.bouncycastle.jcajce.provider.asymmetric.util.* {*;}
-keep class org.bouncycastle.jcajce.provider.asymmetric.dh.* {*;}
-keep class org.bouncycastle.jcajce.provider.asymmetric.ec.* {*;}
-keep class org.bouncycastle.jcajce.provider.asymmetric.rsa.* {*;}

-keep class org.bouncycastle.jcajce.provider.digest.** {*;}
-keep class org.bouncycastle.jcajce.provider.keystore.** {*;}
-keep class org.bouncycastle.jcajce.provider.symmetric.** {*;}
-keep class org.bouncycastle.jcajce.spec.* {*;}
-keep class org.bouncycastle.jce.** {*;}

-dontwarn javax.naming.**

# jMDNS
-dontwarn javax.jmdns.impl.DNSCache
