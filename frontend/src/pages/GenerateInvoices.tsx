import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../api/client";
import { useToast } from "../components/ToastContext";
import { formatDate } from "../utils/format";
import { GenerateInvoicesRequest, GenerateInvoicesResult, InvoiceType, PendingInvoiceLineView } from "../types";

const money = (v: number) => `£${v.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

export default function GenerateInvoices() {
  const { showToast } = useToast();
  const queryClient = useQueryClient();

  const [invoiceType, setInvoiceType] = useState<InvoiceType>("INVOICE");
  // Defaults to today - this is "the date that goes on the invoice", not a
  // filter on which orders show up (every order awaiting invoicing for the
  // selected type shows, regardless of when it was despatched - fully
  // despatched (Invoice Pending) or only Partially Despatched, in which case
  // just what's actually shipped so far is offered here).
  const [generationDate, setGenerationDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [result, setResult] = useState<GenerateInvoicesResult | null>(null);

  const { data: lines, isLoading } = useQuery({
    queryKey: ["invoices-pending", invoiceType],
    queryFn: async () =>
      (await api.get<PendingInvoiceLineView[]>("/invoices/pending", { params: { type: invoiceType } })).data,
  });

  // Group into the same company-then-order shape as the OrderWise export
  // Dan attached - one heading per company, one sub-heading per order.
  const groups = useMemo(() => {
    const byCompany = new Map<number, { companyName: string; orders: Map<number, { orderNumber: string; orderDate: string; lines: PendingInvoiceLineView[] }> }>();
    for (const line of lines ?? []) {
      let company = byCompany.get(line.companyId);
      if (!company) {
        company = { companyName: line.companyName, orders: new Map() };
        byCompany.set(line.companyId, company);
      }
      let order = company.orders.get(line.orderId);
      if (!order) {
        order = { orderNumber: line.orderNumber, orderDate: line.orderDate, lines: [] };
        company.orders.set(line.orderId, order);
      }
      order.lines.push(line);
    }
    return Array.from(byCompany.entries())
      .map(([companyId, c]) => ({
        companyId,
        companyName: c.companyName,
        orders: Array.from(c.orders.entries()).map(([orderId, o]) => ({ orderId, ...o })),
      }))
      .sort((a, b) => a.companyName.localeCompare(b.companyName));
  }, [lines]);

  const allIds = useMemo(() => (lines ?? []).map((l) => l.orderLineId), [lines]);
  const allSelected = allIds.length > 0 && allIds.every((id) => selected.has(id));

  const toggleAll = () => {
    setSelected(allSelected ? new Set() : new Set(allIds));
  };

  const toggleLine = (id: number) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const toggleOrder = (ids: number[]) => {
    setSelected((prev) => {
      const next = new Set(prev);
      const allOn = ids.every((id) => next.has(id));
      for (const id of ids) {
        if (allOn) next.delete(id);
        else next.add(id);
      }
      return next;
    });
  };

  const generateMutation = useMutation({
    mutationFn: async () => {
      const body: GenerateInvoicesRequest = {
        invoiceType,
        generationDate,
        orderLineIds: Array.from(selected),
      };
      return (await api.post<GenerateInvoicesResult>("/invoices/generate", body)).data;
    },
    onSuccess: (data) => {
      setResult(data);
      setSelected(new Set());
      queryClient.invalidateQueries({ queryKey: ["invoices-pending"] });
      const failedEmails = data.invoices.filter((i) => !i.emailed).length;
      showToast(
        failedEmails > 0
          ? `Generated ${data.invoices.length} - ${failedEmails} couldn't be emailed, see below.`
          : `Generated ${data.invoices.length} ${invoiceType === "CREDIT_NOTE" ? "credit note(s)" : "invoice(s)"}.`
      );
    },
    onError: (err: Error) => showToast(err.message),
  });

  return (
    <div>
      <h2 className="text-2xl font-semibold text-slate-800 mb-2">Generate Invoices</h2>
      <p className="text-slate-500 mb-6">
        Every despatched line waiting to be invoiced. Tick the lines to include - phones and routers on the same
        order can go out separately if that's how a customer wants them - then Generate.
      </p>

      <div className="bg-white rounded-lg border border-slate-200 p-4 mb-4 flex flex-wrap items-end gap-4">
        <div>
          <label className="block text-xs font-medium text-slate-500 mb-1">Generation Date</label>
          <input
            type="date"
            value={generationDate}
            onChange={(e) => setGenerationDate(e.target.value)}
            className="input"
          />
        </div>

        <div>
          <label className="block text-xs font-medium text-slate-500 mb-1">Type</label>
          <div className="flex gap-4 h-[38px] items-center">
            <label className="flex items-center gap-1.5 text-sm text-slate-700">
              <input
                type="radio"
                name="invoiceType"
                checked={invoiceType === "INVOICE"}
                onChange={() => {
                  setInvoiceType("INVOICE");
                  setSelected(new Set());
                  setResult(null);
                }}
              />
              Invoice
            </label>
            <label className="flex items-center gap-1.5 text-sm text-slate-700">
              <input
                type="radio"
                name="invoiceType"
                checked={invoiceType === "CREDIT_NOTE"}
                onChange={() => {
                  setInvoiceType("CREDIT_NOTE");
                  setSelected(new Set());
                  setResult(null);
                }}
              />
              Credit
            </label>
          </div>
        </div>

        <div className="ml-auto flex items-center gap-3">
          <span className="text-sm text-slate-500">{selected.size} line(s) selected</span>
          <button type="button" className="btn-secondary" onClick={toggleAll} disabled={allIds.length === 0}>
            {allSelected ? "Deselect all" : "Select all"}
          </button>
          <button
            type="button"
            className="btn-primary"
            disabled={selected.size === 0 || generateMutation.isPending}
            onClick={() => generateMutation.mutate()}
          >
            {generateMutation.isPending ? "Generating..." : "Generate"}
          </button>
        </div>
      </div>

      {result && (
        <div className="bg-white rounded-lg border border-slate-200 p-4 mb-4">
          <h3 className="font-medium text-slate-800 mb-2">
            Generated {result.invoices.length} {invoiceType === "CREDIT_NOTE" ? "credit note(s)" : "invoice(s)"}
          </h3>
          <table className="w-full text-sm mb-2">
            <tbody>
              {result.invoices.map((inv) => (
                <tr key={inv.invoiceId} className="border-t border-slate-100">
                  <td className="py-1 pr-4 font-medium">{inv.invoiceNumber}</td>
                  <td className="py-1 pr-4">{inv.companyName}</td>
                  <td className="py-1 pr-4">{money(inv.grandTotal)}</td>
                  <td className="py-1">
                    {inv.emailed ? (
                      <span className="text-emerald-600">Emailed</span>
                    ) : (
                      <span className="text-amber-600">Not emailed{inv.emailError ? ` - ${inv.emailError}` : ""}</span>
                    )}
                  </td>
                  <td className="py-1 text-right">
                    <a
                      href={`/api/invoices/${inv.invoiceId}/pdf`}
                      target="_blank"
                      rel="noreferrer"
                      className="text-emerald-600 hover:underline"
                    >
                      Download PDF
                    </a>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {result.warnings.length > 0 && (
            <ul className="text-xs text-amber-600 list-disc list-inside">
              {result.warnings.map((w, i) => (
                <li key={i}>{w}</li>
              ))}
            </ul>
          )}
        </div>
      )}

      {isLoading && <p className="text-slate-400 text-sm">Loading...</p>}

      {!isLoading && groups.length === 0 && (
        <p className="text-slate-400 text-sm">
          Nothing waiting to be {invoiceType === "CREDIT_NOTE" ? "credited" : "invoiced"} right now.
        </p>
      )}

      {groups.map((company) => (
        <div key={company.companyId} className="bg-white rounded-lg border border-slate-200 mb-4 overflow-hidden">
          <div className="px-4 py-2 bg-slate-50 border-b border-slate-200 font-medium text-slate-800">
            {company.companyName}
          </div>
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-slate-500 border-b border-slate-100">
                <th className="py-2 pl-4 pr-2 w-8"></th>
                <th className="py-2 pr-2">Code</th>
                <th className="py-2 pr-2">Description</th>
                <th className="py-2 pr-2 text-right">Qty</th>
                <th className="py-2 pr-2 text-right">Price Each</th>
                <th className="py-2 pr-4 text-right">Net</th>
              </tr>
            </thead>
            <tbody>
              {company.orders.map((order) => {
                const orderIds = order.lines.map((l) => l.orderLineId);
                const orderAllSelected = orderIds.every((id) => selected.has(id));
                return (
                  <>
                    <tr key={`order-${order.orderId}`} className="bg-slate-50/60 border-t border-slate-100">
                      <td className="py-1.5 pl-4 pr-2">
                        <input
                          type="checkbox"
                          checked={orderAllSelected}
                          onChange={() => toggleOrder(orderIds)}
                          aria-label={`Select all lines on order ${order.orderNumber}`}
                        />
                      </td>
                      <td colSpan={5} className="py-1.5 pr-4 text-xs text-slate-500">
                        Order {order.orderNumber} &middot; <span className="font-medium text-slate-600">{company.companyName}</span> &middot;{" "}
                        {formatDate(order.orderDate)}
                      </td>
                    </tr>
                    {order.lines.map((line) => (
                      <tr key={line.orderLineId} className="border-t border-slate-50">
                        <td className="py-1.5 pl-4 pr-2">
                          <input
                            type="checkbox"
                            checked={selected.has(line.orderLineId)}
                            onChange={() => toggleLine(line.orderLineId)}
                          />
                        </td>
                        <td className="py-1.5 pr-2">{line.sku}</td>
                        <td className="py-1.5 pr-2 text-slate-600">{line.description}</td>
                        <td className="py-1.5 pr-2 text-right">{line.quantityPending}</td>
                        <td className="py-1.5 pr-2 text-right">{money(line.unitPrice)}</td>
                        <td className="py-1.5 pr-4 text-right">{money(line.netAmount)}</td>
                      </tr>
                    ))}
                  </>
                );
              })}
            </tbody>
          </table>
        </div>
      ))}
    </div>
  );
}
