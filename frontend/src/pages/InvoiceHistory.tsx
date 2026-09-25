import { useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useSearchParams } from "react-router-dom";
import { api } from "../api/client";
import { formatDate, inDateRange } from "../utils/format";
import DateRangePicker from "../components/DateRangePicker";
import { InvoiceHistoryView, InvoiceType } from "../types";

const money = (v: number) => `£${v.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

const TYPE_STYLES: Record<InvoiceType, string> = {
  INVOICE: "bg-slate-100 text-slate-600",
  CREDIT_NOTE: "bg-red-100 text-red-700",
};

export default function InvoiceHistory() {
  const [searchParams] = useSearchParams();
  // A link from an order's own screen (OrderEdit's "Invoice History" link,
  // once that order has actually been invoiced) lands here with ?order=
  // set, pre-filtering to just that order number rather than dumping the
  // whole history on screen.
  const [search, setSearch] = useState(() => searchParams.get("order") ?? "");
  const [typeFilter, setTypeFilter] = useState<InvoiceType | "">("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");

  useEffect(() => {
    const order = searchParams.get("order");
    if (order) setSearch(order);
  }, [searchParams]);

  const { data: invoices, isLoading } = useQuery({
    queryKey: ["invoice-history"],
    queryFn: async () => (await api.get<InvoiceHistoryView[]>("/invoices/history")).data,
  });

  const filtered = useMemo(() => {
    const list = invoices ?? [];
    const term = search.trim().toLowerCase();
    let matches = term
      ? list.filter(
          (inv) =>
            inv.companyName.toLowerCase().includes(term) ||
            String(inv.invoiceNumber).includes(term) ||
            inv.orderNumbers.some((n) => n.toLowerCase().includes(term))
        )
      : list;
    if (typeFilter) {
      matches = matches.filter((inv) => inv.invoiceType === typeFilter);
    }
    if (from || to) {
      matches = matches.filter((inv) => inDateRange(inv.generationDate, from, to));
    }
    return matches;
  }, [invoices, search, typeFilter, from, to]);

  return (
    <div>
      <div className="flex justify-between items-center mb-4">
        <div>
          <h2 className="text-2xl font-semibold text-slate-800">Invoice History</h2>
          <p className="text-slate-500">Every invoice and credit note ever generated.</p>
        </div>
      </div>

      <div className="flex gap-3 mb-4 flex-wrap items-end">
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search by company, invoice number or order number..."
          className="flex-1 min-w-[260px] border-2 border-slate-300 focus:border-emerald-500 rounded-lg px-4 py-2.5 outline-none text-sm"
        />
        <select value={typeFilter} onChange={(e) => setTypeFilter(e.target.value as InvoiceType | "")} className="input">
          <option value="">All types</option>
          <option value="INVOICE">Invoices</option>
          <option value="CREDIT_NOTE">Credit notes</option>
        </select>
        <DateRangePicker from={from} to={to} onFromChange={setFrom} onToChange={setTo} />
        <span className="text-sm text-slate-500 ml-auto">{filtered.length} record(s)</span>
      </div>

      <div className="bg-white rounded-lg shadow-sm border border-slate-200 overflow-auto">
        <table className="w-full text-sm">
          <thead className="text-left text-slate-500 border-b border-slate-200 sticky top-0 bg-white">
            <tr>
              <th className="px-3 py-2">Invoice #</th>
              <th className="px-3 py-2">Type</th>
              <th className="px-3 py-2">Date</th>
              <th className="px-3 py-2">Company</th>
              <th className="px-3 py-2">Order(s)</th>
              <th className="px-3 py-2 text-right">Net</th>
              <th className="px-3 py-2 text-right">VAT</th>
              <th className="px-3 py-2 text-right">Gross</th>
              <th className="px-3 py-2"></th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {isLoading && (
              <tr>
                <td colSpan={9} className="px-3 py-4 text-slate-400">
                  Loading...
                </td>
              </tr>
            )}
            {!isLoading && filtered.length === 0 && (
              <tr>
                <td colSpan={9} className="px-3 py-4 text-slate-400">
                  {search ? `No invoices match "${search}".` : "No invoices generated yet."}
                </td>
              </tr>
            )}
            {filtered.map((inv) => (
              <tr key={inv.invoiceId} className="hover:bg-slate-50">
                <td className="px-3 py-2 font-medium text-slate-800">{inv.invoiceNumber}</td>
                <td className="px-3 py-2">
                  <span className={`text-xs px-2 py-0.5 rounded font-medium whitespace-nowrap ${TYPE_STYLES[inv.invoiceType]}`}>
                    {inv.invoiceType === "CREDIT_NOTE" ? "Credit Note" : "Invoice"}
                  </span>
                </td>
                <td className="px-3 py-2 whitespace-nowrap">{formatDate(inv.generationDate)}</td>
                <td className="px-3 py-2">{inv.companyName}</td>
                <td className="px-3 py-2">{inv.orderNumbers.join(", ") || "-"}</td>
                <td className="px-3 py-2 text-right">{money(inv.netTotal)}</td>
                <td className="px-3 py-2 text-right">{money(inv.vatTotal)}</td>
                <td className="px-3 py-2 text-right font-medium">{money(inv.grandTotal)}</td>
                <td className="px-3 py-2 text-right">
                  <a
                    href={`/api/invoices/${inv.invoiceId}/pdf`}
                    target="_blank"
                    rel="noreferrer"
                    className="text-emerald-600 hover:underline"
                  >
                    PDF
                  </a>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
