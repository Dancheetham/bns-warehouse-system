interface Slice {
  label: string;
  value: number;
  color: string;
}

// Deliberately dependency-free (no recharts etc.) - a handful of slices doesn't
// need a charting library, and this keeps the frontend's dependency list small.
export default function PieChart({ data, size = 180 }: { data: Slice[]; size?: number }) {
  const total = data.reduce((sum, d) => sum + d.value, 0);
  const radius = size / 2;
  const center = size / 2;

  let angle = -90; // start at 12 o'clock
  const arcs = data
    .filter((d) => d.value > 0)
    .map((d) => {
      const fraction = total > 0 ? d.value / total : 0;
      const startAngle = angle;
      const endAngle = angle + fraction * 360;
      angle = endAngle;

      const largeArc = endAngle - startAngle > 180 ? 1 : 0;
      const [x1, y1] = pointOnCircle(center, radius, startAngle);
      const [x2, y2] = pointOnCircle(center, radius, endAngle);

      // A full circle (single status) can't be drawn as one arc path, so
      // render it as a full circle instead.
      const isFullCircle = fraction >= 0.9999;
      const path = isFullCircle
        ? `M ${center - radius} ${center} A ${radius} ${radius} 0 1 1 ${center + radius} ${center} A ${radius} ${radius} 0 1 1 ${center - radius} ${center} Z`
        : `M ${center} ${center} L ${x1} ${y1} A ${radius} ${radius} 0 ${largeArc} 1 ${x2} ${y2} Z`;

      return { ...d, path };
    });

  if (total === 0) {
    return (
      <div style={{ width: size, height: size }} className="flex items-center justify-center text-slate-400 text-sm">
        No data
      </div>
    );
  }

  return (
    <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
      {arcs.map((arc) => (
        <path key={arc.label} d={arc.path} fill={arc.color} stroke="white" strokeWidth={1} />
      ))}
    </svg>
  );
}

function pointOnCircle(center: number, radius: number, angleDeg: number): [number, number] {
  const rad = (angleDeg * Math.PI) / 180;
  return [center + radius * Math.cos(rad), center + radius * Math.sin(rad)];
}
