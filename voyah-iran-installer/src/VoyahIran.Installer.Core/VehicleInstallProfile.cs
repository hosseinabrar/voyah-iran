namespace VoyahIran.Installer.Core;

/// Select platform mechanics independently of vehicle options and CAN capabilities.
public sealed record VehicleInstallProfile(string Name, bool PatchFramework, bool StreamApk = false, bool LegacyFramework = false)
{
    public static VehicleInstallProfile Detect(string sdkText, string systemFingerprint, string vendorFingerprint)
    {
        if (!int.TryParse(sdkText.Trim(), out var sdk))
            throw new InvalidDataException("نسخهٔ اندروید خودرو خوانده نشد؛ نصب شروع نشد.");
        var h97a = System.Text.RegularExpressions.Regex.IsMatch(systemFingerprint + " " + vendorFingerprint,
            @"(?i)(?:^|[^a-z0-9])h97a(?:$|[^a-z0-9])");
        // This H97A firmware uses the product/device identity instead of an H97A build label.
        // Match the complete fingerprint prefix, not arbitrary mentions of f0506h.
        h97a |= new[] { systemFingerprint, vendorFingerprint }.Any(fingerprint =>
            fingerprint.Trim().StartsWith("qti/f0506h/f0506h:9/", StringComparison.OrdinalIgnoreCase));
        // H97A SELinux denies system_server access to shell_data_file even with adbd root.
        // Streaming passes the APK through stdin rather than opening /data/local/tmp/app.apk.
        if (sdk == 28 && h97a) return new("H97A Android 9 (verified VDEX reconstruction)", true, StreamApk: true, LegacyFramework: true);
        if (sdk == 30) return new("Android 11 (exact framework verification required)", true);
        throw new InvalidDataException($"این ترکیب خودرو و اندروید هنوز پشتیبانی نشده است (SDK {sdk}). فایل‌های خودرو تغییر نکردند.");
    }
}
