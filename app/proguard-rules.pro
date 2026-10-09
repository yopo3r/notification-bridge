# R8 rules for release builds (minification and resource shrinking are on).
#
# The app has no reflection, JNI or serialization of its own, and AndroidX, Compose,
# DataStore and kotlinx-coroutines ship their own consumer rules, so nothing needs keeping.
# Add `-keep` rules here only if a release build crashes with a ClassNotFoundException or
# NoSuchMethodException that a debug build does not.

# Keep file names and line numbers so release crash traces can be read after deobfuscation.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
