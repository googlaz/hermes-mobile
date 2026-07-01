# Add project-specific Proguard rules here.
# By default, keep Hilt and Room entities reflection intact.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Retrofit, OkHttp and Gson rules if obfuscated
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
