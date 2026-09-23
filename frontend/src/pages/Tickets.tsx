import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { api } from "../api/client";
import { TicketStatus, TicketSummaryView } from "../types";

const STATUSES: TicketStatus[] = ["IN_PROGRESS", "ON_HOLD", "CLOSED", "COMPLETE"];

const STATUS_LABEL: Record<TicketStatus, string> = {
  IN_PROGRESS: "In Progress",
  ON_HOLD: "On Hold",
  CLOSED: "Closed",
  COMPLETE: "Complete",
};

const STATUS_STYLE: Record<TicketStatus, string> = {
  IN_PROGRESS: "bg-amber-50 text-amber-700 border-amber-200",
  ON_HOLD: "bg-slate-100 text-slate-600 border-slate-200",
  CLOSED: "bg-emerald-50 text-emerald-700 border-emerald-200",
  COMPLETE: "bg-emerald-50 text-emerald-700 border-emerald-200",
};

export function talkTimeLabel(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return h > 0 ? `${h}h ${m}m` : `${m}m`;
}

export default function Tickets() {
  const navigate = useNavigate();
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<TicketStatus | "">("");

  const { data: tickets, isLoading } = useQuery({
    queryKey: ["tickets"],
    queryFn: async () => (await api.get<TicketSummaryView[]>("/tickets")).data,
  });

  const filtered = useMemo(() => {
    if (!tickets) return [];
    const term = search.trim().toLowerCase();
    return tickets.filter((t) => {
      if (statusFilter && t.status !== statusFilter) return false;
      if (!term) return true;
      return [t.ticketNumber, t.title, t.callerName, t.companyName, t.orderNumber, t.phone, t.email]
        .filter(Boolean)
        .some((v) => v!.toLowerCase().includes(term));
    });
  }, [tickets, search, statusFilter]);

  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <div>
          <h2 className="text-2xl font-semibold text-slate-800">Support Tickets</h2>
          <p className="text-slate-500">Calls and emails logged against a customer, and optionally a specific order.</p>
        </div>
        <button
          onClick={() => navigate("/tickets/new")}
          className="bg-slate-800 text-white text-sm px-4 py-2 rounded-md hover:bg-slate-700"
        >
          New Ticket
        </button>
      </div>

      <div className="flex gap-3 mb-4 flex-wrap">
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search ticket #, title, caller, company, order..."
          className="input flex-1 min-w-[240px]"
        />
        <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value as TicketStatus | "")} className="input w-48">
          <option value="">All statuses</option>
          {STATUSES.map((s) => (
            <option key={s} value={s}>
              {STATUS_LABEL[s]}
            </option>
          ))}
        </select>
      </div>

      <div className="bg-white rounded-lg shadow-sm border border-slate-200 overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-left text-slate-500">
            <tr>
              <th className="px-4 py-2.5">Ticket #</th>
              <th className="px-4 py-2.5">Title</th>
              <th className="px-4 py-2.5">Caller</th>
              <th className="px-4 py-2.5">Company</th>
              <th className="px-4 py-2.5">Order</th>
              <th className="px-4 py-2.5">Status</th>
              <th className="px-4 py-2.5">Talk time</th>
              <th className="px-4 py-2.5">Opened</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {isLoading && (
              <tr>
                <td colSpan={8} className="px-4 py-4 text-slate-400 text-center">
                  Loading...
                </td>
              </tr>
            )}
            {!isLoading && filtered.length === 0 && (
              <tr>
                <td colSpan={8} className="px-4 py-4 text-slate-400 text-center">
                  No tickets found.
                </td>
              </tr>
            )}
            {filtered.map((t) => (
              <tr
                key={t.id}
                onClick={() => navigate(`/tickets/${t.id}`)}
                className="cursor-pointer hover:bg-slate-50"
              >
                <td className="px-4 py-2.5 font-medium text-slate-800">{t.ticketNumber}</td>
                <td className="px-4 py-2.5 text-slate-700">{t.title}</td>
                <td className="px-4 py-2.5 text-slate-600">{t.callerName ?? "-"}</td>
                <td className="px-4 py-2.5 text-slate-600">{t.companyName ?? "-"}</td>
                <td className="px-4 py-2.5 text-slate-600">{t.orderNumber ?? "-"}</td>
                <td className="px-4 py-2.5">
                  <span className={`text-xs px-2 py-0.5 rounded-full border ${STATUS_STYLE[t.status]}`}>
                    {STATUS_LABEL[t.status]}
                  </span>
                </td>
                <td className="px-4 py-2.5 text-slate-600">{talkTimeLabel(t.talkTimeMinutes)}</td>
                <td className="px-4 py-2.5 text-slate-500">{new Date(t.createdAt).toLocaleDateString("en-GB")}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export { STATUSES as TICKET_STATUSES, STATUS_LABEL as TICKET_STATUS_LABEL, STATUS_STYLE as TICKET_STATUS_STYLE };
