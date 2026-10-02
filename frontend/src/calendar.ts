export function seoulToday(now = new Date()): string {
  const parts = new Intl.DateTimeFormat('en-US', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(now);
  const value = (type: string) => parts.find(part => part.type === type)!.value;
  return `${value('year')}-${value('month')}-${value('day')}`;
}

export function monthGrid(year: number, month: number): (number | null)[] {
  const start = new Date(Date.UTC(year, month - 1, 1)).getUTCDay();
  const days = daysInMonth(year, month);
  const cells: (number | null)[] = Array(start).fill(null);
  for (let day = 1; day <= days; day++) cells.push(day);
  while (cells.length % 7) cells.push(null);
  return cells;
}

export function daysInMonth(year: number, month: number): number {
  return new Date(Date.UTC(year, month, 0)).getUTCDate();
}

export function recordedDays(dates: string[], year: number, month: number): Set<number> {
  const prefix = `${year}-${String(month).padStart(2, '0')}-`;
  return new Set(dates.filter(date => date.startsWith(prefix)).map(date => Number(date.slice(8))).filter(day => day >= 1 && day <= daysInMonth(year, month)));
}

export function categoryColor(color: string): string {
  return /^#[0-9a-f]{6}$/i.test(color) ? color : '#9470e8';
}
