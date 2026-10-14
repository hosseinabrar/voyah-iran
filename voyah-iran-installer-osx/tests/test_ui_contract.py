"""Keep the Mac visual contract tied to the Windows XAML in the same branch."""
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WINDOWS = ROOT.parent / "voyah-iran-installer/src/VoyahIran.Installer"
SOURCE = (ROOT / "Sources/App.swift").read_text()
NS = {"w": "http://schemas.microsoft.com/winfx/2006/xaml/presentation"}


class WindowsVisualContractTests(unittest.TestCase):
    def test_macos12_and_runtime_selection_contract(self):
        self.assertIn(".macOS(.v12)", (ROOT / "Package.swift").read_text())
        self.assertNotIn("struct ButtonWrap: Layout", SOURCE)
        self.assertNotIn(".defaultSize(", SOURCE)
        # CommandsBuilder.buildIf is macOS 13+, unlike ViewBuilder.buildIf.
        self.assertRegex(SOURCE, r'CommandMenu\("پیش‌نمایش توسعه"\)\s*\{\s*if model\.uiPreview')
        self.assertIn("#if arch(arm64)", SOURCE)
        self.assertIn("#elseif arch(x86_64)", SOURCE)
        self.assertIn('backend/\\(Self.runtime)/VoyahIran.Installer.Mac', SOURCE)
        self.assertIn('payloads/\\(Self.runtime).zip', SOURCE)

    def test_shared_palette(self):
        app = ET.parse(WINDOWS / "App.xaml")
        for brush in app.findall(".//w:SolidColorBrush", NS):
            self.assertIn("0x" + brush.attrib["Color"].lstrip("#"), SOURCE)

    def test_home_buttons_follow_windows_order(self):
        window = ET.parse(WINDOWS / "MainWindow.xaml")
        buttons = window.findall(".//w:WrapPanel/w:Button", NS)
        home = SOURCE.split("var home: some View", 1)[1].split("var progress: some View", 1)[0]
        positions = []
        for button in buttons:
            title = button.attrib["Content"]
            if title.startswith("{"):
                title = "شروع گزارش‌گیری"
            positions.append(home.index('"' + title + '"'))
        self.assertEqual(positions, sorted(positions))

    def test_separate_progress_and_native_windows(self):
        self.assertIn("if model.progressPage { progress } else { home }", SOURCE)
        self.assertIn("frame(width: 250)", SOURCE)
        self.assertIn("NSWindow(contentRect:", SOURCE)
        self.assertNotIn(".sheet(", SOURCE)

    def test_permission_grid_and_all_control(self):
        self.assertIn('Toggle("همه موارد"', SOURCE)
        self.assertIn("count: 3)", SOURCE)
        self.assertIn('model.send("set-all-permissions"', SOURCE)


if __name__ == "__main__":
    unittest.main()
