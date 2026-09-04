import { useEffect, useRef, useState } from "react";
import { naturalCompare } from "../../utils/naturalSort";

interface Bin {
  id: number;
  code: string;
  description?: string;
}

interface HandheldBinSelectProps {
  bins: Bin[];
  value: string;
  onChange: (id: string) => void;
  placeholder?: string;
  className?: string;
}

export default function HandheldBinSelect({
  bins,
  value,
  onChange,
  placeholder = "Select a bin...",
  className = "",
}: HandheldBinSelectProps) {
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
        className="w-full text-left bg-slate-900 border border-slate-800 rounded-xl px-4 py-3 text-lg flex justify-between items-center"
      >
        <span className={selected ? "text-white" : "text-slate-500"}>
          {selected ? `${selected.code}${selected.description ? " - " + selected.description : ""}` : placeholder}
        </span>
        <span className="text-slate-500 shrink-0 ml-2">▾</span>
      </button>
      {open && (
        <div className="absolute z-20 mt-1 w-full bg-slate-900 border border-slate-700 rounded-xl shadow-lg max-h-72 flex flex-col">
          <input
            autoFocus
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search bins..."
            className="bg-slate-900 border-b border-slate-800 px-4 py-3 text-white outline-none shrink-0"
          />
          <div className="overflow-y-auto">
            {filtered.length === 0 && <p className="px-4 py-3 text-slate-500">No bins match.</p>}
            {filtered.map((b) => (
              <button
                key={b.id}
                type="button"
                onClick={() => {
                  onChange(String(b.id));
                  setOpen(false);
                  setQuery("");
                }}
                className={`w-full text-left px-4 py-3 active:bg-slate-800 ${
                  String(b.id) === value ? "bg-emerald-950 text-emerald-400 font-medium" : "text-white"
                }`}
              >
                {b.code}
                {b.description && <span className="text-slate-500"> - {b.description}</span>}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
