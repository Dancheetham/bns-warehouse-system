import { useEffect, useRef, useState } from "react";
import { naturalCompare } from "../../utils/naturalSort";
import ScanInput from "./ScanInput";

interface Bin {
  id: number;
  code: string;
  description?: string;
}

interface HandheldBinPickerScreenProps {
  bins: Bin[];
  value: string;
  onSelect: (id: string) => void;
  onClose: () => void;
}

/**
 * Full screen, not a dropdown panel - a floating dropdown genuinely doesn't
 * have room to render properly on a 720px-wide handheld screen, especially
 * next to anything else on the page. One field does double duty as both
 * search and scan target: a barcode scan "types" the full code almost
 * instantly, so an exact match auto-selects immediately rather than also
 * needing a tap on the one matching result.
 */
export default function HandheldBinPickerScreen({ bins, value, onSelect, onClose }: HandheldBinPickerScreenProps) {
  const [query, setQuery] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const trimmed = query.trim();
    if (!trimmed) return;
    const exact = bins.find((b) => b.code.toLowerCase() === trimmed.toLowerCase());
    if (exact) onSelect(String(exact.id));
    // Deliberately only re-checking when the typed/scanned value itself
    // changes - bins/onSelect are stable for the lifetime of this screen.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [query]);

  const sorted = [...bins].sort((a, b) => naturalCompare(a.code, b.code));
  const filtered = query.trim()
    ? sorted.filter(
        (b) =>
          b.code.toLowerCase().includes(query.trim().toLowerCase()) ||
          (b.description ?? "").toLowerCase().includes(query.trim().toLowerCase())
      )
    : sorted;

  return (
    <div className="fixed inset-0 z-50 bg-slate-950 flex flex-col">
      <div className="px-4 py-4 border-b border-slate-800 flex items-center gap-3 shrink-0">
        <button type="button" onClick={onClose} className="text-slate-400 active:text-slate-200 text-2xl shrink-0 px-1">
          ✕
        </button>
        <ScanInput
          ref={inputRef}
          autoFocus
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search or scan a bin..."
          className="flex-1 bg-slate-900 border border-slate-800 rounded-xl px-4 py-3 text-lg"
        />
      </div>
      <div className="flex-1 overflow-y-auto">
        {filtered.length === 0 && <p className="px-4 py-6 text-slate-500">No bins match.</p>}
        {filtered.map((b) => (
          <button
            key={b.id}
            type="button"
            onClick={() => onSelect(String(b.id))}
            className={`w-full text-left px-4 py-4 border-b border-slate-900 active:bg-slate-900 ${
              String(b.id) === value ? "bg-emerald-950 text-emerald-400 font-medium" : "text-white"
            }`}
          >
            {b.code}
            {b.description && <span className="text-slate-500"> - {b.description}</span>}
          </button>
        ))}
      </div>
    </div>
  );
}
