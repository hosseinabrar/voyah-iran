import SwiftUI
import AppKit
import UniformTypeIdentifiers

struct PermissionItem: Identifiable {
    let id: Int
    let title: String
    let available: Bool
    let enabled: Bool?
}
struct VehicleApp: Identifiable {
    var id: String { package }
    let package: String
    let label: String
}
@MainActor final class InstallerModel: ObservableObject {
    @Published var ready = false
    @Published var busy = false
    @Published var connection = "در حال آماده‌سازی…"
    @Published var version = ""
    @Published var serial: String?
    @Published var logs = ""
    @Published var message = ""
    @Published var showMessage = false
    @Published var phases = Array(repeating: "", count: 8)
    @Published var permissions: [PermissionItem] = []
    @Published var apps: [VehicleApp] = []
    @Published var showApps = false
    @Published var showPermissions = false
    @Published var capture = ""
    @Published var selectedPackage = ""
    let phaseTitles = ["شناسایی خودرو", "روت و بکاپ", "ساخت و بررسی پچ", "نصب برنامه", "نصب واسط", "نصب پچ سیستم", "راه‌اندازی مجدد", "بررسی نهایی"]
    var process: Process?
    var input: FileHandle?
    var timer: Timer?
    var dataURL: URL
    private var buffer = Data()
    private var errorBuffer = Data()
    var canOperate: Bool { ready && !busy && serial != nil && capture.isEmpty }
    init() {
        if let custom = ProcessInfo.processInfo.environment["VI_MAC_DATA_ROOT"] { dataURL = URL(fileURLWithPath: custom) }
        else { dataURL = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("VoyahIran/Installer") }
    }
    func start() {
        guard process == nil else { return }
        guard let resources = Bundle.main.resourceURL else { fail("فایل‌های برنامه پیدا نشد."); return }
        let backend = resources.appendingPathComponent("backend/VoyahIran.Installer.Mac")
        let payload = resources.appendingPathComponent("bundle.zip")
        guard FileManager.default.fileExists(atPath: backend.path), FileManager.default.fileExists(atPath: payload.path) else {
            connection = "نسخهٔ توسعه — باندل نصب هنوز ساخته نشده"
            logs = "برای ساخت نسخهٔ قابل نصب، Publish.command را اجرا کنید. این نسخه فرمانی به خودرو نمی‌فرستد.\n"
            return
        }
        let child = Process(), stdin = Pipe(), stdout = Pipe(), stderr = Pipe()
        child.executableURL = backend
        child.arguments = ["serve", payload.path, dataURL.path]
        child.standardInput = stdin; child.standardOutput = stdout; child.standardError = stderr
        stdout.fileHandleForReading.readabilityHandler = { [weak self] handle in
            let bytes = handle.availableData
            if !bytes.isEmpty { Task { @MainActor in self?.receive(bytes) } }
        }
        stderr.fileHandleForReading.readabilityHandler = { [weak self] handle in
            let bytes = handle.availableData
            if !bytes.isEmpty { Task { @MainActor in self?.receiveError(bytes) } }
        }
        child.terminationHandler = { [weak self] p in
            Task { @MainActor in
                guard let self else { return }
                self.ready = false; self.busy = false; self.timer?.invalidate()
                self.connection = "ارتباط با موتور نصب پایان یافت (\(p.terminationStatus))"
                if !self.errorBuffer.isEmpty { self.logs += String(decoding: self.errorBuffer, as: UTF8.self) }
            }
        }
        do { try child.run(); process = child; input = stdin.fileHandleForWriting }
        catch { fail(error.localizedDescription) }
        timer = Timer.scheduledTimer(withTimeInterval: 3, repeats: true) { [weak self] _ in
            Task { @MainActor in guard let self, self.ready, !self.busy else { return }; self.send("scan") }
        }
    }
    func receiveError(_ bytes: Data) { errorBuffer.append(bytes) }
    func receive(_ bytes: Data) {
        buffer.append(bytes)
        while let index = buffer.firstIndex(of: 10) {
            let line = buffer.prefix(upTo: index); buffer.removeSubrange(...index)
            guard let event = try? JSONSerialization.jsonObject(with: line) as? [String: Any], let type = event["type"] as? String else { continue }
            switch type {
            case "ready": ready = true; version = event["version"] as? String ?? ""; send("scan")
            case "devices": connection = event["text"] as? String ?? ""; serial = event["serial"] as? String
            case "log":
                logs += event["text"] as? String ?? ""
                if logs.count > 120_000 { logs = String(logs.suffix(100_000)) }
            case "phase":
                if let i = event["phase"] as? Int, phases.indices.contains(i) { phases[i] = event["state"] as? String ?? "" }
            case "done":
                busy = false
                if event["ok"] as? Bool == true, let command = event["command"] as? String {
                    if command == "stop-log" || command == "stop-can" { capture = "" }
                    if command == "remove" { send("apps") }
                }
            case "fatal": ready = false; connection = event["text"] as? String ?? ""; fail(connection)
            case "error", "message": fail(event["text"] as? String ?? "")
            case "capture": capture = event["kind"] as? String ?? ""
            case "file":
                if let path = event["path"] as? String { NSWorkspace.shared.activateFileViewerSelecting([URL(fileURLWithPath: path)]) }
            case "permissions":
                selectedPackage = event["package"] as? String ?? ""
                permissions = (event["permissions"] as? [[String: Any]] ?? []).compactMap { p in
                    guard let i = p["index"] as? Int, let title = p["title"] as? String else { return nil }
                    return PermissionItem(id: i, title: title, available: p["available"] as? Bool ?? false, enabled: p["enabled"] as? Bool)
                }; showPermissions = true
            case "apps":
                apps = (event["apps"] as? [[String: Any]] ?? []).compactMap { a in
                    guard let package = a["Package"] as? String else { return nil }
                    return VehicleApp(package: package, label: a["Label"] as? String ?? package)
                }; showApps = true
            default: break
            }
        }
    }
    func fail(_ text: String) { message = text; showMessage = true }
    func send(_ command: String, extra: [String: Any] = [:]) {
        guard ready, !busy else { return }
        var request = extra; request["command"] = command; request["serial"] = serial
        do {
            var data = try JSONSerialization.data(withJSONObject: request); data.append(10)
            guard let input else { return }; busy = true
            try input.write(contentsOf: data)
        } catch { busy = false; fail(error.localizedDescription) }
    }
    func confirm(_ title: String, text: String, command: String, extra: [String: Any] = [:]) {
        let alert = NSAlert(); alert.messageText = title; alert.informativeText = text
        alert.addButton(withTitle: "ادامه"); alert.addButton(withTitle: "انصراف")
        if alert.runModal() == .alertFirstButtonReturn {
            if command == "install" { phases = Array(repeating: "", count: 8) }
            send(command, extra: extra)
        }
    }
    func chooseApk() {
        let panel = NSOpenPanel(); panel.allowsMultipleSelection = false
        panel.allowedContentTypes = [UTType(filenameExtension: "apk") ?? .data]
        if panel.runModal() == .OK, let url = panel.url { send("install-apk", extra: ["path": url.path]) }
    }
    func openLogs() {
        try? FileManager.default.createDirectory(at: dataURL, withIntermediateDirectories: true)
        NSWorkspace.shared.open(dataURL)
    }
}

