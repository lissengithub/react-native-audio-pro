import Foundation

enum PlaybackStallRetryDecision: Equatable {
	case attempt(Int)
	case exhausted(Int)
}

struct PlaybackResumePresentation: Equatable {
	let isActuallyPlaying: Bool
	let nowPlayingRate: Float
}

struct PlaybackRecoveryPolicy {
	static func nextStallRetry(currentAttempt: Int, maxAttempts: Int) -> PlaybackStallRetryDecision {
		let normalizedAttempt = max(0, currentAttempt)
		let normalizedMaximum = max(0, maxAttempts)
		guard normalizedAttempt < normalizedMaximum else {
			return .exhausted(normalizedAttempt)
		}

		return .attempt(normalizedAttempt + 1)
	}

	static func resumePresentation(
		isActuallyPlaying: Bool,
		playbackSpeed: Float
	) -> PlaybackResumePresentation {
		PlaybackResumePresentation(
			isActuallyPlaying: isActuallyPlaying,
			nowPlayingRate: isActuallyPlaying ? playbackSpeed : 0
		)
	}
}
