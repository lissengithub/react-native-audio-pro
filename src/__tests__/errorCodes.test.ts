import { AudioProErrorCode, isTransientErrorCode } from '../values';

describe('AudioProErrorCode', () => {
	it('has the correct numeric values', () => {
		expect(AudioProErrorCode.NETWORK_DISCONNECTED).toBe(1001);
		expect(AudioProErrorCode.NETWORK_TIMEOUT).toBe(1002);
		expect(AudioProErrorCode.HTTP_SERVER_ERROR).toBe(1003);
		expect(AudioProErrorCode.HTTP_CLIENT_ERROR).toBe(1004);
		expect(AudioProErrorCode.IO_UNSPECIFIED).toBe(1005);
	});
});

describe('isTransientErrorCode', () => {
	it('returns true for transient error codes', () => {
		expect(isTransientErrorCode(AudioProErrorCode.NETWORK_DISCONNECTED)).toBe(true);
		expect(isTransientErrorCode(AudioProErrorCode.NETWORK_TIMEOUT)).toBe(true);
		expect(isTransientErrorCode(AudioProErrorCode.HTTP_SERVER_ERROR)).toBe(true);
		expect(isTransientErrorCode(AudioProErrorCode.IO_UNSPECIFIED)).toBe(true);
	});

	it('returns false for fatal error codes', () => {
		expect(isTransientErrorCode(AudioProErrorCode.HTTP_CLIENT_ERROR)).toBe(false);
	});

	it('returns false for undefined/null/zero', () => {
		expect(isTransientErrorCode(undefined)).toBe(false);
		expect(isTransientErrorCode(0)).toBe(false);
	});

	it('returns false for unknown error codes', () => {
		expect(isTransientErrorCode(9999)).toBe(false);
		expect(isTransientErrorCode(500)).toBe(false);
	});
});
