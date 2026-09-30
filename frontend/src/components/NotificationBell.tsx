import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { api } from "../api/client";
import { Notification } from "../types";
import { formatDateTime } from "../utils/format";

function BellIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" />
      <path d="M13.73 21a2 2 0 0 1-3.46 0" />
    </svg>
  );
}

// Floating, fixed top-right - this app's Layout has no top header bar to
// live in (left sidebar only), so it's pinned over the content instead.
export default function NotificationBell() {
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const { data: unreadCount } = useQuery({
    queryKey: ["notifications-unread-count"],
    queryFn: async () => (await api.get<{ count: number }>("/notifications/unread-count")).data.count,
    refetchInterval: 30000,
  });

  const { data: notifications, isLoading } = useQuery({
    queryKey: ["notifications"],
    queryFn: async () => (await api.get<Notification[]>("/notifications")).data,
    enabled: open,
  });

  // Clears the badge the moment the bell is opened - Dan's explicit choice,
  // not deferred until each notification's target page has actually been
  // visited. Optimistically zeroes the cached count so the badge disappears
  // instantly rather than waiting on the round trip.
  const markAllReadMutation = useMutation({
    mutationFn: async () => api.post("/notifications/mark-all-read"),
    onSuccess: () => {
      queryClient.setQueryData(["notifications-unread-count"], 0);
      queryClient.invalidateQueries({ queryKey: ["notifications"] });
    },
  });

  const toggleOpen = () => {
    const next = !open;
    setOpen(next);
    if (next && (unreadCount ?? 0) > 0) {
      markAllReadMutation.mutate();
    }
  };

  const handleClick = (n: Notification) => {
    setOpen(false);
    if (n.link) navigate(n.link);
  };

  return (
    <div className="fixed top-4 right-4 z-50">
      <button
        onClick={toggleOpen}
        title="Notifications"
        className="relative p-2.5 rounded-full bg-white border border-slate-200 shadow-sm hover:bg-slate-50 text-slate-500 hover:text-slate-700"
      >
        <BellIcon />
        {!!unreadCount && unreadCount > 0 && (
          <span className="absolute -top-1 -right-1 bg-red-500 text-white text-[10px] leading-none rounded-full min-w-[16px] h-4 px-1 flex items-center justify-center font-semibold">
            {unreadCount > 99 ? "99+" : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <>
          {/* Click-outside-to-close backdrop - simpler than a ref/listener setup for a small dropdown like this. */}
          <div className="fixed inset-0 z-40" onClick={() => setOpen(false)} />
          <div className="absolute right-0 mt-2 w-80 bg-white rounded-lg shadow-lg border border-slate-200 max-h-96 overflow-auto z-50">
            <div className="px-3 py-2 border-b border-slate-100 text-xs font-semibold text-slate-500 uppercase tracking-wide sticky top-0 bg-white">
              Notifications
            </div>
            {isLoading && <p className="px-3 py-4 text-sm text-slate-400">Loading...</p>}
            {!isLoading && (!notifications || notifications.length === 0) && (
              <p className="px-3 py-4 text-sm text-slate-400">Nothing here.</p>
            )}
            {notifications?.map((n) => (
              <button
                key={n.id}
                onClick={() => handleClick(n)}
                disabled={!n.link}
                className="w-full text-left px-3 py-2.5 border-b border-slate-50 last:border-0 hover:bg-slate-50 disabled:hover:bg-white disabled:cursor-default"
              >
                <p className="text-sm text-slate-800">{n.message}</p>
                <p className="text-xs text-slate-400 mt-0.5">{formatDateTime(n.createdAt)}</p>
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
