# Neshan UI contracts, revision 6

APK SHA-256 values were compared against the Downloads files and all three matched the existing profiles.

| Version | APK | promotion_banner_layout | Takeover FrameLayout |
|---|---|---|---|
| 14.12.0.3 / 70789 | Neshan.apk | 0x7f0a076f | y11.p |
| 14.13.0.6 / 70794 | Neshan new.apk | 0x7f0a077d | w91.p |
| 14.14.0.4 / 70799 | neshan new 2.apk | 0x7f0a0784 | bb1.p |

Verified public.xml, fragment_main_page.xml and takeover_layout.xml for each APK.
Takeover layout references: 70789 g11.e.b -> y11.p$d; 70794 e91.e.b -> w91.p$d;
70799 ja1.e.b -> bb1.p$d. Each corresponding p class extends FrameLayout and sets
its own visibility in q(state), independently of the map controls.

All three use Setting3 / MapLightMode integer 3 for Android theme.
70789: o80.b reads/writes MapLightMode; p80.a enum ANDROID_SETTING=3;
ya0.s$k -> x80.x -> x80.h.p -> x80.a0.L -> Setting3.
70794 and 70799 theme contracts were verified in the preceding implementation.

No on-vehicle validation performed. Config contains selectors only; no APK patching.
