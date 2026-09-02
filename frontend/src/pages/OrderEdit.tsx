import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useParams } from "react-router-dom";
import { api } from "../api/client";
import { AcknowledgementResult, CompanyView, Order, OrderCreditStatus, OrderStatus, OrderType, PaymentView, Product } from "../types";

const STATUSES: OrderStatus[] = ["ON_HOLD", "AWAITING_DESPATCH", "CANCELLED", "COMPLETED", "PARTIALLY_DESPATCHED", "AWAITING_CONVERSION"];
const TYPES: OrderType[] = ["ORDER", "PAUSED", "QUOTE", "CREDIT_REFUND", "SCHEDULED"];

// crypto.randomUUID() is only exposed in "secure contexts" (HTTPS or localhost) -
// it's silently undefined on plain http://<LAN-IP>, which crashed this whole page.
// This works everywhere.
function generateKey(): string {
  return `line-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

interface LineDraft {
  key: string;
  productId: string;
  quantityOrdered: string;
  quantityDespatched: string;
  unitPrice: string;
  notes: string;
}

function emptyLine(): LineDraft {
  return { key: generateKey(), productId: "", quantityOrdered: "1", quantityDespatched: "0", unitPrice: "", notes: "" };
}

export default function OrderEdit() {
  const { id } = useParams();
  const isNew = id === "new";
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [orderNumber, setOrderNumber] = useState("");
  const [orderDate, setOrderDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [customerName, setCustomerName] = useState("");
  const [customerEmail, setCustomerEmail] = useState("");
  const [companyId, setCompanyId] = useState("");
  const [orderReference, setOrderReference] = useState("");
  const [ecommerceOrderNumber, setEcommerceOrderNumber] = useState("");
  const [orderedBy, setOrderedBy] = useState("");
  const [deliveryName, setDeliveryName] = useState("");
  const [deliveryTown, setDeliveryTown] = useState("");
  const [deliveryCountry, setDeliveryCountry] = useState("");
  const [deliveryPostcode, setDeliveryPostcode] = useState("");
  const [deliveryCountryCode, setDeliveryCountryCode] = useState("");
  const [status, setStatus] = useState<OrderStatus>("ON_HOLD");
  const [orderType, setOrderType] = useState<OrderType>("ORDER");
  const [shippingCost, setShippingCost] = useState("");
  const [courierMethod, setCourierMethod] = useState("");
  const [specialInstructions, setSpecialInstructions] = useState("");
  const [lines, setLines] = useState<LineDraft[]>([emptyLine()]);
  const [error, setError] = useState<string | null>(null);
  const [ackResult, setAckResult] = useState<AcknowledgementResult | null>(null);
  const [paymentAmount, setPaymentAmount] = useState("");
  const [paymentReference, setPaymentReference] = useState("");
  const [paymentNotes, setPaymentNotes] = useState("");

  const { data: existingOrder } = useQuery({
    queryKey: ["order", id],
    queryFn: async () => (await api.get<Order>(`/orders/${id}`)).data,
    enabled: !isNew,
  });

  const { data: products } = useQuery({
    queryKey: ["products"],
    queryFn: async () => (await api.get<Product[]>("/products")).data,
  });

  const { data: companies } = useQuery({
    queryKey: ["companies"],
    queryFn: async () => (await api.get<CompanyView[]>("/companies")).data,
  });

  const { data: creditStatus, refetch: refetchCreditStatus } = useQuery({
    queryKey: ["order-credit-status", id],
    queryFn: async () => (await api.get<OrderCreditStatus | null>(`/orders/${id}/credit-status`)).data,
    enabled: !isNew,
  });

  const { data: payments, refetch: refetchPayments } = useQuery({
    queryKey: ["order-payments", id],
    queryFn: async () => (await api.get<PaymentView[]>(`/orders/${id}/payments`)).data,
    enabled: !isNew,
  });

  useEffect(() => {
    if (!existingOrder) return;
    setOrderNumber(existingOrder.orderNumber);
    setOrderDate(existingOrder.orderDate.slice(0, 10));
    setCustomerName(existingOrder.customerName);
    setCustomerEmail(existingOrder.customerEmail ?? "");
    setCompanyId(existingOrder.company ? String(existingOrder.company.id) : "");
    setOrderReference(existingOrder.orderReference ?? "");
    setEcommerceOrderNumber(existingOrder.ecommerceOrderNumber ?? "");
    setOrderedBy(existingOrder.orderedBy ?? "");
    setDeliveryName(existingOrder.deliveryName ?? "");
    setDeliveryTown(existingOrder.deliveryTown ?? "");
    setDeliveryCountry(existingOrder.deliveryCountry ?? "");
    setDeliveryPostcode(existingOrder.deliveryPostcode ?? "");
    setDeliveryCountryCode(existingOrder.deliveryCountryCode ?? "");
    setStatus(existingOrder.status);
    setOrderType(existingOrder.orderType);
    setShippingCost(existingOrder.shippingCost != null ? String(existingOrder.shippingCost) : "");
    setCourierMethod(existingOrder.courierMethod ?? "");
    setSpecialInstructions(existingOrder.specialInstructions ?? "");
    setLines(
      existingOrder.lines.length > 0
        ? existingOrder.lines.map((l) => ({
            key: generateKey(),
            productId: String(l.product.id),
            quantityOrdered: String(l.quantityOrdered),
            quantityDespatched: String(l.quantityDespatched),
            unitPrice: l.unitPrice != null ? String(l.unitPrice) : "",
            notes: l.notes ?? "",
          }))
        : [emptyLine()]
    );
  }, [existingOrder]);

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload = {
        orderNumber: orderNumber || undefined,
        orderDate: new Date(orderDate).toISOString(),
        customerName,
        customerEmail: customerEmail || undefined,
        companyId: companyId ? Number(companyId) : undefined,
        orderReference: orderReference || undefined,
        ecommerceOrderNumber: ecommerceOrderNumber || undefined,
        orderedBy: orderedBy || undefined,
        deliveryName: deliveryName || undefined,
        deliveryTown: deliveryTown || undefined,
        deliveryCountry: deliveryCountry || undefined,
        deliveryPostcode: deliveryPostcode || undefined,
        deliveryCountryCode: deliveryCountryCode || undefined,
        status,
        orderType,
        shippingCost: shippingCost ? Number(shippingCost) : undefined,
        courierMethod: courierMethod || undefined,
        specialInstructions: specialInstructions || undefined,
        lines: lines
          .filter((l) => l.productId)
          .map((l) => ({
            productId: Number(l.productId),
            quantityOrdered: Number(l.quantityOrdered) || 0,
            quantityDespatched: Number(l.quantityDespatched) || 0,
            unitPrice: l.unitPrice ? Number(l.unitPrice) : undefined,
            notes: l.notes || undefined,
          })),
      };
      if (isNew) {
        return (await api.post<Order>("/orders", payload)).data;
      }
      return (await api.put<Order>(`/orders/${id}`, payload)).data;
    },
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ["orders"] });
      setError(null);
      navigate(`/sales-activity/${data.id}`, { replace: true });
    },
    onError: (err: Error) => setError(err.message),
  });

  const { data: settings } = useQuery({
    queryKey: ["settings"],
    queryFn: async () => (await api.get<Record<string, string>>("/settings")).data,
  });

  const acknowledgeMutation = useMutation({
    mutationFn: async () => (await api.post<AcknowledgementResult>(`/orders/${id}/acknowledge`)).data,
    onSuccess: (data) => {
      setAckResult(data);
      queryClient.invalidateQueries({ queryKey: ["order", id] });
    },
    onError: (err: Error) => setError(err.message),
  });

  const [creditOverrideReason, setCreditOverrideReason] = useState("");
  const [showCreditOverride, setShowCreditOverride] = useState(false);

  const releaseMutation = useMutation({
    mutationFn: async (override?: boolean) =>
      (
        await api.post<Order>(`/orders/${id}/release-for-despatch`, {
          shippingCost: shippingCost ? Number(shippingCost) : undefined,
          courierMethod: courierMethod || undefined,
          overrideCreditHold: override ?? false,
          overrideReason: override ? creditOverrideReason : undefined,
        })
      ).data,
    onSuccess: (data) => {
      setStatus(data.status);
      queryClient.invalidateQueries({ queryKey: ["orders"] });
      queryClient.invalidateQueries({ queryKey: ["order", id] });
      setError(null);
      setShowCreditOverride(false);
      setCreditOverrideReason("");
      // One click covers both steps when auto-acknowledge is on in Settings -
      // no separate "now go send the email" step needed.
      if ((settings?.["auto_acknowledge_on_release"] ?? "true") === "true") {
        acknowledgeMutation.mutate();
      }
    },
    onError: (err: Error) => {
      setError(err.message);
      if (err.message.toLowerCase().includes("credit limit")) {
        setShowCreditOverride(true);
      }
    },
  });

  const paymentMutation = useMutation({
    mutationFn: async () =>
      api.post(`/orders/${id}/payments`, {
        amount: Number(paymentAmount),
        reference: paymentReference || undefined,
        notes: paymentNotes || undefined,
      }),
    onSuccess: () => {
      setPaymentAmount("");
      setPaymentReference("");
      setPaymentNotes("");
      refetchPayments();
      refetchCreditStatus();
      queryClient.invalidateQueries({ queryKey: ["companies"] });
    },
    onError: (err: Error) => setError(err.message),
  });

  const [printStatus, setPrintStatus] = useState<string | null>(null);

  const printPickingNote = async () => {
    setPrintStatus(null);
    const agentUrl = settings?.["print_agent_url"] || "http://localhost:9191/print";
    const printerName = settings?.["picking_note_printer"] || "";

    try {
      const pdfResponse = await api.get(`/orders/${id}/picking-note`, { responseType: "blob" });

      // Try the local print agent first - if it's running, this is a genuinely
      // silent print with no dialog and no new tab. If it's not reachable
      // (not installed/running on this PC), fall back to opening the PDF so
      // printing is never a dead end.
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 1500);
      const agentResponse = await fetch(agentUrl, {
        method: "POST",
        headers: { "X-Printer-Name": printerName, "Content-Type": "application/pdf" },
        body: pdfResponse.data,
        signal: controller.signal,
      }).catch(() => null);
      clearTimeout(timeout);

      if (agentResponse && agentResponse.ok) {
        setPrintStatus("Sent to printer.");
      } else {
        setPrintStatus("Print agent not reachable - opened in a new tab instead. See Settings for setup.");
        const blobUrl = window.URL.createObjectURL(pdfResponse.data);
        window.open(blobUrl, "_blank");
      }
    } catch (err) {
      setError((err as Error).message);
    }
  };

  const updateLine = (key: string, field: keyof LineDraft, value: string) => {
    setLines((prev) => prev.map((l) => (l.key === key ? { ...l, [field]: value } : l)));
  };

  const removeLine = (key: string) => {
    setLines((prev) => (prev.length > 1 ? prev.filter((l) => l.key !== key) : prev));
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-2xl font-semibold text-slate-800">
          {isNew ? "New Order" : `Edit Order ${existingOrder?.orderNumber ?? ""}`}
        </h2>
        <button onClick={() => navigate("/sales-activity")} className="text-sm text-slate-500 hover:text-slate-800">
          ← Back to Sales Activity
        </button>
      </div>

      {creditStatus && (
        <div
          className={`rounded-lg px-4 py-3 mb-6 text-sm border ${
            creditStatus.overLimit
              ? "bg-red-50 border-red-200 text-red-700"
              : "bg-slate-50 border-slate-200 text-slate-600"
          }`}
        >
          <span className="font-medium">{creditStatus.companyName}:</span>{" "}
          {creditStatus.creditLimit != null ? (
            <>
              {creditStatus.creditUsed?.toFixed(2)} used of {creditStatus.creditLimit.toFixed(2)} credit limit (
              {creditStatus.creditAvailable?.toFixed(2)} available){creditStatus.overLimit && " - OVER LIMIT"}
            </>
          ) : (
            "no credit account set"
          )}
          {" · "}This order: {creditStatus.orderOutstanding.toFixed(2)} outstanding of {creditStatus.orderTotal.toFixed(2)}
        </div>
      )}

      <div className="bg-white border border-slate-200 rounded-lg p-5 mb-6">
        <div className="grid grid-cols-3 gap-4 mb-4">
          <Field label="Order Number (leave blank to auto-generate)">
            <input value={orderNumber} onChange={(e) => setOrderNumber(e.target.value)} className="input" />
          </Field>
          <Field label="Order Date">
            <input type="date" required value={orderDate} onChange={(e) => setOrderDate(e.target.value)} className="input" />
          </Field>
          <Field label="Customer Name">
            <input required value={customerName} onChange={(e) => setCustomerName(e.target.value)} className="input" />
          </Field>
          <Field label="Customer Email">
            <input type="email" value={customerEmail} onChange={(e) => setCustomerEmail(e.target.value)} className="input" />
          </Field>
          <Field label="Company (B2B credit account)">
            <select value={companyId} onChange={(e) => setCompanyId(e.target.value)} className="input">
              <option value="">None</option>
              {companies?.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Order Reference">
            <input value={orderReference} onChange={(e) => setOrderReference(e.target.value)} className="input" />
          </Field>
          <Field label="Ecommerce Order Number">
            <input value={ecommerceOrderNumber} onChange={(e) => setEcommerceOrderNumber(e.target.value)} className="input" />
          </Field>
          <Field label="Ordered By">
            <input value={orderedBy} onChange={(e) => setOrderedBy(e.target.value)} className="input" />
          </Field>
          <Field label="Delivery Name">
            <input value={deliveryName} onChange={(e) => setDeliveryName(e.target.value)} className="input" />
          </Field>
          <Field label="Delivery Town">
            <input value={deliveryTown} onChange={(e) => setDeliveryTown(e.target.value)} className="input" />
          </Field>
          <Field label="Delivery Country">
            <input value={deliveryCountry} onChange={(e) => setDeliveryCountry(e.target.value)} className="input" />
          </Field>
          <Field label="Delivery Postcode">
            <input value={deliveryPostcode} onChange={(e) => setDeliveryPostcode(e.target.value)} className="input" />
          </Field>
          <Field label="Delivery Country Code">
            <input value={deliveryCountryCode} onChange={(e) => setDeliveryCountryCode(e.target.value)} placeholder="GB" className="input" />
          </Field>
          <Field label="Status">
            <select value={status} onChange={(e) => setStatus(e.target.value as OrderStatus)} className="input">
              {STATUSES.map((s) => (
                <option key={s} value={s}>
                  {s.replace(/_/g, " ")}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Order Type">
            <select value={orderType} onChange={(e) => setOrderType(e.target.value as OrderType)} className="input">
              {TYPES.map((t) => (
                <option key={t} value={t}>
                  {t.replace(/_/g, " ")}
                </option>
              ))}
            </select>
          </Field>
        </div>
        <Field label="Special Instructions (shown on the picking note, below the line items)">
          <textarea
            value={specialInstructions}
            onChange={(e) => setSpecialInstructions(e.target.value)}
            rows={3}
            className="input"
          />
        </Field>
      </div>

      {!isNew && (
        <div className="bg-white border border-slate-200 rounded-lg p-5 mb-6">
          <h3 className="font-medium text-slate-700 mb-3">Despatch</h3>

          {status === "ON_HOLD" ? (
            <div>
              <p className="text-sm text-slate-500 mb-3">
                Set the shipping cost and courier, then release - this replaces the old
                "untick On Hold" step.
              </p>
              <div className="flex gap-3 items-end flex-wrap mb-3">
                <div>
                  <label className="block text-xs text-slate-400 mb-1">Shipping Cost</label>
                  <input
                    type="number"
                    step="0.01"
                    min={0}
                    value={shippingCost}
                    onChange={(e) => setShippingCost(e.target.value)}
                    className="input w-32"
                  />
                </div>
                <div>
                  <label className="block text-xs text-slate-400 mb-1">Courier Method</label>
                  <input
                    value={courierMethod}
                    onChange={(e) => setCourierMethod(e.target.value)}
                    placeholder="e.g. DPD Next Day"
                    className="input w-48"
                  />
                </div>
                <button
                  onClick={() => releaseMutation.mutate(undefined)}
                  disabled={releaseMutation.isPending}
                  className="bg-emerald-600 text-white text-sm px-4 py-2 rounded-md hover:bg-emerald-500 disabled:opacity-50"
                >
                  {releaseMutation.isPending ? "Releasing..." : "Release for Despatch"}
                </button>
              </div>

              {showCreditOverride && (
                <div className="bg-red-50 border border-red-200 rounded-lg p-4 mt-3">
                  <p className="text-sm text-red-700 mb-2">
                    Blocked by the credit limit. Enter a reason to release anyway - this is logged.
                  </p>
                  <div className="flex gap-3 items-end flex-wrap">
                    <input
                      value={creditOverrideReason}
                      onChange={(e) => setCreditOverrideReason(e.target.value)}
                      placeholder="Reason for overriding the credit hold"
                      className="input flex-1 min-w-[240px]"
                    />
                    <button
                      onClick={() => releaseMutation.mutate(true)}
                      disabled={releaseMutation.isPending || !creditOverrideReason.trim()}
                      className="bg-red-600 text-white text-sm px-4 py-2 rounded-md hover:bg-red-500 disabled:opacity-50"
                    >
                      Release Anyway
                    </button>
                  </div>
                </div>
              )}
            </div>
          ) : (
            <div className="flex flex-wrap gap-3 items-center">
              <button
                onClick={printPickingNote}
                className="bg-slate-800 text-white text-sm px-4 py-2 rounded-md hover:bg-slate-700"
              >
                Print Picking Note
              </button>
              <button
                onClick={() => acknowledgeMutation.mutate()}
                disabled={acknowledgeMutation.isPending}
                className="bg-slate-800 text-white text-sm px-4 py-2 rounded-md hover:bg-slate-700 disabled:opacity-50"
              >
                {acknowledgeMutation.isPending ? "Sending..." : "Send Acknowledgement"}
              </button>
              {existingOrder?.acknowledgementSentAt && (
                <span className="text-xs text-slate-400">
                  Last acknowledged: {new Date(existingOrder.acknowledgementSentAt).toLocaleString("en-GB")}
                </span>
              )}
              {printStatus && <p className="w-full text-xs text-slate-500">{printStatus}</p>}
            </div>
          )}

          {ackResult && (
            <div
              className={`mt-4 text-sm rounded px-4 py-3 border ${
                ackResult.emailSent
                  ? "bg-emerald-50 border-emerald-200 text-emerald-700"
                  : "bg-amber-50 border-amber-200 text-amber-800"
              }`}
            >
              <p className="font-medium mb-1">{ackResult.emailSent ? "Sent" : "Not sent"} - {ackResult.reason}</p>
              {ackResult.toAddress && <p className="mb-2">To: {ackResult.toAddress}</p>}
              <details>
                <summary className="cursor-pointer text-xs">View composed email</summary>
                <p className="mt-2 font-medium">{ackResult.subject}</p>
                <pre className="whitespace-pre-wrap text-xs mt-1 font-sans">{ackResult.body}</pre>
              </details>
            </div>
          )}
        </div>
      )}

      {!isNew && (
        <div className="bg-white border border-slate-200 rounded-lg p-5 mb-6">
          <h3 className="font-medium text-slate-700 mb-3">Payments</h3>
          {payments && payments.length > 0 ? (
            <table className="w-full text-sm mb-4">
              <thead className="text-left text-slate-500">
                <tr>
                  <th className="py-1.5 pr-4">Date</th>
                  <th className="py-1.5 pr-4">Amount</th>
                  <th className="py-1.5 pr-4">Reference</th>
                  <th className="py-1.5">Notes</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {payments.map((p) => (
                  <tr key={p.id}>
                    <td className="py-1.5 pr-4">{new Date(p.receivedAt).toLocaleDateString("en-GB")}</td>
                    <td className="py-1.5 pr-4 font-medium">{p.amount.toFixed(2)}</td>
                    <td className="py-1.5 pr-4">{p.reference ?? "-"}</td>
                    <td className="py-1.5">{p.notes ?? "-"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <p className="text-sm text-slate-400 mb-4">No payments recorded yet.</p>
          )}

          <div className="flex gap-3 items-end flex-wrap">
            <div>
              <label className="block text-xs text-slate-400 mb-1">Amount</label>
              <input
                type="number"
                step="0.01"
                min={0}
                value={paymentAmount}
                onChange={(e) => setPaymentAmount(e.target.value)}
                className="input w-32"
              />
            </div>
            <div>
              <label className="block text-xs text-slate-400 mb-1">Reference</label>
              <input
                value={paymentReference}
                onChange={(e) => setPaymentReference(e.target.value)}
                placeholder="e.g. bank ref"
                className="input w-40"
              />
            </div>
            <div className="flex-1 min-w-[160px]">
              <label className="block text-xs text-slate-400 mb-1">Notes</label>
              <input value={paymentNotes} onChange={(e) => setPaymentNotes(e.target.value)} className="input" />
            </div>
            <button
              onClick={() => paymentMutation.mutate()}
              disabled={paymentMutation.isPending || !paymentAmount}
              className="bg-slate-800 text-white text-sm px-4 py-2 rounded-md hover:bg-slate-700 disabled:opacity-50"
            >
              {paymentMutation.isPending ? "Recording..." : "Record Payment"}
            </button>
          </div>
        </div>
      )}

      <div className="bg-white border border-slate-200 rounded-lg p-5 mb-6">
        <div className="flex justify-between items-center mb-3">
          <h3 className="font-medium text-slate-700">Order Lines</h3>
          <button
            onClick={() => setLines((prev) => [...prev, emptyLine()])}
            className="text-sm text-slate-600 hover:text-slate-900"
          >
            + Add line
          </button>
        </div>
        <div className="space-y-2">
          {lines.map((line) => (
            <div key={line.key} className="flex gap-2 items-end flex-wrap border-b border-slate-100 pb-2">
              <div className="flex-1 min-w-[180px]">
                <label className="block text-xs text-slate-400 mb-1">Product</label>
                <select
                  value={line.productId}
                  onChange={(e) => updateLine(line.key, "productId", e.target.value)}
                  className="input"
                >
                  <option value="">Select...</option>
                  {products?.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.sku} - {p.name}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-xs text-slate-400 mb-1">Qty Ordered</label>
                <input
                  type="number"
                  min={0}
                  value={line.quantityOrdered}
                  onChange={(e) => updateLine(line.key, "quantityOrdered", e.target.value)}
                  className="input w-24"
                />
              </div>
              <div>
                <label className="block text-xs text-slate-400 mb-1">Qty Despatched</label>
                <input
                  type="number"
                  min={0}
                  value={line.quantityDespatched}
                  onChange={(e) => updateLine(line.key, "quantityDespatched", e.target.value)}
                  className="input w-28"
                />
              </div>
              <div>
                <label className="block text-xs text-slate-400 mb-1">Unit Price</label>
                <input
                  type="number"
                  step="0.01"
                  min={0}
                  value={line.unitPrice}
                  onChange={(e) => updateLine(line.key, "unitPrice", e.target.value)}
                  className="input w-24"
                />
              </div>
              <div className="flex-1 min-w-[140px]">
                <label className="block text-xs text-slate-400 mb-1">Notes</label>
                <input value={line.notes} onChange={(e) => updateLine(line.key, "notes", e.target.value)} className="input" />
              </div>
              <button
                onClick={() => removeLine(line.key)}
                className="text-xs text-red-600 hover:text-red-800 px-2 pb-2"
              >
                Remove
              </button>
            </div>
          ))}
        </div>
      </div>

      {error && <p className="text-sm text-red-600 mb-4">{error}</p>}

      <button
        onClick={() => saveMutation.mutate()}
        disabled={saveMutation.isPending || !customerName}
        className="bg-emerald-600 text-white text-sm px-5 py-2.5 rounded-md hover:bg-emerald-500 disabled:opacity-50"
      >
        {saveMutation.isPending ? "Saving..." : "Save Order"}
      </button>

      <style>{`.input { width: 100%; border: 1px solid #cbd5e1; border-radius: 0.375rem; padding: 0.5rem 0.75rem; font-size: 0.875rem; }`}</style>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="block text-xs font-medium text-slate-500 mb-1">{label}</label>
      {children}
    </div>
  );
}
