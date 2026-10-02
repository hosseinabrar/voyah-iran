# Neshan 14.14.0.4 (70799)

Input: Downloads/neshan new 2.apk
SHA-256: 7e17451029594040eeccedfbeb4ae97ea29eb5f035a88fef3dc44e6ebb095330
Publication: catalog revision 4; existing profiles preserved.

## Verified APK contract
- Step d90.b: a() int distance, f() k50.h type, g() k50.d modifier,
  h()/i() String road/extra, j() Integer turn index.
- g90.i.b(int,double,List<d90.b>) reads the current step and subtracts traveled distance.
- e90.b.b(List<k50.q>) rebuilds steps, resets selection and calls h(0,0).
- BaseApplication.n is static volatile ur0.e.
- ur0.e.U() checks the active navigation state (LiveData k instanceof lu0.c$a).
- ur0.e.D() returns navigation engine q (os0.a).
- Maneuver enum names retain their semantic names.
- All configured free-driving and routing resource IDs and Persian labels remain present.
- Manifest still accepts geo links. Route automation labels Driving/Start remain unchanged.

## Validation
APK hash, all hook signatures, route singleton, IDs, labels and geo intent checked.
Signed envelope verified using the Android embedded public key; payload matches source.
Production embedded hook state/lifecycle JavaScript tests passed.
No on-vehicle test performed. Upload deployment/configs/neshan.json to
/var/www/html/configs/neshan.json; no client code update required for this profile.
