# Feature subscription policy

## Release branch preparation

When creating a new release branch, first align Android versionName and installer Version/InformationalVersion with the release number, and increment Android versionCode. Verify macOS bundle metadata and Windows output naming use the matching version sources. Do not build or commit unless requested.

Before implementing any newly requested feature, ask the user whether it belongs to the ordinary or VIP subscription unless they already specified its tier. Do not assume a tier for new features. Existing feature fixes retain their assigned tier.

VIP restrictions must preserve saved user preferences: mask unavailable controls without clearing stored settings, and resume those settings after a valid VIP entitlement returns. Enforce entitlement at runtime as well as in the UI.

# Installer platform parity

For every installer change, inspect both the Windows and macOS installer paths. Apply shared behavior, fixes, payload updates, and validation to both platforms, preferably through their shared core and patch sources. Platform-specific features (such as macOS Universal packaging) remain platform-specific; explicitly verify that they do not break the other installer. Distinguish source coverage from rebuilt/tested release artifacts; never claim a Windows runtime test based only on macOS validation.
