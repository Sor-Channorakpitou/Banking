# kotlinx.serialization: keep generated serializers of our API models.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.example.bank.mobile.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.bank.mobile.**$$serializer { *; }

# Retrofit interfaces are used via reflection.
-keep interface com.example.bank.mobile.data.BankApi { *; }
