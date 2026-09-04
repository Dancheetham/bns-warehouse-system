import { useRef, useState } from "react";
import HandheldBinSelect from "./HandheldBinSelect";
import ScanInput from "./ScanInput";

interface Bin {
  id: number;
  code: string;
  description?: string;
}

interface HandheldBinPickerProps {
  bins: Bin[];
  value: string;
  onChange: (id: string) => void;
  className?: string;
}

/**
 * Half searchable picker, half scan-a-bin-code input - for right now, staff
 * can type a bin's code into the scan side manually; once bins have printed
 * barcodes, scanning one does exactly the same thing with zero further
 * changes needed here, since it's just matching against Location.code either
 * way, not anything scan-hardware-specific.
 */
export default function HandheldBinPicker({ bins, value, onChange, className = "" }: HandheldBinPickerProps) {
  const [scanValue, setScanValue] = useState("");
  const [scanError, setScanError] = useState<string | null>(null);
  const scanRef = useRef<HTMLInputElement>(null);

  const submitScan = () => {
    const code = scanValue.trim();
    if (!code) return;
    const match = bins.find((b) => b.code.toLowerCase() === code.toLowerCase());
    if (match) {
      onChange(String(match.id));
      setScanValue("");
      setScanError(null);
    } else {
      setScanError(`No bin found for "${code}"`);
    }
    scanRef.current?.focus();
  };

  return (
    <div className={className}>
      <div className="flex gap-2">
        <HandheldBinSelect bins={bins} value={value} onChange={onChange} className="flex-1" />
        <form
          onSubmit={(e) => {
            e.preventDefault();
            submitScan();
          }}
          className="flex-1"
        >
          <ScanInput
            ref={scanRef}
            value={scanValue}
            onChange={(e) => {
              setScanValue(e.target.value);
              setScanError(null);
            }}
            placeholder="Scan bin..."
            className="w-full bg-slate-900 border border-slate-800 rounded-xl px-4 py-3 text-lg"
          />
        </form>
      </div>
      {scanError && <p className="text-sm text-red-400 mt-1">{scanError}</p>}
    </div>
  );
}
