# ProGuard/R8 rules for :app.
# Minification is disabled for all build types (debug-only distribution),
# so these only matter once shrinking is enabled. Firebase ships its own consumer rules.

# CrashGuard SDK: closed-source and must reach its crash activity + handler internals by
# name after a fatal. Kept wholesale; it is tiny. Same rule as the other PIP apps.
-keep class crashguard.android.library.** { *; }
-dontwarn crashguard.android.library.**
