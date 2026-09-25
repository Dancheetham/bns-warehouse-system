import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../api/client";
import { useToast } from "../components/ToastContext";
import { formatDate } from "../utils/format";
import {
  ApplyCreditBalanceRequest,
  CompanyView,
  OutstandingInvoiceView,
  RecordInvoicePaymentRequest,
  RecordPaymentResult,
} from "../types";

const money = (v: number) => `£${v.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

type StatusFilter = "" | "CLOSE_TO_TERMS" | "OVER_TERMS" | "PART_PAID";

// Orange from 0 days out to the day it hits terms, red from the day it hits
// terms onward. Matches Dan's "5 days away... orange, red once equal to or
// over" wording - daysOverTerms is already 0 right up to and including the
// due day itself (see PaymentTrackingService/OutstandingInvoiceView on the
// backend), so >0 is enough to mean "actually over".
function rowStyle(inv: OutstandingInvoiceView): string {
  if (inv.daysOverTerms > 0) return "bg-red-50";
  const daysToTerms = inv.paymentTermsDays - inv.daysSinceInvoiced;
  if (daysToTerms <= 5) return "bg-amber-50";
  return "";
}

function PaymentRow({ invoice }: { invoice: OutstandingInvoiceView }) {
  const { showToast } = useToast();
  const queryClient = useQueryClient();
  const [expanded, setExpanded] = useState(false);
  const [amount, setAmount] = useState("");
  const [reference, setReference] = useState("");
  const [notes, setNotes] = useState("");
  const [pushResult, setPushResult] = useState<string | null>(null);

  const { data: company } = useQuery({
    queryKey: ["companies"],
    queryFn: async () => (await api.get<CompanyView[]>("/companies")).data,
    enabled: expanded,
    select: (companies) => companies.find((c) => c.id === invoice.companyId),
  });

  const recordMutation = useMutation({
    mutationFn: async () => {
      const body: RecordInvoicePaymentRequest = {
        invoiceId: invoice.invoiceId,
        amount: Number(amount),
        reference: reference || undefined,
        notes: notes || undefined,
      };
      return (await api.post<RecordPaymentResult>("/payment-tracking/record-payment", body)).data;
    },
    onSuccess: (data) => {
      setAmount("");
      setReference("");
      setNotes("");
      setPushResult(data.shopifyPushResult ?? null);
      queryClient.invalidateQueries({ queryKey: ["payment-tracking-outstanding"] });
      queryClient.invalidateQueries({ queryKey: ["companies"] });
      showToast(`Payment of ${money(data.payment.amount)} recorded for invoice ${invoice.invoiceNumber}.`);
    },
    onError: (err: Error) => showToast(err.message),
  });

  const applyCreditMutation = useMutation({
    mutationFn: async () => {
      const body: ApplyCreditBalanceRequest = { invoiceId: invoice.invoiceId, amount: Number(amount) };
      return (await api.post<RecordPaymentResult>("/payment-tracking/apply-credit-balance", body)).data;
    },
    onSuccess: (data) => {
      setAmount("");
      setPushResult(data.shopifyPushResult ?? null);
      queryClient.invalidateQueries({ queryKey: ["payment-tracking-outstanding"] });
      queryClient.invalidateQueries({ queryKey: ["companies"] });
      showToast(`Applied ${money(data.payment.amount)} credit balance to invoice ${invoice.invoiceNumber}.`);
    },
    onError: (err: Error) => showToast(err.message),
  });

  return (
    <>
      <tr onClick={() => setExpanded((v) => !v)} className={`cursor-pointer ${rowStyle(invoice)}`}>
        <td className="px-3 py-2 font-medium text-slate-800">{invoice.companyName}</td>
        <td className="px-3 py-2">{invoice.invoiceNumber}</td>
        <td className="px-3 py-2">{formatDate(invoice.generationDate)}</td>
        <td className="px-3 py-2">{invoice.orderNumbers.join(", ") || "-"}</td>
        <td className="px-3 py-2 text-right">{money(invoice.grandTotal)}</td>
        <td className="px-3 py-2 text-right">{money(invoice.paidAmount)}</td>
        <td className="px-3 py-2 text-right font-medium">{money(invoice.outstanding)}</td>
        <td className="px-3 py-2 text-right">{invoice.daysSinceInvoiced}</td>
        <td className="px-3 py-2 text-right">
          {invoice.daysOverTerms > 0 ? (
            <span className="text-red-600 font-medium">{invoice.daysOverTerms}</span>
          ) : (
            "-"
          )}
        </td>
      </tr>
      {expanded && (
        <tr>
          <td colSpan={9} className="px-3 py-3 bg-slate-50 border-t border-b border-slate-200">
            <div className="flex gap-3 items-end flex-wrap">
              <div>
                <label className="block text-xs text-slate-400 mb-1">Amount</label>
                <input
                  type="number"
                  step="0.01"
                  min={0}
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  className="input w-32"
                />
              </div>
              <div>
                <label className="block text-xs text-slate-400 mb-1">Reference</label>
                <input
                  value={reference}
                  onChange={(e) => setReference(e.target.value)}
                  placeholder="e.g. bank ref"
                  className="input w-40"
                />
              </div>
              <div className="flex-1 min-w-[160px]">
                <label className="block text-xs text-slate-400 mb-1">Notes</label>
                <input value={notes} onChange={(e) => setNotes(e.target.value)} className="input" />
              </div>
              <button
                onClick={() => recordMutation.mutate()}
                disabled={recordMutation.isPending || !amount}
                className="bg-slate-800 text-white text-sm px-4 py-2 rounded-md hover:bg-slate-700 disabled:opacity-50"
              >
                {recordMutation.isPending ? "Recording..." : "Record Payment"}
              </button>
              {company && company.creditBalance > 0 && (
                <button
                  onClick={() => applyCreditMutation.mutate()}
                  disabled={applyCreditMutation.isPending || !amount || Number(amount) > company.creditBalance}
                  title={`Available credit balance: ${money(company.creditBalance)}`}
                  className="border border-emerald-300 text-emerald-700 text-sm px-4 py-2 rounded-md hover:bg-emerald-50 disabled:opacity-50"
                >
                  {applyCreditMutation.isPending
                    ? "Applying..."
                    : `Apply Credit Balance (${money(company.creditBalance)} available)`}
                </button>
              )}
              {pushResult && <p className="w-full text-xs text-slate-500">Shopify: {pushResult}</p>}
            </div>
          </td>
        </tr>
      )}
    </>
  );
}

export default function PaymentTracking() {
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("");

  const { data: outstanding, isLoading } = useQuery({
    queryKey: ["payment-tracking-outstanding"],
    queryFn: async () => (await api.get<OutstandingInvoiceView[]>("/payment-tracking/outstanding")).data,
    refetchInterval: 30000,
  });

  const filtered = useMemo(() => {
    const list = outstanding ?? [];
    const term = search.trim().toLowerCase();
    let matches = term
      ? list.filter(
          (inv) =>
            inv.companyName.toLowerCase().includes(term) ||
            String(inv.invoiceNumber).includes(term) ||
            inv.orderNumbers.some((n) => n.toLowerCase().includes(term))
        )
      : list;

    if (statusFilter === "OVER_TERMS") {
      matches = matches.filter((inv) => inv.daysOverTerms > 0);
    } else if (statusFilter === "CLOSE_TO_TERMS") {
      matches = matches.filter((inv) => inv.daysOverTerms === 0 && inv.paymentTermsDays - inv.daysSinceInvoiced <= 5);
    } else if (statusFilter === "PART_PAID") {
      matches = matches.filter((inv) => inv.paidAmount > 0 && inv.outstanding > 0);
    }

    return [...matches].sort((a, b) => b.daysOverTerms - a.daysOverTerms || b.daysSinceInvoiced - a.daysSinceInvoiced);
  }, [outstanding, search, statusFilter]);

  const totalOutstanding = filtered.reduce((sum, inv) => sum + inv.outstanding, 0);

  return (
    <div>
      <div className="flex justify-between items-center mb-4">
        <div>
          <h2 className="text-2xl font-semibold text-slate-800">Payment Tracking</h2>
          <p className="text-slate-500">
            Every invoiced order with an outstanding balance. Click a row to record a payment or apply credit
            balance.
          </p>
        </div>
      </div>

      <div className="flex gap-3 mb-4 flex-wrap items-center">
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search by company, invoice number or order number..."
          className="flex-1 min-w-[260px] border-2 border-slate-300 focus:border-emerald-500 rounded-lg px-4 py-2.5 outline-none text-sm"
        />
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value as StatusFilter)}
          className="input"
        >
          <option value="">All outstanding</option>
          <option value="CLOSE_TO_TERMS">Close to payment terms</option>
          <option value="OVER_TERMS">Over payment terms</option>
          <option value="PART_PAID">Part payment</option>
        </select>
        <span className="text-sm text-slate-500 ml-auto">
          {filtered.length} invoice(s) · {money(totalOutstanding)} outstanding
        </span>
      </div>

      <div className="bg-white rounded-lg shadow-sm border border-slate-200 overflow-auto">
        <table className="w-full text-sm">
          <thead className="text-left text-slate-500 border-b border-slate-200 sticky top-0 bg-white">
            <tr>
              <th className="px-3 py-2">Company</th>
              <th className="px-3 py-2">Invoice #</th>
              <th className="px-3 py-2">Date</th>
              <th className="px-3 py-2">Order(s)</th>
              <th className="px-3 py-2 text-right">Total</th>
              <th className="px-3 py-2 text-right">Paid</th>
              <th className="px-3 py-2 text-right">Outstanding</th>
              <th className="px-3 py-2 text-right">Days Since Invoiced</th>
              <th className="px-3 py-2 text-right">Days Over Terms</th>
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
                  {search ? `No outstanding invoices match "${search}".` : "Nothing outstanding right now."}
                </td>
              </tr>
            )}
            {filtered.map((inv) => (
              <PaymentRow key={inv.invoiceId} invoice={inv} />
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
