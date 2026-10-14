// swift-tools-version: 5.9
import PackageDescription
let package = Package(name: "VoyahIranInstaller", platforms: [.macOS(.v13)], products: [
    .executable(name: "VoyahIranInstaller", targets: ["VoyahIranInstaller"])
], targets: [.executableTarget(name: "VoyahIranInstaller", path: "Sources")])
