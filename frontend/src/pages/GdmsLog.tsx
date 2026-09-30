import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useSearchParams } from "react-router-dom";
import { api } from "../api/client";
import { formatDateTime, inDateRange } from "../utils/format";
import DateRangePicker from "../components/DateRangePicker";
import { GdmsSyncLog } from "../types";

type StatusFilter = "" | "SUCCESS" | "FAILURE";
type OperationFilter = "" | "ASSIGN" | "RECALL";

export default function GdmsLog() {
  // Pre-filled from a notification bell link (?from=...&to=...&status=FAILURE)
  // when arriving that way - read once on mount, not kept in sync afterwards
  // (changing the filters in the UI doesn't rewrite the URL).
  const [searchParams] = useSearchParams();
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>(() => {
    const status = searchParams.get("status");
    return status === "SUCCESS" || status === "FAILURE" ? status : "";
  });
  const [operationFilter, setOperationFilter] = useState<OperationFilter>("");
  const [from, setFrom] = useState(() => searchParams.get("from") ?? "");
  const [to, setTo] = useState(() => searchParams.get("to") ?? "");

  const { data: entries, isLoading } = useQuery({
    queryKey: ["gdms-sync-log"],
    queryFn: async () => (await api.get<GdmsSyncLog[]>("/gdms-sync-log")).data,
    refetchInterval: 30000,
  });

  const filtered = useMemo(() => {
    const list = entries ?? [];
    const term = search.trim().toLowerCase();
    let matches = term
      ? list.filter(
          (e) =>
            e.macAddress.toLowerCase().includes(term) ||
            (e.orderNumber ?? "").toLowerCase().includes(term) ||
            e.source.toLowerCase().includes(term) ||
            (e.channelName ?? "").toLowerCase().includes(term) ||
            (e.errorReason ?? "").toLowerCase().includes(term)
        )
      : list;

    if (statusFilter) {
      matches = matches.filter((e) => e.status === statusFilter);
    }
    if (operationFilter) {
      matches = matches.filter((e) => e.operation === operationFilter);
    }
    if (from || to) {
      matches = matches.filter((e) => inDateRange(e.attemptedAt, from, to));
    }

    return matches;
  }, [entries, search, statusFilter, operationFilter, from, to]);

  return (
    <div>
      <div className="flex justify-between items-center mb-4">
        <div>
          <h2 className="text-2xl font-semibold text-slate-800">GDMS Sync Log</h2>
          <p className="text-slate-500">
            Every MAC address attempted against GDMS - scheduled and manual channel assignments, and recalls when a
            unit is returned to stock. One row per MAC attempted, newest first.
          </p>
        </div>
      </div>

      <div className="flex gap-3 mb-4 flex-wrap items-end">
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search by MAC, order number, source or channel..."
          className="flex-1 min-w-[260px] border-2 border-slate-300 focus:border-emerald-500 rounded-lg px-4 py-2.5 outline-none text-sm"
        />
        <select
          value={operationFilter}
          onChange={(e) => setOperationFilter(e.target.value as OperationFilter)}
          className="input"
        >
          <option value="">Assign &amp; Recall</option>
          <option value="ASSIGN">Assign only</option>
          <option value="RECALL">Recall only</option>
        </select>
        <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value as StatusFilter)} className="input">
          <option value="">All statuses</option>
          <option value="SUCCESS">Success</option>
          <option value="FAILURE">Failure</option>
        </select>
        <DateRangePicker from={from} to={to} onFromChange={setFrom} onToChange={setTo} />
        <span className="text-sm text-slate-500 ml-auto">{filtered.length} attempt(s)</span>
      </div>

      <div className="bg-white rounded-lg shadow-sm border border-slate-200 overflow-auto">
        <table className="w-full text-sm">
          <thead className="text-left text-slate-500 border-b border-slate-200 sticky top-0 bg-white">
            <tr>
              <th className="px-3 py-2">Date</th>
              <th className="px-3 py-2">Operation</th>
              <th className="px-3 py-2">Source</th>
              <th className="px-3 py-2">Order #</th>
              <th className="px-3 py-2">MAC</th>
              <th className="px-3 py-2">Channel</th>
              <th className="px-3 py-2">Status</th>
              <th className="px-3 py-2">Error</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {isLoading && (
              <tr>
                <td colSpan={8} className="px-3 py-4 text-slate-400">
                  Loading...
                </td>
              </tr>
            )}
            {!isLoading && filtered.length === 0 && (
              <tr>
                <td colSpan={8} className="px-3 py-4 text-slate-400">
                  {search ? `No attempts match "${search}".` : "No GDMS sync attempts recorded yet."}
                </td>
              </tr>
            )}
            {filtered.map((e) => (
              <tr key={e.id}>
                <td className="px-3 py-2 text-slate-500 whitespace-nowrap">{formatDateTime(e.attemptedAt)}</td>
                <td className="px-3 py-2">
                  <span
                    className={`text-xs px-2 py-0.5 rounded font-medium ${
                      e.operation === "ASSIGN" ? "bg-blue-100 text-blue-700" : "bg-purple-100 text-purple-700"
                    }`}
                  >
                    {e.operation}
                  </span>
                </td>
                <td className="px-3 py-2 text-slate-600">{e.source}</td>
                <td className="px-3 py-2">{e.orderNumber ?? "-"}</td>
                <td className="px-3 py-2 font-mono text-slate-700">{e.macAddress}</td>
                <td className="px-3 py-2 text-slate-600">{e.channelName ?? "-"}</td>
                <td className="px-3 py-2">
                  <span
                    className={`text-xs px-2 py-0.5 rounded font-medium ${
                      e.status === "SUCCESS" ? "bg-emerald-100 text-emerald-700" : "bg-red-100 text-red-700"
                    }`}
                  >
                    {e.status}
                  </span>
                </td>
                <td className="px-3 py-2 text-slate-500 text-xs">{e.errorReason ?? "-"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
