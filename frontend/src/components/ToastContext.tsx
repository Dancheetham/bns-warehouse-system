import { createContext, useCallback, useContext, useRef, useState, ReactNode } from "react";

interface ToastContextValue {
  showToast: (message: string) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

/**
 * A brief, non-blocking "Saved." confirmation - shows itself, then fades
 * out on its own after a couple of seconds. Nothing to click to dismiss;
 * that's deliberate, it's a confirmation, not something that needs
 * acknowledging.
 */
export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error("useToast must be used within a ToastProvider");
  return ctx;
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [message, setMessage] = useState<string | null>(null);
  const [visible, setVisible] = useState(false);
  const timeoutRef = useRef<number | null>(null);

  const showToast = useCallback((msg: string) => {
    if (timeoutRef.current) window.clearTimeout(timeoutRef.current);
    setMessage(msg);
    setVisible(true);
    timeoutRef.current = window.setTimeout(() => setVisible(false), 2500);
  }, []);

  return (
    <ToastContext.Provider value={{ showToast }}>
      {children}
      {message && (
        <div
          aria-live="polite"
          className={`fixed bottom-6 right-6 z-50 transition-all duration-300 ${
            visible ? "opacity-100 translate-y-0" : "opacity-0 translate-y-2 pointer-events-none"
          }`}
        >
          <div className="bg-emerald-600 text-white text-sm px-4 py-2.5 rounded-lg shadow-lg flex items-center gap-2">
            <span>✓</span>
            <span>{message}</span>
          </div>
        </div>
      )}
    </ToastContext.Provider>
  );
}
