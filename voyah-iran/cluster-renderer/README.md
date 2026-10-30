# Cluster presentation helper

These Android-only widgets run inside the OEM instrumentcard process. Gradle
compiles them separately into `assets/cluster/renderer/classes.dex`; they are
not part of the application's classes.dex. Host tests compile the same sources.

The hook opens our package context with flags 0 (resources only), then loads
this small DEX using InMemoryDexClassLoader with the OEM loader as parent.
Never use our package's ClassLoader here: the protected APK's component factory
initializes its native shell, which cannot access our private directory from
the OEM UID. No private-directory permission changes are needed.

Only text layout, scrolling and image rounding live here. No subscriptions,
voice models, commands or vehicle control may be added. App protection remains
unchanged; the protection packager verifies all asset bytes are preserved.
