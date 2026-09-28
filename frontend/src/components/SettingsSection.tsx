import { useState } from "react";

/**
 * One collapsible block of settings. Everything starts collapsed so the page
 * opens as a short list of headings you can scan, rather than a very long
 * scroll - open just the area you came here to change. Collapsed state is
 * per-section and deliberately not remembered between visits: the useful
 * default is always "show me the list", not "show me whatever I left open
 * last time".
 *
 * Shared between Settings.tsx (global/admin settings) and
 * AccountSettings.tsx (per-user settings) - split out from Settings.tsx in
 * v0.107 when the per-user sections ("My Email", "Customisation") moved to
 * their own page.
 */
export default function SettingsSection({
  title,
  description,
  defaultOpen = false,
  children,
}: {
  title: string;
  description?: React.ReactNode;
  defaultOpen?: boolean;
  children: React.ReactNode;
}) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <div className="bg-white border border-slate-200 rounded-lg mb-4">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="w-full flex items-center justify-between gap-3 px-5 py-4 text-left hover:bg-slate-50 rounded-lg"
      >
        <span className="font-medium text-slate-800">{title}</span>
        <span className={`text-slate-400 text-xs transition-transform ${open ? "rotate-90" : ""}`}>▶</span>
      </button>
      {open && (
        <div className="px-5 pb-5">
          {description && <p className="text-sm text-slate-500 mb-4">{description}</p>}
          <div className="space-y-4">{children}</div>
        </div>
      )}
    </div>
  );
}
