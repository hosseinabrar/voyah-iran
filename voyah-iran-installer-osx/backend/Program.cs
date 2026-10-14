using System.Text.Json;
using System.Runtime.InteropServices;
using VoyahIran.Installer.Core;
using VoyahIran.Installer.Mac;

if (args.Length > 0 && args[0] == "required-files") {
    Console.WriteLine(JsonSerializer.Serialize(PayloadBundle.RequiredFiles(args[1]))); return;
}
if (args.Length > 0 && args[0] == "verify-payload") {
    using var input = File.OpenRead(args[1]);
    var verified = PayloadBundle.Extract(input, args[2]);
    Console.WriteLine(JsonSerializer.Serialize(verified.Manifest)); return;
}
if (args.Length != 3 || args[0] != "serve") throw new ArgumentException("serve PAYLOAD_ZIP DATA_DIRECTORY");
Console.InputEncoding = System.Text.Encoding.UTF8;
Console.OutputEncoding = new System.Text.UTF8Encoding(false);
var outputGate = new object();
void Emit(object value) { lock (outputGate) Console.WriteLine(JsonSerializer.Serialize(value)); }
var dataRoot = Path.GetFullPath(args[2]);
Directory.CreateDirectory(dataRoot);
using var instanceLock = new FileStream(Path.Combine(dataRoot, "session.lock"), FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None);
using var log = new SessionLog(Path.Combine(dataRoot, "Logs"));
log.LineAdded += text => Emit(new { type = "log", text });
PayloadBundle? bundle = null;
try {
    using var input = File.OpenRead(args[1]);
    bundle = PayloadBundle.Extract(input, Path.Combine(dataRoot, "Cache", Guid.NewGuid().ToString("N")));
    var runtime = RuntimeInformation.ProcessArchitecture == Architecture.Arm64 ? "osx-arm64" : "osx-x64";
    if (!OperatingSystem.IsMacOS() || bundle.Manifest.TargetRuntime != runtime) throw new InvalidDataException("معماری باندل با این نسخهٔ نصاب مطابقت ندارد.");
    Emit(new { type = "ready", version = bundle.Manifest.AppVersion, log = log.FilePath, dataRoot });
} catch (Exception e) { Emit(new { type = "fatal", text = "باندل نصب آماده نیست: " + e.Message }); return; }
var adbPath = bundle.File("adb");
var adb = new AdbClient(adbPath, log);
LogcatCapture? capture = null;
CanTraceCapture? can = null;
string? permissionApk = null;
string? permissionSerial = null;
try {
    while (await Console.In.ReadLineAsync() is { } line) {
        string command = "";
        try {
            using var doc = JsonDocument.Parse(line);
            var request = doc.RootElement;
            command = request.GetProperty("command").GetString() ?? "";
            string Text(string key) => request.GetProperty(key).GetString() ?? throw new ArgumentException(key);
            if (command == "scan") {
                var r = await adb.RunAsync(null, ["devices", "-l"], 15, true);
                if (r.ExitCode != 0) throw new IOException(r.Output);
                var devices = DeviceParser.Parse(r.Output);
                Emit(new { type = "devices", text = DeviceParser.Describe(devices), serial = DeviceParser.SingleReady(devices)?.Serial });
            } else if (command == "password") {
                Emit(new { type = "message", text = "رمز امروز منوی مهندسی: " + EngineeringPassword.ForInstant(DateTimeOffset.UtcNow) });
            } else if (command == "stop-log") {
                if (capture == null) throw new InvalidOperationException("گزارش‌گیری فعال نیست.");
                var stopped = await capture.StopAsync();
                Emit(new { type = "file", path = capture.FilePath, text = stopped.Error ?? "گزارش ذخیره شد" });
                capture.Dispose(); capture = null;
            } else if (command == "stop-can") {
                if (can == null) throw new InvalidOperationException("شنود فعال نیست.");
                await can.StopAsync(); Emit(new { type = "file", path = can.FilePath, text = "گزارش CAN ذخیره شد" }); can = null;
            } else if (command == "mark-can") {
                if (can == null) throw new InvalidOperationException("شنود فعال نیست.");
                await can.MarkAsync();
            } else {
                if (capture != null || can != null) throw new InvalidOperationException("ابتدا گزارش‌گیری جاری را پایان دهید.");
                var serial = Text("serial");
                var discovery = await adb.RunAsync(null, ["devices", "-l"], 15, true);
                if (discovery.ExitCode != 0 || DeviceParser.SingleReady(DeviceParser.Parse(discovery.Output))?.Serial != serial)
                    throw new IOException("اتصال خودرو تغییر کرده است؛ فقط یک خودرو باید متصل باشد.");
                switch (command) {
                    case "install":
                        var engine = new InstallEngine(adb, bundle, log, Path.Combine(dataRoot, "Backups"));
                        engine.Progress += p => Emit(new { type = "phase", phase = (int)p.Phase, state = p.State.ToString() });
                        await engine.RunAsync(serial);
                        Emit(new { type = "message", text = "نصب و بررسی نهایی با موفقیت انجام شد." });
                        break;
                    case "install-apk":
                    case "install-bundled": {
                        var paths = command == "install-apk" ? new[] { Text("path") } : Text("app") switch {
                            "gboard" => new[] { bundle.File("apps/gboard.apk"), bundle.File("apps/gboard-config.xhdpi.apk") },
                            "neshan" => new[] { bundle.File("apps/neshan.apk") },
                            "myket" => new[] { bundle.File("apps/myket.apk") },
                            _ => throw new ArgumentException("Unknown bundled app")
                        };
                        var staging = Path.Combine(dataRoot, "ApkStaging", Guid.NewGuid().ToString("N"));
                        Directory.CreateDirectory(staging);
                        paths = paths.Select((p, i) => { var dest = Path.Combine(staging, $"{i}.apk"); File.Copy(p, dest); return dest; }).ToArray();
                        var info = ApkInfo.Read(paths[0]);
                        if (info.Package == "com.abrar.voyahiran") throw new InvalidOperationException("برای وویا ایران از نصب کامل استفاده کنید تا واسط نیز به‌روز شود.");
                        var installer = new ApkInstaller(adb, log, serial);
                        if (paths.Length == 1) await installer.InstallAsync(paths[0], info);
                        else await installer.InstallSplitAsync(paths, info);
                        permissionApk = paths[0];
                        permissionSerial = serial;
                        await Permissions(installer, info);
                        break;
                    }
                    case "permissions":
                    case "set-permission": {
                        if (permissionApk == null || permissionSerial != serial) throw new InvalidOperationException("ابتدا یک APK روی همین خودرو نصب کنید.");
                        var info = ApkInfo.Read(permissionApk);
                        var installer = new ApkInstaller(adb, log, serial);
                        if (command == "set-permission") {
                            var index = request.GetProperty("index").GetInt32();
                            if (index < 0 || index >= ApkInstaller.Choices.Length || !ApkInstaller.Available(info, ApkInstaller.Choices[index]))
                                throw new InvalidOperationException("این مجوز برای برنامه قابل تغییر نیست.");
                            await installer.SetAsync(info, [ApkInstaller.Choices[index]], request.GetProperty("enabled").GetBoolean());
                        }
                        await Permissions(installer, info); break;
                    }
                    case "apps":
                        Emit(new { type = "apps", apps = await new AppRemoval(adb, log, serial, bundle.File("app.apk")).ListAsync() }); break;
                    case "remove":
                        await new AppRemoval(adb, log, serial, bundle.File("app.apk")).RemoveAsync(Text("package")); break;
                    case "launch":
                        await Shell(serial, "am", "start", "--user", "0", "-n", "com.abrar.voyahiran/.ui.MainActivity"); break;
                    case "clock": await MacOperations.SetClock(adb, serial); break;
                    case "reboot": await new DeviceRebooter(adb, log).RebootAsync(serial); break;
                    case "start-log":
                        capture = await LogcatCapture.StartAsync(adbPath, serial, ReportPath("logcat"), adb);
                        var logSession = capture;
                        _ = Task.Run(async () => {
                            var ended = await logSession.Completion;
                            if (!ended.RequestedStop) Emit(new { type = "capture-exited", text = "گزارش‌گیری Logcat قطع شد؛ فایل ناقص حفظ شده است. برای ذخیره و آزادکردن عملیات، پایان گزارش‌گیری را بزنید." });
                        });
                        Emit(new { type = "capture", kind = "log", active = true }); break;
                    case "start-can":
                        can = await CanTraceCapture.StartAsync(adb, adbPath, serial, bundle.File("roa/frida-inject"), ReportPath("can"));
                        var canSession = can;
                        _ = Task.Run(async () => {
                            try { await canSession.Completion; }
                            catch (Exception e) { log.Write(e.Message); }
                            if (ReferenceEquals(can, canSession)) Emit(new { type = "capture-exited", text = "شنود CAN پایان یافت. برای ذخیرهٔ گزارش و آزادکردن عملیات، پایان شنود را بزنید." });
                        });
                        Emit(new { type = "capture", kind = "can", active = true }); break;
                    default: throw new ArgumentException("Unknown command");
                }
            }
            Emit(new { type = "done", command, ok = true });
        } catch (Exception e) {
            log.Write(e.ToString()); Emit(new { type = "error", text = e.Message });
            Emit(new { type = "done", command, ok = false });
        }
    }
} finally {
    if (capture != null) { await capture.StopAsync(); capture.Dispose(); }
    if (can != null) await can.StopAsync();
}
string ReportPath(string prefix) {
    var dir = Path.Combine(dataRoot, "Reports"); Directory.CreateDirectory(dir);
    return Path.Combine(dir, $"{prefix}-{DateTime.Now:yyyyMMdd-HHmmss}-{Guid.NewGuid():N}.txt");
}
async Task Shell(string serial, params string[] command) {
    var r = await adb.RunAsync(serial, ["shell", .. command]);
    if (r.ExitCode != 0 || System.Text.RegularExpressions.Regex.IsMatch(r.Output, "Error|Exception|denied", System.Text.RegularExpressions.RegexOptions.IgnoreCase)) throw new IOException(r.Output);
}
async Task Permissions(ApkInstaller installer, ApkInfo info) {
    var states = new List<object>();
    for (var i = 0; i < ApkInstaller.Choices.Length; i++) {
        var choice = ApkInstaller.Choices[i]; var available = ApkInstaller.Available(info, choice);
        bool? enabled = null;
        if (available) { try { enabled = await installer.ReadAsync(info, choice); } catch (Exception e) { log.Write(e.Message); } }
        states.Add(new { index = i, title = choice.Title, available, enabled });
    }
    Emit(new { type = "permissions", package = info.Package, permissions = states });
}
