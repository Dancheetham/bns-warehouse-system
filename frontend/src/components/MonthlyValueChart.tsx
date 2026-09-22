interface MonthSeries {
  month: number; // 1-12
  invoiceTotal: number;
  creditTotal: number;
}

const MONTH_LABELS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

// Deliberately dependency-free (no recharts etc.), same reasoning as
// PieChart.tsx - a 12-point monthly line chart doesn't need a charting
// library. A line (not bars) with each point's value labelled directly on
// the chart, matching the reference report the values-by-month figures
// originally came from.
export default function MonthlyValueChart({
  data,
  height = 240,
  showCredits = true,
}: {
  data: MonthSeries[];
  height?: number;
  showCredits?: boolean;
}) {
  const width = 760;
  const paddingLeft = 56;
  const paddingBottom = 28;
  // Extra headroom above the plotted line for the value-label boxes, so a
  // tall point near the top of the range doesn't get its label clipped.
  const paddingTop = 34;
  const chartWidth = width - paddingLeft - 12;
  const chartHeight = height - paddingTop - paddingBottom;

  const byMonth = new Map(data.map((d) => [d.month, d]));
  const months = Array.from({ length: 12 }, (_, i) => byMonth.get(i + 1) ?? { month: i + 1, invoiceTotal: 0, creditTotal: 0 });

  const maxValue = Math.max(1, ...months.map((m) => Math.max(m.invoiceTotal, showCredits ? m.creditTotal : 0)));
  // Round the axis max up to a "nice" number so the gridlines read cleanly.
  const niceMax = niceCeiling(maxValue);

  const stepX = chartWidth / 11; // 12 points, 11 gaps
  const pointX = (i: number) => paddingLeft + i * stepX;
  const pointY = (value: number) => paddingTop + chartHeight - (value / niceMax) * chartHeight;

  const yTicks = [0, 0.25, 0.5, 0.75, 1].map((f) => Math.round(niceMax * f));

  const formatK = (v: number) => (v >= 1000 ? `${(v / 1000).toFixed(v >= 10000 ? 0 : 1)}k` : String(v));
  const formatLabel = (v: number) =>
    `£${v.toLocaleString(undefined, { maximumFractionDigits: v >= 1000 ? 0 : 2 })}`;

  const allZero = months.every((m) => m.invoiceTotal === 0 && m.creditTotal === 0);

  const invoicePath = months.map((m, i) => `${i === 0 ? "M" : "L"}${pointX(i)},${pointY(m.invoiceTotal)}`).join(" ");
  const creditPath = months.map((m, i) => `${i === 0 ? "M" : "L"}${pointX(i)},${pointY(m.creditTotal)}`).join(" ");

  // A small rounded box with the value inside, anchored above the point (or
  // below, for the credit line, so the two series' labels don't collide on
  // months where both have a value).
  function ValueLabel({ x, y, value, color, above }: { x: number; y: number; value: number; color: string; above: boolean }) {
    if (value === 0) return null;
    const text = formatLabel(value);
    const boxWidth = Math.max(34, text.length * 6.2 + 10);
    const boxHeight = 16;
    const boxY = above ? y - 10 - boxHeight : y + 10;
    return (
      <g>
        <rect x={x - boxWidth / 2} y={boxY} width={boxWidth} height={boxHeight} rx={3} fill={color} opacity={0.95} />
        <text x={x} y={boxY + boxHeight / 2 + 4} textAnchor="middle" fontSize={10} fontWeight={600} fill="white">
          {text}
        </text>
      </g>
    );
  }

  return (
    <div className="w-full overflow-x-auto">
      <svg viewBox={`0 0 ${width} ${height}`} className="w-full" style={{ minWidth: 560 }}>
        {/* gridlines + y-axis labels */}
        {yTicks.map((tick) => {
          const y = paddingTop + chartHeight - (tick / niceMax) * chartHeight;
          return (
            <g key={tick}>
              <line x1={paddingLeft} y1={y} x2={width - 12} y2={y} stroke="#e2e8f0" strokeWidth={1} />
              <text x={paddingLeft - 8} y={y + 4} textAnchor="end" fontSize={11} fill="#94a3b8">
                £{formatK(tick)}
              </text>
            </g>
          );
        })}

        {allZero && (
          <text x={width / 2} y={height / 2} textAnchor="middle" fontSize={13} fill="#94a3b8">
            No invoiced values yet this year
          </text>
        )}

        {/* lines drawn first so the point markers and value labels sit on top */}
        <path d={invoicePath} fill="none" stroke="#10b981" strokeWidth={2} />
        {showCredits && <path d={creditPath} fill="none" stroke="#f43f5e" strokeWidth={2} />}

        {months.map((m, i) => {
          const x = pointX(i);
          const invoiceY = pointY(m.invoiceTotal);
          const creditY = pointY(m.creditTotal);

          return (
            <g key={m.month}>
              <circle cx={x} cy={invoiceY} r={3.5} fill="#10b981" />
              <ValueLabel x={x} y={invoiceY} value={m.invoiceTotal} color="#10b981" above />

              {showCredits && (
                <>
                  <circle cx={x} cy={creditY} r={3.5} fill="#f43f5e" />
                  <ValueLabel x={x} y={creditY} value={m.creditTotal} color="#f43f5e" above={false} />
                </>
              )}

              <text x={x} y={height - 8} textAnchor="middle" fontSize={11} fill="#64748b">
                {MONTH_LABELS[i]}
              </text>
            </g>
          );
        })}
      </svg>
    </div>
  );
}

function niceCeiling(value: number): number {
  if (value <= 0) return 1;
  const magnitude = Math.pow(10, Math.floor(Math.log10(value)));
  const normalized = value / magnitude;
  const niceNormalized = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10;
  return niceNormalized * magnitude;
}
