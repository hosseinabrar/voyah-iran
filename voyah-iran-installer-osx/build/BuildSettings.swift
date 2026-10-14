import AppKit
import Security

let app = NSApplication.shared
app.setActivationPolicy(.regular)
app.activate(ignoringOtherApps: true)
let path = URL(fileURLWithPath: CommandLine.arguments[1])
let old = (try? Data(contentsOf: path)).flatMap { try? JSONSerialization.jsonObject(with: $0) as? [String: Any] } ?? [:]
let alert = NSAlert()
alert.messageText = "تنظیمات بیلد وویا ایران"
alert.informativeText = "رمز کلید امضای قبلی اندروید در Keychain همین مک ذخیره می‌شود. رمز خالی، رمز ذخیره‌شده را حفظ می‌کند."
let stack = NSStackView(); stack.orientation = .vertical; stack.alignment = .leading; stack.spacing = 10
let store = NSSecureTextField(), key = NSSecureTextField()
store.placeholderString = "رمز Keystore"; key.placeholderString = "رمز مستقل کلید — اختیاری"
let protect = NSButton(checkboxWithTitle: "اعمال پروتکشن DPT (نیازمند R8)", target: nil, action: nil)
let minify = NSButton(checkboxWithTitle: "مینی‌فای و کوچک‌سازی منابع (R8)", target: nil, action: nil)
protect.state = old["ProtectionEnabled"] as? Bool == true ? .on : .off
minify.state = old["MinifyEnabled"] as? Bool == false ? .off : .on
for view in [store, key, protect, minify] as [NSView] { stack.addArrangedSubview(view) }
stack.frame = NSRect(x: 0, y: 0, width: 410, height: 130)
alert.accessoryView = stack; alert.addButton(withTitle: "ذخیره"); alert.addButton(withTitle: "انصراف")
if alert.runModal() == .alertFirstButtonReturn {
    func save(_ service: String, _ value: String) throws {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: "voyahiran"]
        let attributes = [kSecValueData as String: Data(value.utf8)]
        var result = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if result == errSecItemNotFound { result = SecItemAdd(query.merging(attributes) { _, new in new } as CFDictionary, nil) }
        if result != errSecSuccess { throw NSError(domain: NSOSStatusErrorDomain, code: Int(result)) }
    }
    do {
        if !store.stringValue.isEmpty {
            try save("VoyahIran.Android.StorePassword", store.stringValue)
            try save("VoyahIran.Android.KeyPassword", key.stringValue.isEmpty ? store.stringValue : key.stringValue)
        } else if !key.stringValue.isEmpty { try save("VoyahIran.Android.KeyPassword", key.stringValue) }
        let settings = ["ProtectionEnabled": protect.state == .on, "MinifyEnabled": protect.state == .on || minify.state == .on]
        try JSONSerialization.data(withJSONObject: settings, options: [.prettyPrinted, .sortedKeys]).write(to: path, options: .atomic)
    } catch { let failure = NSAlert(error: error); failure.runModal(); exit(1) }
}
