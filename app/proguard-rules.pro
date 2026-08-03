# Xposed modules must not obfuscate the entry point.
-keep class com.fuckcospm.HookEntry { *; }
-keep class com.fuckcospm.WhiteListStripper { *; }
