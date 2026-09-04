import { useState } from "react";
import HandheldBinPickerScreen from "./HandheldBinPickerScreen";

interface Bin {
  id: number;
  code: string;
  description?: string;
}

interface HandheldBinFieldProps {
  bins: Bin[];
  value: string;
  onChange: (id: string) => void;
  placeholder?: string;
  className?: string;
}

export default function HandheldBinField({
  bins,
  value,
  onChange,
  placeholder = "Select a bin...",
  className = "",
}: HandheldBinFieldProps) {
  const [open, setOpen] = useState(false);
  const selected = bins.find((b) => String(b.id) === value);

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className={`w-full text-left bg-slate-900 border border-slate-800 rounded-xl px-4 py-3 text-lg flex justify-between items-center ${className}`}
      >
        <span className={selected ? "text-white" : "text-slate-500"}>
          {selected ? `${selected.code}${selected.description ? " - " + selected.description : ""}` : placeholder}
        </span>
        <span className="text-slate-500 shrink-0 ml-2">›</span>
      </button>
      {open && (
        <HandheldBinPickerScreen
          bins={bins}
          value={value}
          onSelect={(id) => {
            onChange(id);
            setOpen(false);
          }}
          onClose={() => setOpen(false)}
        />
      )}
    </>
  );
}
