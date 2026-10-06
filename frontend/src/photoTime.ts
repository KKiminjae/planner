import { parse } from 'exifr';

export async function photoCaptureTime(file: Blob | Uint8Array): Promise<string | null> {
  try {
    // Preserve the camera's local clock; Date conversion could change its timezone.
    const metadata = await parse(file, { pick: ['DateTimeOriginal'], reviveValues: false });
    const value = metadata?.DateTimeOriginal;
    if (typeof value !== 'string') return null;
    const match = /^(\d{4}):(\d{2}):(\d{2}) (\d{2}):(\d{2}):(\d{2})$/.exec(value.trim());
    if (!match) return null;
    const [, year, month, day, hour, minute, second] = match;
    const date = new Date(Date.UTC(Number(year), Number(month) - 1, Number(day)));
    if (date.getUTCFullYear() !== Number(year) || date.getUTCMonth() + 1 !== Number(month) || date.getUTCDate() !== Number(day)
      || Number(hour) > 23 || Number(minute) > 59 || Number(second) > 59) return null;
    return `${hour}:${minute}`;
  } catch {
    return null;
  }
}
