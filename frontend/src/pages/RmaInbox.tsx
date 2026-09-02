import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { api } from "../api/client";
import { RmaStatus, RmaSummaryView } from "../types";

const tabs: { label: string; value: RmaStatus | "" }[] = [
  { label: "Submitted", value: "SUBMITTED" },
  { label: "Approved", value: "APPROVED" },
  { label: "Received", value: "RECEIVED" },
  { label: "Rejected", value: "REJECTED" },
  { label: "All", value: "" },
];

export default function RmaInbox() {
  const navigate = useNavigate();
  const [tab, setTab] = useState<RmaStatus | "">("SUBMITTED");

  const { data: rmas, isLoading } = useQuery({
    queryKey: ["rma-list", tab],
    queryFn: async () =>
      (await api.get<RmaSummaryView[]>("/rma", { params: tab ? { status: tab } : {} })).data,
  });

  return (
    <div>
      <h2 className="text-2xl font-semibold text-slate-800 mb-2">RMAs</h2>
      <p className="text-slate-500 mb-4">
        Return requests submitted through the public RMA form, and their progress through review, approval, and
        receipt.
      </p>

      <div className="flex gap-1 mb-4 border-b border-slate-200">
        {tabs.map((t) => (
          <button
            key={t.label}
            onClick={() => setTab(t.value)}
            className={`px-3 py-2 text-sm border-b-2 -mb-px ${
              tab === t.value ? "border-emerald-600 text-emerald-700 font-medium" : "border-transparent text-slate-500"
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-slate-500 text-left">
            <tr>
              <th className="px-4 py-2">Reference</th>
              <th className="px-4 py-2">RMA Number</th>
              <th className="px-4 py-2">Customer</th>
              <th className="px-4 py-2">Items</th>
              <th className="px-4 py-2">Submitted</th>
              <th className="px-4 py-2">Flags</th>
            </tr>
          </thead>
          <tbody>
            {isLoading && (
              <tr>
                <td colSpan={6} className="px-4 py-6 text-center text-slate-400">
                  Loading...
                </td>
              </tr>
            )}
            {rmas?.length === 0 && !isLoading && (
              <tr>
                <td colSpan={6} className="px-4 py-6 text-center text-slate-400">
                  Nothing here.
                </td>
              </tr>
            )}
            {rmas?.map((r) => (
              <tr
                key={r.id}
                onClick={() => navigate(`/rmas/${r.id}`)}
                className="border-t border-slate-100 hover:bg-slate-50 cursor-pointer"
              >
                <td className="px-4 py-2 font-mono text-xs">{r.publicReference}</td>
                <td className="px-4 py-2 font-medium">{r.rmaNumber ?? "—"}</td>
                <td className="px-4 py-2">{r.customerName}</td>
                <td className="px-4 py-2">{r.itemCount}</td>
                <td className="px-4 py-2 text-slate-500">{new Date(r.submittedAt).toLocaleDateString("en-GB")}</td>
                <td className="px-4 py-2 space-x-1">
                  {r.anyFaulty && <span className="text-xs px-2 py-0.5 rounded-full bg-amber-100 text-amber-700">Faulty</span>}
                  {r.anyUnmatched && <span className="text-xs px-2 py-0.5 rounded-full bg-red-100 text-red-700">Unmatched</span>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
