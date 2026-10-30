import SwiftUI
import AppKit
import UniformTypeIdentifiers
import CoreText

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
    let icon: NSImage?
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
    @Published var progressPage = false
    @Published var progressTitle = "در حال نصب وویا ایران"
    @Published var progressSubtitle = "کابل را جدا نکنید؛ در پایان نمایشگر خودکار راه‌اندازی مجدد می‌شود."
    @Published var apkPath = ""
    @Published var apkTitle = "نصب APK"
    @Published var windows: [String: InstallerWindow] = [:]
    static var runtime: String {
        #if arch(arm64)
        return "osx-arm64"
        #elseif arch(x86_64)
        return "osx-x64"
        #else
        #error("Unsupported macOS architecture")
        #endif
    }
    // Fixture navigation is opt-in and only available in payload-free development builds.
    var uiPreview: Bool {
        ProcessInfo.processInfo.environment["VI_MAC_UI_PREVIEW"] == "1" &&
        !FileManager.default.fileExists(atPath: Bundle.main.resourceURL!.appendingPathComponent("payloads").path)
    }
    let phaseTitles = ["شناسایی خودرو", "روت، آماده‌سازی سیستم و بکاپ", "ساخت و بررسی پچ نمایش و کیبورد (ممکن است چند دقیقه طول بکشد)", "نصب برنامه", "نصب واسط دسترسی", "اصلاح services.jar", "راه‌اندازی مجدد خودرو", "بررسی نهایی نصب"]
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
        if uiPreview {
            ready = true; serial = "preview"; connection = "خودرو شناسایی شد"; version = "3.0.1"
            return
        }
        guard let resources = Bundle.main.resourceURL else { fail("فایل‌های برنامه پیدا نشد."); return }
        let backend = resources.appendingPathComponent("backend/\(Self.runtime)/VoyahIran.Installer.Mac")
        let payload = resources.appendingPathComponent("payloads/\(Self.runtime).zip")
        guard FileManager.default.fileExists(atPath: backend.path), FileManager.default.fileExists(atPath: payload.path) else {
            connection = "نسخهٔ توسعه — باندل نصب هنوز ساخته نشده"
            logs = "برای ساخت نسخهٔ قابل نصب، Publish.command را اجرا کنید. این نسخه فرمانی به خودرو نمی‌فرستد.\n"
            return
        }
        let child = Process(), stdin = Pipe(), stdout = Pipe(), stderr = Pipe()
        child.executableURL = backend
        child.arguments = ["serve", payload.path, dataURL.path,
                           resources.appendingPathComponent("payloads/common.zip").path]
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
                if event["command"] as? String == "install" {
                    let ok = event["ok"] as? Bool == true
                    progressTitle = ok ? "وویا ایران آماده است" : "نصب کامل نشد"
                    progressSubtitle = ok ? "نصب و اجرای واسط تأیید شد. برنامه روی نمایشگر خودرو باز شده است." : "گزارش را کپی و برای پشتیبانی ارسال کنید."
                }
                if event["ok"] as? Bool == true, let command = event["command"] as? String {
                    if command == "stop-log" || command == "stop-can" { capture = "" }
                    if command == "remove" { send("apps") }
                }
            case "fatal":
                ready = false; connection = event["text"] as? String ?? ""
                progressPage = true; progressTitle = "آماده‌سازی ناموفق بود"
                progressSubtitle = "گزارش را برای پشتیبانی ارسال کنید."; fail(connection)
            case "error", "message", "capture-exited": fail(event["text"] as? String ?? "")
            case "capture": capture = event["kind"] as? String ?? ""
            case "file":
                if let path = event["path"] as? String { NSWorkspace.shared.activateFileViewerSelecting([URL(fileURLWithPath: path)]) }
            case "permissions":
                selectedPackage = event["package"] as? String ?? ""
                permissions = (event["permissions"] as? [[String: Any]] ?? []).compactMap { p in
                    guard let i = p["index"] as? Int, let title = p["title"] as? String else { return nil }
                    return PermissionItem(id: i, title: title, available: p["available"] as? Bool ?? false, enabled: p["enabled"] as? Bool)
                }; showPermissions = true
                present("permissions", title: "مجوزهای برنامه", size: NSSize(width: 760, height: 370)) { PermissionsView(model: self) }
            case "apps":
                apps = (event["apps"] as? [[String: Any]] ?? []).compactMap { a in
                    guard let package = a["Package"] as? String else { return nil }
                    let icon = (a["Icon"] as? String).flatMap { Data(base64Encoded: $0) }.flatMap { NSImage(data: $0) }
                    return VehicleApp(package: package, label: a["Label"] as? String ?? package, icon: icon)
                }; showApps = true
                present("apps", title: "حذف APK", size: NSSize(width: 720, height: 330)) { UninstallView(model: self) }
            default: break
            }
        }
    }
    func fail(_ text: String) { message = text; showMessage = true }
    func send(_ command: String, extra: [String: Any] = [:]) {
        guard !uiPreview else { return } // Preview bundles never communicate with a vehicle.
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
            if command == "install" {
                phases = Array(repeating: "", count: 8); logs = ""; progressPage = true
                progressTitle = "در حال نصب وویا ایران"
                progressSubtitle = "کابل را جدا نکنید؛ در پایان نمایشگر خودکار راه‌اندازی مجدد می‌شود."
            }
            send(command, extra: extra)
        }
    }
    func chooseApk() {
        let panel = NSOpenPanel(); panel.allowsMultipleSelection = false
        panel.allowedContentTypes = [UTType(filenameExtension: "apk") ?? .data]
        if panel.runModal() == .OK, let url = panel.url { apkPath = url.path }
    }
    func present<V: View>(_ key: String, title: String, size: NSSize, @ViewBuilder content: () -> V) {
        if let existing = windows[key] { existing.window.makeKeyAndOrderFront(nil); return }
        let controller = InstallerWindow(model: self, key: key, title: title, size: size, content: content())
        windows[key] = controller; controller.window.makeKeyAndOrderFront(nil)
    }
    func close(_ key: String) { windows[key]?.window.performClose(nil) }
    func openApk(_ bundled: String? = nil, title: String = "نصب APK") {
        if let existing = windows["apk"] { existing.window.makeKeyAndOrderFront(nil); return }
        apkTitle = title; apkPath = ""; logs = ""
        present("apk", title: title, size: NSSize(width: 900, height: 650)) { ApkView(model: self, bundled: bundled) }
        if let bundled { send("install-bundled", extra: ["app": bundled]) }
    }
    func copyLogs() { NSPasteboard.general.clearContents(); NSPasteboard.general.setString(logs, forType: .string) }
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

