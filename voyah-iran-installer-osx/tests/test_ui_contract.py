"""Keep the Mac visual contract tied to the Windows XAML in the same branch."""
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WINDOWS = ROOT.parent / "voyah-iran-installer/src/VoyahIran.Installer"
SOURCE = (ROOT / "Sources/App.swift").read_text()
NS = {"w": "http://schemas.microsoft.com/winfx/2006/xaml/presentation"}


class WindowsVisualContractTests(unittest.TestCase):
    def test_both_themes_use_android_palette(self):
        android = ROOT.parent / "voyah-iran/app/src/main/res"
        theme = (WINDOWS / "InstallerTheme.cs").read_text()
        for folder in ("values", "values-night"):
            colors = {c.attrib["name"]: c.text.upper() for c in ET.parse(android / folder / "colors.xml").getroot() if c.tag == "color"}
            for token in ("background", "surface", "surface_elevated", "stroke", "accent", "text_primary", "text_secondary", "control_track"):
                value = colors[token]
                self.assertIn(value, theme)
                self.assertIn(value.replace("#", "0x"), SOURCE)

    def test_fonts_and_shapes_match_client(self):
        xaml = (WINDOWS / "App.xaml").read_text()
        self.assertIn('CornerRadius="35"', xaml)
        self.assertIn('CornerRadius="20"', xaml)
        self.assertIn('cornerRadius: 35', SOURCE)
        self.assertIn('cornerRadius: 20', SOURCE)
        self.assertIn('assets/passenger/fonts', (WINDOWS / "VoyahIran.Installer.csproj").read_text())
        for family in ("IRANSansX", "Roboto"):
            self.assertIn(family, SOURCE)
            self.assertIn(family, (WINDOWS / "InstallerTheme.cs").read_text())

    def test_all_windows_are_valid_xml(self):
        for path in WINDOWS.glob("*.xaml"):
            ET.parse(path)

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
