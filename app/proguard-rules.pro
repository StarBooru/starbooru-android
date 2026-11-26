# UniFFI's bindings reach the core through JNA, which finds classes, fields
# and callbacks by name.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-dontwarn java.awt.**
-keep class eu.filipnone.starbooru.core.** { *; }
