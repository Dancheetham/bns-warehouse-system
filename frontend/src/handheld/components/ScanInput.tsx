import { forwardRef, InputHTMLAttributes, useRef, useState } from "react";

type ScanInputProps = InputHTMLAttributes<HTMLInputElement>;

/**
 * Defaults to inputMode="none" so the on-screen keyboard doesn't pop up on
 * focus - a barcode scanner (hardware or camera) types into this exactly
 * like a keyboard would, and popping up a software keyboard covering half
 * the screen is wrong for a field that's filled by a scan ~99% of the time.
 * Tap the keyboard icon for the occasional manual-entry case.
 *
 * Pass the same className you'd give a plain <input> for background/border/
 * padding/text styling - it's applied to the wrapping box, not the input
 * itself, since the keyboard icon needs to sit inside that same box.
 */
const ScanInput = forwardRef<HTMLInputElement, ScanInputProps>(({ className, ...props }, forwardedRef) => {
  const [keyboardEnabled, setKeyboardEnabled] = useState(false);
  const localRef = useRef<HTMLInputElement | null>(null);

  const setRefs = (node: HTMLInputElement | null) => {
    localRef.current = node;
    if (typeof forwardedRef === "function") forwardedRef(node);
    else if (forwardedRef) (forwardedRef as React.MutableRefObject<HTMLInputElement | null>).current = node;
  };

  return (
    <div className={`flex items-center gap-2 min-w-0 ${className ?? ""}`}>
      <input
        {...props}
        ref={setRefs}
        inputMode={keyboardEnabled ? undefined : "none"}
        className="flex-1 min-w-0 bg-transparent outline-none placeholder:text-slate-500"
      />
      <button
        type="button"
        onClick={() => {
          setKeyboardEnabled(true);
          // Re-focus after the inputMode change actually lands - focusing in
          // the same tick as the state update can race the browser and skip
          // showing the keyboard.
          requestAnimationFrame(() => localRef.current?.focus());
        }}
        className="shrink-0 text-slate-400 active:text-slate-200"
        aria-label="Show keyboard for manual entry"
      >
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
          <rect x="2" y="6" width="20" height="12" rx="2" />
          <path d="M6 10h.01M10 10h.01M14 10h.01M18 10h.01M6 14h12" strokeLinecap="round" />
        </svg>
      </button>
    </div>
  );
});
ScanInput.displayName = "ScanInput";
export default ScanInput;
