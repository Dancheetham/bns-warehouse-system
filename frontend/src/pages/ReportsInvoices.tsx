import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api } from "../api/client";
import { downloadFile, ReportCard } from "../components/ReportCard";
import SearchableSelect from "../components/SearchableSelect";
import { CompanyView } from "../types";

export default function ReportsInvoices() {
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [includeInvoices, setIncludeInvoices] = useState(true);
  const [includeCredits, setIncludeCredits] = useState(true);
  const [companyId, setCompanyId] = useState<string | null>(null);

  const { data: companies } = useQuery({
    queryKey: ["companies"],
    queryFn: async () => (await api.get<CompanyView[]>("/companies")).data,
  });

  const buildQuery = () => {
    const params = new URLSearchParams();
    if (from) params.set("from", from);
    if (to) params.set("to", to);
    params.set("includeInvoices", String(includeInvoices));
    params.set("includeCredits", String(includeCredits));
    if (companyId) params.set("companyId", companyId);
    return params.toString();
  };

  return (
    <div>
      <h2 className="text-2xl font-semibold text-slate-800 mb-2">Invoice Reports</h2>
      <p className="text-slate-500 mb-6">
        Generated live from current data and downloaded as an Excel spreadsheet - the same values behind the
        Dashboard's Invoiced Values by Month chart.
      </p>

      <div className="grid md:grid-cols-2 gap-4">
        <ReportCard
          title="Invoiced Values"
          description="One row per order - invoice date, customer, company, goods/delivery/total net. Filter by date range, invoice and/or credit, and company below."
          onDownload={() => {
            if (!includeInvoices && !includeCredits) return;
            const query = buildQuery();
            return downloadFile(`/reports/invoices?${query}`, "invoice-report.xlsx");
          }}
          extra={
            <div className="space-y-3 mb-4">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-medium text-slate-500 mb-1">Invoice date from</label>
                  <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} className="input w-full" />
                </div>
                <div>
                  <label className="block text-xs font-medium text-slate-500 mb-1">Invoice date to</label>
                  <input type="date" value={to} onChange={(e) => setTo(e.target.value)} className="input w-full" />
                </div>
              </div>

              <div className="flex items-center gap-4">
                <label className="flex items-center gap-1.5 text-sm text-slate-600">
                  <input
                    type="checkbox"
                    checked={includeInvoices}
                    onChange={(e) => setIncludeInvoices(e.target.checked)}
                  />
                  Show invoices
                </label>
                <label className="flex items-center gap-1.5 text-sm text-slate-600">
                  <input
                    type="checkbox"
                    checked={includeCredits}
                    onChange={(e) => setIncludeCredits(e.target.checked)}
                  />
                  Show credits
                </label>
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-500 mb-1">Company</label>
                <SearchableSelect
                  options={(companies ?? []).map((c) => ({ value: String(c.id), label: c.name }))}
                  value={companyId}
                  onChange={setCompanyId}
                  placeholder="All companies"
                />
              </div>

              {!includeInvoices && !includeCredits && (
                <p className="text-xs text-red-500">Select at least one of Invoices or Credits to download.</p>
              )}
            </div>
          }
        />
      </div>
    </div>
  );
}
