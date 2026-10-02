# Neshan 14.13.0.6 (70794) compatibility — 2026-09-28

Input: Downloads/Neshan new.apk, SHA-256
02573f577f105cccf0f3b1d6453f9c1f8923a77c96351632c8bb97e98d430542.

Publication: schema 1, revision 3; retains profile 70789 unchanged.

## Direct navigation data
- Step model: a90.b. Accessors a()I distance, f()h50.h type, g()h50.d modifier,
  h()/i()String road/extra, j()Integer turn index. Constructor and backing fields
  match the former l10.b model.
- Update d90.i.b(int,double,List<a90.b>): subtracts travelled distance from
  step.a(); invoked by b90.b.h(int,double).
- Reset b90.b.b(List<h50.q>): replaces steps, resets selected index, calls h(0,0).
- BaseApplication.o is a static volatile iq0.e singleton.
  iq0.e.U() checks live route state instanceof zs0.d.a; D() returns its cr0.a
  navigation engine. Equivalent semantics to old BaseApplication.N / ki0.c.H/A.
- Maneuver enum names remain compatible (h50.h / h50.d).

## Free driving and voice destinations
All existing configured active/route/more resource IDs exist in public.xml.
Bottom navigation still uses bottomNavigationViewMain.
Resource strings retain “سایر”, “ورود به حالت رانندگی”, “حالت رانندگی آزاد”,
“رانندگی”, “شروع”. Manifest still accepts geo ACTION_VIEW intents.
Thus existing fixed two-stage Driving/Start automation and configurable route
verification IDs require no client code changes for this version.

## Validation and limits
Verified exact APK hash, method descriptors and singleton fields against extracted
smali; UI IDs/labels and geo intent against decoded resources/manifest.
Verified signed envelope against the public key embedded in the Android client.
Current embedded hook state/lifecycle regression tests passed.
No device injection, actual UI clicks or car cluster test was performed.
Upload deployment/configs/neshan.json to /var/www/html/configs/neshan.json.