// Shared visual contract: Android colors.xml, bg_button.xml and dimens.xml.
enum InstallerTheme {
    static func color(_ hex: UInt32) -> Color { Color(nsColor: native(hex)) }
    private static func native(_ hex: UInt32) -> NSColor { NSColor(srgbRed: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, alpha: 1) }
    static func adaptive(_ light: UInt32, _ dark: UInt32) -> Color {
        Color(nsColor: NSColor(name: nil) { appearance in
            native(appearance.bestMatch(from: [.aqua, .darkAqua]) == .darkAqua ? dark : light)
        })
    }
    static let background = adaptive(0xF1F3F6, 0x191D25), panel = adaptive(0xFFFFFF, 0x23272F)
    static let elevated = adaptive(0xE8ECF1, 0x2D3442), border = adaptive(0xCDD3DC, 0x373F4A)
    static let muted = adaptive(0x53606E, 0xCACACA), accent = adaptive(0x247ED4, 0x4099F3)
    static let text = adaptive(0x202732, 0xFFFFFF), accentSurface = adaptive(0xDCECFB, 0x263F59)
    static let button = adaptive(0xE3E8EF, 0x373F4A), switchTrack = adaptive(0xAEB7C2, 0x808894)
    static let error = adaptive(0xB3261E, 0xFFB4AB), success = color(0x277FCF)
    static let warning = adaptive(0x8A5700, 0xF3C969)
    static let primary = accent, log = panel
    static func registerFonts() {
        guard let resources = Bundle.main.resourceURL else { return }
        for name in ["Roboto-Regular.ttf", "Roboto-Bold.ttf", "iransansx_regular.ttf", "iransansx_bold.ttf"] {
            CTFontManagerRegisterFontsForURL(resources.appendingPathComponent("Fonts/" + name) as CFURL, .process, nil)
        }
    }
    static func font(size: CGFloat, weight: Font.Weight = .regular) -> Font {
        let bold = weight == .bold || weight == .semibold
        let persian = NSFontDescriptor(name: bold ? "IRANSansX-Bold" : "IRANSansX-Regular", size: size)
        let descriptor = NSFontDescriptor(name: bold ? "Roboto-Bold" : "Roboto-Regular", size: size)
            .addingAttributes([.cascadeList: [persian]])
        return Font(NSFont(descriptor: descriptor, size: size) ?? NSFont.systemFont(ofSize: size))
    }
    static func applyAppearance(_ choice: String) {
        NSApp.appearance = choice == "dark" ? NSAppearance(named: .darkAqua) : choice == "light" ? NSAppearance(named: .aqua) : nil
    }
}
struct InstallerToggleStyle: ToggleStyle {
    @Environment(\.isEnabled) private var enabled
    func makeBody(configuration: Configuration) -> some View {
        Button { configuration.isOn.toggle() } label: {
            HStack(spacing: 12) {
                ZStack(alignment: configuration.isOn ? .trailing : .leading) {
                    Capsule().fill(configuration.isOn ? InstallerTheme.accent : InstallerTheme.switchTrack)
                    Circle().fill(Color.white).frame(width: 18, height: 18).padding(3)
                }.frame(width: 42, height: 24).environment(\.layoutDirection, .leftToRight)
                configuration.label
            }.foregroundStyle(InstallerTheme.text).opacity(enabled ? 1 : 0.4)
        }.buttonStyle(.plain).accessibilityValue(configuration.isOn ? "روشن" : "خاموش")
    }
}
struct WindowsButtonStyle: ButtonStyle {
    var primary = false
    @Environment(\.isEnabled) private var enabled
    @State private var hovering = false
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.font(InstallerTheme.font(size: 14, weight: primary ? .bold : .regular))
            .foregroundStyle(primary ? Color.white : InstallerTheme.text)
            .padding(.horizontal, primary ? 36 : 24).padding(.vertical, primary ? 16 : 12)
            .background(primary ? InstallerTheme.primary : InstallerTheme.button, in: RoundedRectangle(cornerRadius: 35, style: .circular))
            .opacity(enabled ? (configuration.isPressed || hovering ? 0.85 : 1) : 0.4)
            .onHover { hovering = $0 }
    }
}
struct Card<Content: View>: View {
    var padding: CGFloat = 28
    var background: Color = InstallerTheme.panel
    @ViewBuilder var content: () -> Content
    var body: some View {
        content().padding(padding).background(background, in: RoundedRectangle(cornerRadius: 20, style: .circular))
    }
}
struct InstallerSurface<Content: View>: View {
    @AppStorage("installerAppearance") private var appearance = "system"
    @ViewBuilder var content: () -> Content
    var body: some View {
        content().font(InstallerTheme.font(size: 14)).foregroundStyle(InstallerTheme.text)
            .buttonStyle(WindowsButtonStyle()).tint(InstallerTheme.accent)
            .frame(maxWidth: .infinity, maxHeight: .infinity).background(InstallerTheme.background)
            .environment(\.layoutDirection, .rightToLeft)
            .preferredColorScheme(appearance == "dark" ? .dark : appearance == "light" ? .light : nil)
            .onAppear { InstallerTheme.applyAppearance(appearance) }
            .onChange(of: appearance) { InstallerTheme.applyAppearance($0) }
    }
}
@MainActor final class InstallerWindow: NSObject, NSWindowDelegate {
    let window: NSWindow
    weak var model: InstallerModel?
    let key: String
    init<V: View>(model: InstallerModel, key: String, title: String, size: NSSize, content: V) {
        self.model = model; self.key = key
        window = NSWindow(contentRect: NSRect(origin: .zero, size: size), styleMask: [.titled, .closable, .miniaturizable, .resizable], backing: .buffered, defer: false)
        super.init()
        window.title = title; window.isReleasedWhenClosed = false
        window.minSize = key == "apk" ? NSSize(width: 700, height: 500) : size
        window.contentView = NSHostingView(rootView: InstallerSurface { content })
        window.appearance = nil; window.delegate = self; window.center()
    }
    func windowShouldClose(_ sender: NSWindow) -> Bool { model?.busy != true }
    func windowWillClose(_ notification: Notification) { model?.windows.removeValue(forKey: key) }
}
@main struct VoyahInstallerApp: App {
    init() { InstallerTheme.registerFonts() }
    @StateObject private var model = InstallerModel()
    @NSApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    var body: some Scene {
        WindowGroup("نصاب وویا ایران") {
            InstallerSurface { InstallerView(model: model) }
                .frame(minWidth: 900, minHeight: 612)
                .onAppear {
                    delegate.model = model; model.start()
                    DispatchQueue.main.async {
                        if let window = NSApplication.shared.windows.first {
                            window.delegate = delegate
                            window.setContentSize(NSSize(width: 1120, height: 732))
                        }
                    }
                }
        }
        .commands {
            CommandGroup(replacing: .newItem) {}
            CommandMenu("ابزارهای تکمیلی") {
                Button(model.capture == "can" ? "پایان شنود CAN" : "شنود CAN") { model.send(model.capture == "can" ? "stop-can" : "start-can") }
                    .disabled(model.busy || !model.ready || (model.capture != "can" && !model.canOperate))
                Button("علامت در گزارش CAN") { model.send("mark-can") }.disabled(model.capture != "can" || model.busy)
            }
            // Conditional Commands require macOS 13; conditional menu content
            // uses ViewBuilder and remains compatible with macOS 12.
            CommandMenu("پیش‌نمایش توسعه") {
                if model.uiPreview {
                    Button("مراحل نصب") {
                        model.progressPage = true
                        model.phases = ["Succeeded", "Succeeded", "Running", "", "", "", "", ""]
                        model.logs = "UI preview only — no vehicle commands.\n[INFO] Preparing framework patch…\n"
                    }
                    Button("نصب APK") { model.openApk() }
                    Button("مجوزها") {
                        model.permissions = ["میکروفون", "دوربین", "موقعیت مکانی", "نصب برنامه", "اجرای پس‌زمینه"].enumerated().map { PermissionItem(id: $0.offset, title: $0.element, available: true, enabled: false) }
                        model.present("permissions", title: "مجوزهای برنامه", size: NSSize(width: 760, height: 370)) { PermissionsView(model: model) }
                    }
                    Button("حذف APK") { model.present("apps", title: "حذف APK", size: NSSize(width: 720, height: 330)) { UninstallView(model: model) } }
                }
            }
        }
    }
}
// Measured flow layout using macOS 12 APIs (SwiftUI.Layout requires macOS 13).
@resultBuilder struct ButtonListBuilder {
    static func buildExpression<V: View>(_ view: V) -> AnyView { AnyView(view) }
    static func buildBlock(_ views: AnyView...) -> [AnyView] { views }
}
struct ButtonSizes: PreferenceKey {
    static var defaultValue: [Int: CGSize] = [:]
    static func reduce(value: inout [Int: CGSize], nextValue: () -> [Int: CGSize]) {
        value.merge(nextValue(), uniquingKeysWith: { _, new in new })
    }
}
struct ButtonWrap: View {
    let buttons: [AnyView]
    @State private var sizes: [Int: CGSize] = [:]
    @State private var availableWidth: CGFloat = 1000
    init(@ButtonListBuilder content: () -> [AnyView]) { buttons = content() }
    func positions(width: CGFloat) -> ([(CGPoint, CGSize)], CGFloat) {
        var result: [(CGPoint, CGSize)] = []; var x: CGFloat = 0; var y: CGFloat = 0; var row: CGFloat = 0
        for index in buttons.indices {
            let size = sizes[index] ?? CGSize(width: 150, height: 44)
            if x > 0 && x + size.width > width { x = 0; y += row + 12; row = 0 }
            result.append((CGPoint(x: x, y: y), size)); x += size.width + 12; row = max(row, size.height)
        }
        return (result, y + row)
    }
    var body: some View {
        GeometryReader { geometry in
            let items = positions(width: geometry.size.width).0
            ZStack(alignment: .topLeading) {
                ForEach(buttons.indices, id: \.self) { index in
                    buttons[index].environment(\.layoutDirection, .rightToLeft).fixedSize()
                        .background(GeometryReader { child in
                            Color.clear.preference(key: ButtonSizes.self, value: [index: child.size])
                        })
                        .position(x: geometry.size.width - items[index].0.x - items[index].1.width / 2,
                                  y: items[index].0.y + items[index].1.height / 2)
                }
            }.environment(\.layoutDirection, .leftToRight)
                .onAppear { availableWidth = geometry.size.width }
                .onChange(of: geometry.size.width) { availableWidth = $0 }
                .onPreferenceChange(ButtonSizes.self) { sizes = $0 }
        }.frame(height: positions(width: availableWidth).1)
    }
}
struct InstallerView: View {
    @AppStorage("installerAppearance") private var appearance = "system"
    @ObservedObject var model: InstallerModel
    var body: some View {
        VStack(spacing: 0) {
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text("وویا ایران").font(InstallerTheme.font(size: 25, weight: .bold))
                    Text("نصب و راه‌اندازی روی خودرو").foregroundStyle(InstallerTheme.muted)
                }
                Spacer()
                HStack(spacing: 9) {
                    Circle().fill(model.serial == nil ? InstallerTheme.muted : InstallerTheme.success).frame(width: 9, height: 9)
                    Text(model.connection).lineLimit(2)
                }.padding(.horizontal, 16).padding(.vertical, 10)
                    .background(InstallerTheme.elevated, in: RoundedRectangle(cornerRadius: 20))
                Button(appearance == "light" ? "تم: روشن" : appearance == "dark" ? "تم: تیره" : "تم: سیستم") {
                    appearance = appearance == "system" ? "light" : appearance == "light" ? "dark" : "system"
                }.accessibilityLabel("تغییر تم")
            }.padding(.horizontal, 32).frame(height: 100).background(InstallerTheme.background)
            Rectangle().fill(InstallerTheme.border).frame(height: 1)
            Group { if model.progressPage { progress } else { home } }
                .disabled(!model.windows.isEmpty)
                .padding(.horizontal, 32).padding(.vertical, 28).frame(maxWidth: .infinity, maxHeight: .infinity)
            Rectangle().fill(InstallerTheme.border).frame(height: 1)
            Text("ابتدا USB debugging را روی ماشین فعال کنید و ماشین را با کابل USB به لپ‌تاپ متصل کنید.")
                .font(InstallerTheme.font(size: 12)).foregroundStyle(InstallerTheme.muted)
                .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 32).frame(height: 58)
        }
        .alert("وویا ایران", isPresented: $model.showMessage) { Button("باشه", role: .cancel) {} } message: { Text(model.message) }
    }
    var home: some View {
        VStack(alignment: .leading, spacing: 24) {
            Card {
                HStack {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("وویا ایران").font(InstallerTheme.font(size: 24, weight: .semibold))
                        Text(model.version.isEmpty ? "نسخهٔ توسعه" : "نسخهٔ " + model.version).foregroundStyle(InstallerTheme.accent)
                    }
                    Spacer()
                    Button("نصب") { model.confirm("نصب وویا ایران", text: "برنامه، واسط و پچ متناسب با خودرو نصب می‌شوند و نمایشگر راه‌اندازی مجدد می‌شود. کابل را تا پایان جدا نکنید.", command: "install") }
                        .buttonStyle(WindowsButtonStyle(primary: true)).disabled(!model.canOperate)
                }
            }
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    Text("خودرو متوقف باشد. پایان نصب، نمایشگر به‌صورت خودکار راه‌اندازی مجدد می‌شود.").foregroundStyle(InstallerTheme.muted)
                    ButtonWrap {
                        Button("نصب APK") { model.openApk() }.disabled(!model.canOperate)
                        Button("حذف APK") { model.send("apps") }.disabled(!model.canOperate)
                        Button("نصب مایکت") { model.openApk("myket", title: "نصب مایکت") }.disabled(!model.canOperate)
                        Button("نصب Gboard") { model.openApk("gboard", title: "نصب Gboard") }.disabled(!model.canOperate)
                        Button("نصب نشان") { model.openApk("neshan", title: "نصب نشان") }.disabled(!model.canOperate)
                        Button("راهنمای اتصال") { model.present("help", title: "راهنمای اتصال خودرو", size: NSSize(width: 760, height: 732)) { ConnectionHelpView(model: model) } }
                        Button(model.capture == "log" ? "پایان گزارش‌گیری" : "شروع گزارش‌گیری") { model.send(model.capture == "log" ? "stop-log" : "start-log") }
                            .disabled(model.busy || !model.ready || (model.capture != "log" && !model.canOperate))
                        Button("اجرای وویا ایران") { model.send("launch") }.disabled(!model.canOperate)
                        Button("تنظیم تاریخ و ساعت") { model.confirm("تنظیم ساعت", text: "ساعت خودرو با این مک هماهنگ و زمان خودکار خاموش می‌شود.", command: "clock") }.disabled(!model.canOperate)
                        Button("راه‌اندازی مجدد نمایشگر") { model.confirm("راه‌اندازی مجدد نمایشگر", text: "نمایشگر خودرو دوباره راه‌اندازی می‌شود.", command: "reboot") }.disabled(!model.canOperate)
                    }
                }.frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }
    var progress: some View {
        VStack(alignment: .leading, spacing: 20) {
            VStack(alignment: .leading, spacing: 8) {
                Text(model.progressTitle).font(InstallerTheme.font(size: 26, weight: .semibold))
                Text(model.progressSubtitle).foregroundStyle(InstallerTheme.muted)
            }
            HStack(spacing: 20) {
                Card(padding: 18) {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 24) {
                            Text("مراحل نصب").font(InstallerTheme.font(size: 17, weight: .semibold))
                            ForEach(0..<8, id: \.self) { i in
                                HStack(spacing: 4) {
                                    if model.phases[i] == "Running" { ProgressView().controlSize(.small).frame(width: 26) }
                                    else { Text(model.phases[i] == "Succeeded" ? "✓" : model.phases[i] == "Failed" ? "✕" : "○").font(InstallerTheme.font(size: 18)).frame(width: 26) }
                                    Text(model.phaseTitles[i]).frame(maxWidth: .infinity, alignment: .leading)
                                }.foregroundStyle(model.phases[i] == "Failed" ? InstallerTheme.error : model.phases[i] == "Succeeded" ? InstallerTheme.success : InstallerTheme.muted)
                            }
                        }
                    }
                }.frame(width: 250)
                Card(padding: 16, background: InstallerTheme.log) {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("گزارش کامل نصب").foregroundStyle(InstallerTheme.muted)
                        LogView(model: model)
                    }
                }
            }
            HStack(spacing: 12) {
                Button("کپی گزارش") { model.copyLogs() }
                Button("پوشهٔ گزارش‌های نصب") { model.openLogs() }
                Button("بازگشت") { model.progressPage = false }.disabled(model.busy)
            }
        }
    }
}
struct LogView: View {
    @ObservedObject var model: InstallerModel
    var body: some View {
        GeometryReader { geometry in
            ScrollViewReader { proxy in
                ScrollView([.horizontal, .vertical]) {
                    VStack(alignment: .leading, spacing: 0) {
                        Text(model.logs).font(InstallerTheme.font(size: 12)).textSelection(.enabled).fixedSize()
                        Color.clear.frame(width: 1, height: 1).id("end")
                    }.frame(minWidth: geometry.size.width, minHeight: geometry.size.height, alignment: .topLeading)
                }.onChange(of: model.logs) { _ in proxy.scrollTo("end", anchor: .bottomLeading) }
            }
        }.foregroundStyle(InstallerTheme.text).background(InstallerTheme.log)
            .environment(\.layoutDirection, .leftToRight).frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
struct ApkView: View {
    @ObservedObject var model: InstallerModel
    let bundled: String?
    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            Text(model.apkTitle).font(InstallerTheme.font(size: 26))
            if bundled == nil {
                VStack(alignment: .leading, spacing: 12) {
                    HStack(spacing: 12) {
                        Button("انتخاب فایل APK") { model.chooseApk() }.disabled(!model.canOperate)
                        Button("نصب") { model.send("install-apk", extra: ["path": model.apkPath]) }
                            .buttonStyle(WindowsButtonStyle(primary: true)).disabled(model.apkPath.isEmpty || !model.canOperate)
                    }
                    Text(model.apkPath.isEmpty ? "فایلی انتخاب نشده است" : model.apkPath)
                        .environment(\.layoutDirection, .leftToRight)
                }
            }
            LogView(model: model)
            HStack(spacing: 12) {
                Button("کپی گزارش") { model.copyLogs() }
                Button("بازگشت") { model.close("apk") }.disabled(model.busy)
            }
        }.padding(24).disabled(model.windows["permissions"] != nil)
    }
}
struct PermissionsView: View {
    @ObservedObject var model: InstallerModel
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("برنامه نصب شد").font(InstallerTheme.font(size: 24))
            Text("دسترسی‌های موردنیاز را روشن یا خاموش کنید.").padding(.vertical, 12)
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), alignment: .leading), count: 3), alignment: .leading, spacing: 28) {
                ForEach(model.permissions) { p in
                    Toggle(p.title, isOn: Binding(get: { p.enabled ?? false }, set: { model.send("set-permission", extra: ["index": p.id, "enabled": $0]) }))
                        .disabled(!model.canOperate || !p.available || p.enabled == nil)
                }
                Toggle("همه موارد", isOn: Binding(get: {
                    let known = model.permissions.filter { $0.available && $0.enabled != nil }
                    return !known.isEmpty && known.allSatisfy { $0.enabled == true }
                }, set: { model.send("set-all-permissions", extra: ["enabled": $0]) }))
                    .disabled(!model.canOperate || !model.permissions.contains { $0.available && $0.enabled != nil })
            }.toggleStyle(InstallerToggleStyle())
            Spacer()
            Text(model.permissions.contains(where: { $0.available && $0.enabled == nil }) ? "خواندن بعضی دسترسی‌ها ناموفق بود؛ گزارش نصب را بررسی کنید." : "تغییر هر گزینه بلافاصله اعمال می‌شود.")
                .foregroundStyle(InstallerTheme.muted)
            HStack(spacing: 12) {
                Button("تازه‌سازی") { model.send("permissions") }.disabled(!model.canOperate)
                Button("بستن") { model.close("permissions") }.disabled(model.busy)
            }
        }.padding(24)
    }
}
struct UninstallView: View {
    @ObservedObject var model: InstallerModel
    @State private var selection = ""
    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            Text("انتخاب برنامه برای حذف").font(InstallerTheme.font(size: 24))
            HStack(spacing: 12) {
                Menu {
                    ForEach(model.apps) { app in
                        Button { selection = app.package } label: {
                            if let icon = app.icon { Image(nsImage: icon) }
                            Text(app.label)
                        }
                    }
                } label: {
                    HStack {
                        if let app = model.apps.first(where: { $0.package == selection }) {
                            if let icon = app.icon { Image(nsImage: icon).resizable().frame(width: 40, height: 40) }
                            Text(app.label)
                        } else { Text("انتخاب برنامه") }
                        Spacer()
                    }.padding(8).frame(maxWidth: .infinity, minHeight: 56)
                }.menuStyle(.borderlessButton).foregroundStyle(InstallerTheme.text)
                    .background(InstallerTheme.panel, in: RoundedRectangle(cornerRadius: 20)).disabled(!model.canOperate)
                Button("حذف برنامه") { model.confirm("حذف برنامه", text: "برنامه و داده‌های آن حذف می‌شوند.", command: "remove", extra: ["package": selection]) }
                    .foregroundStyle(InstallerTheme.error).disabled(selection.isEmpty || !model.canOperate)
            }
            Text(model.busy ? "در حال انجام عملیات…" : "").foregroundStyle(InstallerTheme.muted)
            Spacer()
            HStack(spacing: 12) {
                Button("تازه‌سازی") { model.send("apps") }.disabled(!model.canOperate)
                Button("کپی گزارش") { model.copyLogs() }
            }
        }.padding(24)
            .onAppear { selection = model.apps.first?.package ?? "" }
            .onChange(of: model.apps.map(\.package)) { _ in selection = model.apps.first?.package ?? "" }
    }
}
struct ConnectionHelpView: View {
    @ObservedObject var model: InstallerModel
    static func password(at instant: Date) -> String {
        var calendar = Calendar(identifier: .gregorian); calendar.timeZone = TimeZone(secondsFromGMT: 8 * 3600)!
        let c = calendar.dateComponents([.year, .month, .day], from: instant)
        let y = c.year!, m = c.month!, d = c.day!
        return [y / 1000 + m / 10, y % 1000 / 100 + m % 10, y % 100 / 10 + d / 10, y % 10 + d % 10].map(String.init).joined()
    }
    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            VStack(alignment: .leading, spacing: 8) {
                Text("اتصال خودرو به نصاب").font(InstallerTheme.font(size: 26, weight: .semibold))
                Text("این مراحل را روی نمایشگر خودرو انجام دهید.").foregroundStyle(InstallerTheme.muted)
            }
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Card(padding: 18) {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("پیش از وارد کردن رمز: تاریخ و ساعت خودرو باید درست باشد.").font(InstallerTheme.font(size: 17, weight: .bold)).foregroundStyle(InstallerTheme.accent)
                            Text("اگر تاریخ یا ساعت تنظیم نیست، خودرو را از طریق Wi-Fi به اینترنت متصل کنید تا تاریخ و ساعت به‌صورت خودکار تنظیم شوند؛ سپس رمز را وارد کنید.").font(InstallerTheme.font(size: 16))
                        }.foregroundStyle(InstallerTheme.text).frame(maxWidth: .infinity, alignment: .leading)
                    }
                    Card(padding: 20) {
                        VStack(spacing: 8) {
                            Text("رمز منوی مخفی").font(InstallerTheme.font(size: 18)).foregroundStyle(InstallerTheme.text)
                            TimelineView(.periodic(from: .now, by: 1)) { context in
                                Text(Self.password(at: context.date)).font(InstallerTheme.font(size: 64, weight: .bold))
                                    .foregroundStyle(InstallerTheme.accent).environment(\.layoutDirection, .leftToRight)
                            }
                        }.frame(maxWidth: .infinity)
                    }
                    Group {
                        Text("۱. وارد تنظیمات خودرو (Settings) شوید و آخرین گزینه، سیستم (System) را باز کنید.")
                        Text("۲. چند بار پشت سر هم روی Hardware version بزنید تا پنجرهٔ ورود رمز باز شود.")
                        Text("۳. رمز بزرگِ بالای این صفحه را وارد کنید تا منوی مخفی باز شود.")
                        Text("۴. در صفحهٔ بازشده، گزینهٔ USB debugging را پیدا و فعال کنید.")
                        Text("۵. خودرو را با کابل USB دارای قابلیت انتقال داده به لپ‌تاپ وصل کنید. کابل را حتماً به پورت USB-A سمت راست زیر داشبورد متصل کنید؛ از پورت تایپ‌سی (USB-C) استفاده نکنید.")
                        Text("۶. این راهنما را ببندید. وقتی بالای نصاب «خودرو شناسایی شد» نمایش داده شد، نصب را شروع کنید.")
                    }.font(InstallerTheme.font(size: 17))
                    Text("رمز ساعت ۱۹:۳۰ به وقت ایران تغییر می‌کند. اگر پذیرفته نشد، تاریخ و ساعت خودرو و لپ‌تاپ را بررسی کنید.").foregroundStyle(InstallerTheme.muted)
                }.frame(maxWidth: .infinity, alignment: .leading)
            }
            Button("متوجه شدم") { model.close("help") }.buttonStyle(WindowsButtonStyle(primary: true)).foregroundStyle(.white)
        }.padding(28).foregroundStyle(InstallerTheme.text).background(InstallerTheme.background)
    }
}
