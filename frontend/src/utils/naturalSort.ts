// Natural/alphanumeric sort - "2" sorts before "10", and "1A" sorts before
// "10A", unlike a plain string sort (which puts "10" before "2" and "10A"
// before "1A" since it compares character-by-character). Used for bin codes
// like 1, 1A, 1B, 2, 2A, 2B, ... 10, 10A, which a supplier/OrderWise export
// names in a human-sensible but not string-sortable order.
export function naturalCompare(a: string, b: string): number {
  const chunks = (s: string) => s.match(/\d+|\D+/g) ?? [];
  const ac = chunks(a);
  const bc = chunks(b);
  const len = Math.max(ac.length, bc.length);
  for (let i = 0; i < len; i++) {
    const x = ac[i] ?? "";
    const y = bc[i] ?? "";
    const xIsNum = /^\d+$/.test(x);
    const yIsNum = /^\d+$/.test(y);
    if (xIsNum && yIsNum) {
      const diff = parseInt(x, 10) - parseInt(y, 10);
      if (diff !== 0) return diff;
    } else {
      const cmp = x.localeCompare(y);
      if (cmp !== 0) return cmp;
    }
  }
  return 0;
}
