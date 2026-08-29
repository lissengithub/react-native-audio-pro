import XCTest
@testable import AudioProRecoveryPolicy

final class PlaybackRecoveryPolicyTests: XCTestCase {
	func testStallRetryAdvancesUntilMaximum() {
		XCTAssertEqual(
			PlaybackRecoveryPolicy.nextStallRetry(currentAttempt: 0, maxAttempts: 3),
			.attempt(1)
		)
		XCTAssertEqual(
			PlaybackRecoveryPolicy.nextStallRetry(currentAttempt: 2, maxAttempts: 3),
			.attempt(3)
		)
	}

	func testStallRetryExhaustionIsTerminal() {
		XCTAssertEqual(
			PlaybackRecoveryPolicy.nextStallRetry(currentAttempt: 3, maxAttempts: 3),
			.exhausted(3)
		)
		XCTAssertEqual(
			PlaybackRecoveryPolicy.nextStallRetry(currentAttempt: 0, maxAttempts: 0),
			.exhausted(0)
		)
	}

	func testInterruptionResumeAdvertisesBufferingUntilPlaybackFlows() {
		XCTAssertEqual(
			PlaybackRecoveryPolicy.resumePresentation(
				isActuallyPlaying: false,
				playbackSpeed: 1.5
			),
			PlaybackResumePresentation(isActuallyPlaying: false, nowPlayingRate: 0)
		)
	}

	func testInterruptionResumeAdvertisesConfiguredRateOncePlaying() {
		XCTAssertEqual(
			PlaybackRecoveryPolicy.resumePresentation(
				isActuallyPlaying: true,
				playbackSpeed: 1.5
			),
			PlaybackResumePresentation(isActuallyPlaying: true, nowPlayingRate: 1.5)
		)
	}
}
