import { useEffect, useRef, useState } from "react";
import { naturalCompare } from "../utils/naturalSort";

interface Bin {
  id: number;
  code: string;
  description?: string;
}

interface BinSelectProps {
  bins: Bin[];
  value: string;
  onChange: (id: string) => void;
  placeholder?: string;
  className?: string;
}

/**
 * A native <select> with 200+ bins is genuinely painful to use - a giant
 * alphabetically-sorted (so "10" sits before "2") scrolling list. This sorts
 * naturally (1, 1A, 1B, 2, 2A ... 10, 10A) and adds a search box, without
 * pulling in a combobox library for one component.
 */
export default function BinSelect({ bins, value, onChange, placeholder = "Select a bin...", className = "" }: BinSelectProps) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const containerRef = useRef<HTMLDivElement>(null);

  const sorted = [...bins].sort((a, b) => naturalCompare(a.code, b.code));
  const filtered = query.trim()
    ? sorted.filter(
        (b) =>
          b.code.toLowerCase().includes(query.trim().toLowerCase()) ||
          (b.description ?? "").toLowerCase().includes(query.trim().toLowerCase())
      )
    : sorted;

  const selected = bins.find((b) => String(b.id) === value);

  useEffect(() => {
    const handleClick = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
        setQuery("");
      }
    };
    document.addEventListener("mousedown", handleClick);
    return () => document.removeEventListener("mousedown", handleClick);
  }, []);

  return (
    <div ref={containerRef} className={`relative ${className}`}>
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="w-full text-left border border-slate-300 rounded-md px-3 py-2 bg-white flex justify-between items-center"
      >
        <span className={selected ? "text-slate-800" : "text-slate-400"}>
          {selected ? `${selected.code}${selected.description ? " - " + selected.description : ""}` : placeholder}
        </span>
        <span className="text-slate-400 text-xs shrink-0 ml-2">▾</span>
      </button>
      {open && (
        <div className="absolute z-20 mt-1 w-full min-w-[220px] bg-white border border-slate-200 rounded-md shadow-lg max-h-64 flex flex-col">
          <input
            autoFocus
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search bins..."
            className="border-b border-slate-100 px-3 py-2 text-sm outline-none shrink-0"
          />
          <div className="overflow-y-auto">
            {filtered.length === 0 && <p className="px-3 py-2 text-sm text-slate-400">No bins match.</p>}
            {filtered.map((b) => (
              <button
                key={b.id}
                type="button"
                onClick={() => {
                  onChange(String(b.id));
                  setOpen(false);
                  setQuery("");
                }}
                className={`w-full text-left px-3 py-2 text-sm hover:bg-slate-50 ${
                  String(b.id) === value ? "bg-emerald-50 font-medium text-emerald-700" : "text-slate-700"
                }`}
              >
                {b.code}
                {b.description && <span className="text-slate-400"> - {b.description}</span>}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
