import { useRef, useState } from "react";
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
 * have room to render properly on a 720px-wide handheld screen. One field
 * does double duty as both search (live, as you type - matches how search
 * works elsewhere in this system) and scan target: only matches on Enter,
 * not on every keystroke - matching partway through typing a code (e.g. "3"
 * matching bin "3" while still typing "3B") would otherwise select the
 * wrong bin before you'd finished. A scanner naturally sends Enter right
 * after the scanned value, so this is the correct behaviour for a genuine
 * scan too, not just a typed search - no separate handling needed for either.
 */
export default function HandheldBinPickerScreen({ bins, value, onSelect, onClose }: HandheldBinPickerScreenProps) {
  const [query, setQuery] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);

  const sorted = [...bins].sort((a, b) => naturalCompare(a.code, b.code));
  const term = query.trim().toLowerCase();
  const filtered = term
    ? sorted.filter((b) => b.code.toLowerCase().startsWith(term) || (b.description ?? "").toLowerCase().includes(term))
    : sorted;

  const submitSearch = () => {
    if (!term) return;
    const exact = bins.find((b) => b.code.toLowerCase() === term);
    if (exact) onSelect(String(exact.id));
  };

  return (
    <div className="fixed inset-0 z-50 bg-slate-950 flex flex-col">
      <div className="px-4 py-4 border-b border-slate-800 flex items-center gap-3 shrink-0">
        <button type="button" onClick={onClose} className="text-slate-400 active:text-slate-200 text-2xl shrink-0 px-1">
          ✕
        </button>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            submitSearch();
          }}
          className="flex-1 min-w-0"
        >
          <ScanInput
            ref={inputRef}
            autoFocus
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search or scan a bin..."
            className="w-full bg-slate-900 border border-slate-800 rounded-xl px-4 py-3 text-lg"
          />
        </form>
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
