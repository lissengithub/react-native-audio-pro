// swift-tools-version: 5.9

import PackageDescription

let package = Package(
	name: "AudioProNativeTests",
	platforms: [.macOS(.v13)],
	targets: [
		.target(
			name: "AudioProRecoveryPolicy",
			path: "ios",
			exclude: [
				"AudioPro-Bridging-Header.h",
				"AudioPro.mm",
				"AudioPro.swift",
				"Tests"
			],
			sources: ["PlaybackRecoveryPolicy.swift"]
		),
		.testTarget(
			name: "AudioProRecoveryPolicyTests",
			dependencies: ["AudioProRecoveryPolicy"],
			path: "ios/Tests"
		)
	]
)
