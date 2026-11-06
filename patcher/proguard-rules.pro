# apksig and ARSCLib reference JDK/desktop classes that don't exist on Android
-dontwarn javax.**
-dontwarn java.awt.**
-dontwarn org.xmlpull.**
-dontwarn org.slf4j.**

# apksig's ASN.1 DER encoder/parser reads @Asn1Class/@Asn1Field models by reflection (v1 signatures)
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keep @interface com.android.apksig.internal.asn1.** { *; }
-keep @com.android.apksig.internal.asn1.Asn1Class class * { <init>(); <fields>; }
