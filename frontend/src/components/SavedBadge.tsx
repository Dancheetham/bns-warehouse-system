/**
 * The small white "Saved." pill shown next to a Save button - the
 * confirmation a save confirmation belongs next to, not a corner toast.
 *
 * Before v0.116 most Save buttons fired the global green corner toast
 * (ToastContext) for this; Settings.tsx was the one page that additionally
 * had this pill of its own, which meant clicking Save Settings showed BOTH
 * at once, directly on top of each other (both are fixed bottom-right).
 * v0.116 standardised on this pill everywhere a Save button's form/page
 * stays on screen after saving, and dropped the green toast for those
 * "Saved." confirmations - it's still used for things that aren't a save
 * confirmation next to a button (e.g. "Deleted.", "DPD shipment booked.",
 * or a save that immediately navigates/unmounts the button, like Goods In
 * or the inline-edit rows on Contacts/Companies).
 */
export default function SavedBadge({ show, label = "Saved." }: { show: boolean; label?: string }) {
  if (!show) return null;
  return (
    <span className="text-sm text-emerald-700 bg-white border border-emerald-200 px-3 py-1.5 rounded-full shadow-sm">
      {label}
    </span>
  );
}
