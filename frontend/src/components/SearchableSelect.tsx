import { useEffect, useRef, useState } from "react";

export interface SearchableSelectOption {
  value: string;
  label: string;
}

/**
 * A type-to-filter dropdown for picking one option out of a list that's too
 * long to scan as a plain <select> - built for the company picker on the
 * Invoice Reports filters, but generic (just options in, value out) so it
 * can be reused anywhere else a long list needs searching rather than
 * scrolling. No dependency added for this - it's a small enough component to
 * own directly, matching how PieChart/MonthlyValueChart avoid a charting
 * library.
 */
export default function SearchableSelect({
  options,
  value,
  onChange,
  placeholder = "Search...",
  className = "",
}: {
  options: SearchableSelectOption[];
  value: string | null;
  onChange: (value: string | null) => void;
  placeholder?: string;
  className?: string;
}) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const containerRef = useRef<HTMLDivElement>(null);

  const selected = options.find((o) => o.value === value) ?? null;

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
        setQuery("");
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const filtered = query.trim()
    ? options.filter((o) => o.label.toLowerCase().includes(query.trim().toLowerCase()))
    : options;

  return (
    <div ref={containerRef} className={`relative ${className}`}>
      <input
        value={open ? query : selected?.label ?? ""}
        onChange={(e) => {
          setQuery(e.target.value);
          if (!open) setOpen(true);
        }}
        onFocus={() => {
          setOpen(true);
          setQuery("");
        }}
        placeholder={placeholder}
        className="input w-full"
      />
      {selected && !open && (
        <button
          type="button"
          onClick={() => onChange(null)}
          title="Clear"
          className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 text-sm"
        >
          ✕
        </button>
      )}
      {open && (
        <div className="absolute z-10 mt-1 w-full max-h-60 overflow-y-auto bg-white border border-slate-200 rounded-md shadow-lg">
          <button
            type="button"
            onClick={() => {
              onChange(null);
              setOpen(false);
              setQuery("");
            }}
            className="w-full text-left px-3 py-2 text-sm text-slate-500 hover:bg-slate-50"
          >
            All
          </button>
          {filtered.length === 0 && <p className="px-3 py-2 text-sm text-slate-400">No matches</p>}
          {filtered.map((o) => (
            <button
              key={o.value}
              type="button"
              onClick={() => {
                onChange(o.value);
                setOpen(false);
                setQuery("");
              }}
              className={`w-full text-left px-3 py-2 text-sm hover:bg-slate-50 ${
                o.value === value ? "bg-emerald-50 text-emerald-700 font-medium" : "text-slate-700"
              }`}
            >
              {o.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
