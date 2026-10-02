import { describe, expect, it } from 'vitest';
import { daysInMonth, monthGrid, recordedDays, seoulToday } from './calendar';

describe('calendar dates', () => {
  it('changes month at Korean midnight even when UTC is still the previous day', () => {
    expect(seoulToday(new Date('2026-09-30T14:59:59Z'))).toBe('2026-09-30');
    expect(seoulToday(new Date('2026-09-30T15:00:00Z'))).toBe('2026-10-01');
  });
  it('handles leap years and December', () => {
    expect(daysInMonth(2024, 2)).toBe(29);
    expect(daysInMonth(2025, 2)).toBe(28);
    expect(daysInMonth(2026, 12)).toBe(31);
  });
  it('aligns September 2026 to Tuesday and pads the final week', () => {
    const grid = monthGrid(2026, 9);
    expect(grid.slice(0, 4)).toEqual([null, null, 1, 2]);
    expect(grid).toHaveLength(35);
    expect(grid.filter(day => day !== null)).toHaveLength(30);
  });
  it('handles months starting Sunday and six-week months', () => {
    expect(monthGrid(2026, 2)[0]).toBe(1);
    expect(monthGrid(2026, 8)).toHaveLength(42);
  });
  it('counts unique valid record dates in only the selected month', () => {
    expect([...recordedDays(['2026-09-01', '2026-09-01', '2026-09-30', '2026-10-01', '2026-09-31'], 2026, 9)]).toEqual([1, 30]);
  });
});
