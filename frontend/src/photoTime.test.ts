import { describe, expect, it } from 'vitest';
import { photoCaptureTime } from './photoTime';

function jpegWithCaptureTime(value: string) {
  const tiff = new Uint8Array(64);
  const view = new DataView(tiff.buffer);
  tiff.set([0x49, 0x49, 42, 0, 8, 0, 0, 0]);
  view.setUint16(8, 1, true);
  view.setUint16(10, 0x8769, true); view.setUint16(12, 4, true);
  view.setUint32(14, 1, true); view.setUint32(18, 26, true);
  view.setUint16(26, 1, true);
  view.setUint16(28, 0x9003, true); view.setUint16(30, 2, true);
  view.setUint32(32, 20, true); view.setUint32(36, 44, true);
  tiff.set(new TextEncoder().encode(value), 44);
  return new Uint8Array([0xff, 0xd8, 0xff, 0xe1, 0, 72, 69, 120, 105, 102, 0, 0, ...tiff, 0xff, 0xd9]);
}

describe('photo capture time', () => {
  it('reads real EXIF capture time without timezone conversion', async () => {
    expect(await photoCaptureTime(jpegWithCaptureTime('2026:10:05 23:47:12'))).toBe('23:47');
    expect(await photoCaptureTime(jpegWithCaptureTime('2026:10:06 00:00:00'))).toBe('00:00');
  });
  it('ignores absent, corrupt and invalid metadata', async () => {
    expect(await photoCaptureTime(new Uint8Array([0xff, 0xd8, 0xff, 0xd9]))).toBeNull();
    expect(await photoCaptureTime(new Uint8Array([1, 2, 3]))).toBeNull();
    expect(await photoCaptureTime(jpegWithCaptureTime('2026:02:30 12:00:00'))).toBeNull();
    expect(await photoCaptureTime(jpegWithCaptureTime('2026:10:06 24:00:00'))).toBeNull();
  });
});
