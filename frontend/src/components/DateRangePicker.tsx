// Small shared "From"/"To" date pair, styled to match the inline filter bars
// already used on Delivery History, Sales Activity etc. Deliberately just
// two <input type="date"> - the values are plain yyyy-mm-dd strings, ready
// either for a server-side query param (Delivery History) or for
// utils/format.ts's inDateRange() against an already-loaded list (every
// other page that uses this).
interface DateRangePickerProps {
  from: string;
  to: string;
  onFromChange: (value: string) => void;
  onToChange: (value: string) => void;
  fromLabel?: string;
  toLabel?: string;
}

export default function DateRangePicker({
  from,
  to,
  onFromChange,
  onToChange,
  fromLabel = "From",
  toLabel = "To",
}: DateRangePickerProps) {
  return (
    <>
      <div>
        <label className="block text-xs font-medium text-slate-500 mb-1">{fromLabel}</label>
        <input type="date" value={from} onChange={(e) => onFromChange(e.target.value)} className="input" />
      </div>
      <div>
        <label className="block text-xs font-medium text-slate-500 mb-1">{toLabel}</label>
        <input type="date" value={to} onChange={(e) => onToChange(e.target.value)} className="input" />
      </div>
    </>
  );
}
