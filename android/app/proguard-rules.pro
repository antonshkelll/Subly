# kotlinx.serialization и Ktor приносят свои правила R8; здесь только то, чего им не хватает на Android.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn io.ktor.util.debug.**
-keepattributes *Annotation*, InnerClasses, Signature
-keep,includedescriptorclasses class ru.antonshkel.subly.data.**$$serializer { *; }
-keepclassmembers class ru.antonshkel.subly.data.** { *** Companion; kotlinx.serialization.KSerializer serializer(...); }
