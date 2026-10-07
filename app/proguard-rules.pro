# kotlinx.serialization: keep generated serializers for @Serializable save models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.cyberoperative.game.** {
    *** Companion;
}
-keepclasseswithmembers class com.cyberoperative.game.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.cyberoperative.game.save.**$$serializer { *; }
-keep class com.cyberoperative.game.save.** { *; }