@MainActor final class AppDelegate: NSObject, NSApplicationDelegate, NSWindowDelegate {
    weak var model: InstallerModel?
    func applicationShouldTerminate(_ sender: NSApplication) -> NSApplication.TerminateReply {
        if model?.busy == true || !(model?.capture.isEmpty ?? true) {
            model?.fail("عملیات در حال اجراست. تا پایان نصب یا پایان گزارش‌گیری برنامه را نبندید.")
            return .terminateCancel
        }
        try? model?.input?.close(); return .terminateNow
    }
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool { true }
    func windowShouldClose(_ sender: NSWindow) -> Bool {
        if model?.busy == true || !(model?.capture.isEmpty ?? true) {
            model?.fail("تا پایان عملیات یا پایان گزارش‌گیری، پنجره را باز نگه دارید.")
            return false
        }
        return true
    }
}

@main struct VoyahInstallerApp: App {
    @StateObject private var model = InstallerModel()
    @NSApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    var body: some Scene {
        WindowGroup("نصاب وویا ایران") {
            InstallerView(model: model).environment(\.layoutDirection, .rightToLeft)
                .frame(minWidth: 880, minHeight: 680)
                .onAppear {
                    delegate.model = model; model.start()
                    DispatchQueue.main.async { NSApplication.shared.windows.first?.delegate = delegate }
                }
        }.commands { CommandGroup(replacing: .newItem) {} }
    }
}
struct InstallerView: View {
    @ObservedObject var model: InstallerModel
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Image(systemName: "car.side.fill").font(.largeTitle).foregroundStyle(.cyan)
                VStack(alignment: .leading) { Text("وویا ایران").font(.largeTitle.bold()); Text("نصاب macOS  \(model.version)").foregroundStyle(.secondary) }
                Spacer()
                if model.busy { ProgressView().controlSize(.small) }
                Label(model.connection, systemImage: model.serial == nil ? "cable.connector" : "checkmark.circle.fill").font(.callout)
            }
            HStack {
                Button("نصب وویا ایران") { model.confirm("نصب وویا ایران", text: "برنامه، واسط و پچ متناسب با خودرو نصب می‌شوند و نمایشگر راه‌اندازی مجدد می‌شود. کابل را تا پایان جدا نکنید.", command: "install") }
                    .buttonStyle(.borderedProminent)
                Button("نصب APK…") { model.chooseApk() }
                Button("برنامه‌های قابل حذف") { model.send("apps") }
                Button("بازکردن وویا ایران") { model.send("launch") }
            }.disabled(!model.canOperate)
            HStack {
                ForEach([("نشان", "neshan"), ("مایکت", "myket"), ("کیبورد گوگل", "gboard")], id: \.1) { title, app in
                    Button("نصب \(title)") { model.send("install-bundled", extra: ["app": app]) }
                }
                Button("تنظیم تاریخ و ساعت") { model.confirm("تنظیم ساعت", text: "ساعت خودرو با این مک هماهنگ و زمان خودکار خاموش می‌شود.", command: "clock") }
                Button("راه‌اندازی مجدد") { model.confirm("راه‌اندازی مجدد نمایشگر", text: "نمایشگر خودرو دوباره راه‌اندازی می‌شود.", command: "reboot") }
            }.disabled(!model.canOperate)
            Divider()
            HStack {
                Button(model.capture == "log" ? "پایان گزارش‌گیری" : "گزارش Logcat") { model.send(model.capture == "log" ? "stop-log" : "start-log") }
                    .disabled(model.busy || !model.ready || (model.capture != "log" && !model.canOperate))
                Button(model.capture == "can" ? "پایان شنود CAN" : "شنود CAN") { model.send(model.capture == "can" ? "stop-can" : "start-can") }
                    .disabled(model.busy || !model.ready || (model.capture != "can" && !model.canOperate))
                Button("علامت در گزارش CAN") { model.send("mark-can") }.disabled(model.capture != "can" || model.busy)
                Button("رمز منوی مهندسی") { model.send("password") }.disabled(!model.ready || model.busy)
                Spacer()
                Button("گزارش‌ها و بکاپ‌ها") { model.openLogs() }
            }
            if model.phases.contains(where: { !$0.isEmpty }) {
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], alignment: .leading) {
                    ForEach(0..<8, id: \.self) { i in
                        Label(model.phaseTitles[i], systemImage: model.phases[i] == "Succeeded" ? "checkmark.circle.fill" : model.phases[i] == "Failed" ? "xmark.circle.fill" : model.phases[i] == "Running" ? "arrow.triangle.2.circlepath" : "circle")
                            .foregroundStyle(model.phases[i] == "Failed" ? .red : model.phases[i] == "Succeeded" ? .green : .primary)
                    }
                }.padding(12).background(.quaternary, in: RoundedRectangle(cornerRadius: 12))
            }
            HStack { Text("گزارش عملیات").font(.headline); Spacer(); Button("کپی") { NSPasteboard.general.clearContents(); NSPasteboard.general.setString(model.logs, forType: .string) } }
            ScrollView { Text(model.logs.isEmpty ? "گزارش عملیات اینجا نمایش داده می‌شود." : model.logs).font(.system(size: 11, design: .monospaced)).textSelection(.enabled).frame(maxWidth: .infinity, alignment: .leading) }
                .environment(\.layoutDirection, .leftToRight).padding(12).background(.background, in: RoundedRectangle(cornerRadius: 12))
            Text("USB debugging را فعال کنید و فقط یک خودرو را با کابل متصل نگه دارید.").font(.footnote).foregroundStyle(.secondary)
        }.padding(24).background(Color(nsColor: .windowBackgroundColor))
            .alert("وویا ایران", isPresented: $model.showMessage) { Button("باشه", role: .cancel) {} } message: { Text(model.message) }
            .sheet(isPresented: $model.showApps) {
                VStack { Text("حذف برنامه").font(.title2); List(model.apps) { app in HStack { VStack(alignment: .leading) { Text(app.label); Text(app.package).font(.caption).foregroundStyle(.secondary) }; Spacer(); Button("حذف", role: .destructive) { model.confirm("حذف \(app.label)", text: "برنامه و داده‌های آن حذف می‌شوند.", command: "remove", extra: ["package": app.package]) }.disabled(!model.canOperate) } }; Button("بستن") { model.showApps = false }.disabled(model.busy) }.padding().frame(width: 650, height: 450)
            }
            .sheet(isPresented: $model.showPermissions) {
                VStack(alignment: .leading, spacing: 16) {
                    Text("مجوزهای برنامه").font(.title2); Text(model.selectedPackage).font(.caption)
                    ForEach(model.permissions) { p in
                        Toggle(p.title + (p.available && p.enabled == nil ? " — وضعیت نامشخص" : ""), isOn: Binding(get: { p.enabled ?? false }, set: { value in model.send("set-permission", extra: ["index": p.id, "enabled": value]) }))
                            .disabled(!model.canOperate || !p.available || p.enabled == nil)
                    }
                    Button("بازخوانی وضعیت") { model.send("permissions") }.disabled(!model.canOperate)
                    Button("بستن") { model.showPermissions = false }.disabled(model.busy)
                }.padding(28).frame(width: 420)
            }
    }
}
