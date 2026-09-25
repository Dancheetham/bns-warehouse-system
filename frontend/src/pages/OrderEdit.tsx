import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useParams } from "react-router-dom";
import { api } from "../api/client";
import { AcknowledgementResult, CompanyView, DpdServiceLookupResult, InvoiceHistoryView, Order, OrderCreditStatus, OrderStatus, OrderType, Product, TicketSummaryView } from "../types";
import { printPdf, printRaw } from "../utils/printAgent";
import { dpdTrackingUrl } from "../utils/tracking";
import { useToast } from "../components/ToastContext";

// COMPLETED, PARTIALLY_DESPATCHED and INVOICE_PENDING are deliberately left
// out - the backend (OrderService.update) now rejects picking any of these
// manually, since they're only ever set by actually going through despatch
// and/or invoicing. See STATUS_OPTIONS_FOR below, which adds back whichever
// of these the order is currently sitting in, read-only, so the dropdown
// still shows the truth without offering it as a choice.
const STATUSES: OrderStatus[] = ["ON_HOLD", "AWAITING_DESPATCH", "CANCELLED", "AWAITING_CONVERSION"];
const SYSTEM_ONLY_STATUSES: OrderStatus[] = ["COMPLETED", "PARTIALLY_DESPATCHED", "INVOICE_PENDING"];
const TYPES: OrderType[] = ["ORDER", "PAUSED", "QUOTE", "CREDIT_REFUND", "SCHEDULED"];
// Only DPD is wired up today - this is a real dropdown (not hardcoded into the
// service picker) so another courier can be added here later without
// reworking the release screen.
const COURIERS = ["DPD"] as const;

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
  const { showToast } = useToast();
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
  const [deliveryAddressLine1, setDeliveryAddressLine1] = useState("");
  const [deliveryAddressLine2, setDeliveryAddressLine2] = useState("");
  const [deliveryPhone, setDeliveryPhone] = useState("");
  const [deliveryTown, setDeliveryTown] = useState("");
  const [deliveryCountry, setDeliveryCountry] = useState("");
  const [deliveryPostcode, setDeliveryPostcode] = useState("");
  const [deliveryCountryCode, setDeliveryCountryCode] = useState("");
  const [status, setStatus] = useState<OrderStatus>("ON_HOLD");
  const [orderType, setOrderType] = useState<OrderType>("ORDER");
  const [shippingCost, setShippingCost] = useState("");
  const [courierMethod, setCourierMethod] = useState("");
  const [courier, setCourier] = useState<string>(COURIERS[0]);
  const [dpdNetworkKey, setDpdNetworkKey] = useState("");
  const [specialInstructions, setSpecialInstructions] = useState("");
  const [lines, setLines] = useState<LineDraft[]>([emptyLine()]);
  const [error, setError] = useState<string | null>(null);
  const [errorIsConflict, setErrorIsConflict] = useState(false);
  const [ackResult, setAckResult] = useState<AcknowledgementResult | null>(null);

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

  // Support Tickets links to an order the other way round too - a ticket
  // that was opened about a specific order, e.g. "SO-10019 hasn't turned up".
  // A single order isn't expected to have more than one open ticket at once,
  // but this stays a list rather than assuming that.
  const { data: linkedTickets } = useQuery({
    queryKey: ["tickets-by-order", id],
    queryFn: async () => (await api.get<TicketSummaryView[]>(`/tickets/by-order/${id}`)).data,
    enabled: !isNew,
  });

  // Whether this order has actually reached invoicing yet - reuses the same
  // cached list Invoice History itself loads (react-query dedupes on the
  // query key), just to answer "does a link to Invoice History, filtered to
  // this order, actually go anywhere". Delivery History doesn't need this -
  // existingOrder.despatchedAt already answers that one directly.
  const { data: invoiceHistory } = useQuery({
    queryKey: ["invoice-history"],
    queryFn: async () => (await api.get<InvoiceHistoryView[]>("/invoices/history")).data,
    enabled: !isNew,
  });
  const hasInvoices = !isNew && !!existingOrder && (invoiceHistory ?? []).some((inv) => inv.orderNumbers.includes(existingOrder.orderNumber));

  const { data: testDataResetStatus } = useQuery({
    queryKey: ["test-data-reset-status"],
    queryFn: async () => (await api.get<{ enabled: boolean }>("/admin/test-data-reset")).data,
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
    setDeliveryAddressLine1(existingOrder.deliveryAddressLine1 ?? "");
    setDeliveryAddressLine2(existingOrder.deliveryAddressLine2 ?? "");
    setDeliveryPhone(existingOrder.deliveryPhone ?? "");
    setDeliveryTown(existingOrder.deliveryTown ?? "");
    setDeliveryCountry(existingOrder.deliveryCountry ?? "");
    setDeliveryPostcode(existingOrder.deliveryPostcode ?? "");
    setDeliveryCountryCode(existingOrder.deliveryCountryCode ?? "");
    setStatus(existingOrder.status);
    setOrderType(existingOrder.orderType);
    setShippingCost(existingOrder.shippingCost != null ? String(existingOrder.shippingCost) : "");
    setCourierMethod(existingOrder.courierMethod ?? "");
    setDpdNetworkKey(existingOrder.dpdNetworkKey ?? "");
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
        deliveryAddressLine1: deliveryAddressLine1 || undefined,
        deliveryAddressLine2: deliveryAddressLine2 || undefined,
        deliveryPhone: deliveryPhone || undefined,
        deliveryTown: deliveryTown || undefined,
        deliveryCountry: deliveryCountry || undefined,
        deliveryPostcode: deliveryPostcode || undefined,
        deliveryCountryCode: deliveryCountryCode || undefined,
        status,
        orderType,
        shippingCost: shippingCost ? Number(shippingCost) : undefined,
        courierMethod: courierMethod || undefined,
        // Included so a service change made after release (once the order's
        // no longer ON_HOLD, and so no longer going through
        // release-for-despatch) is actually saved by the ordinary Save Order
        // button - see OrderService.applyFields, which now accepts this on
        // the general update path too.
        dpdNetworkKey: dpdNetworkKey || undefined,
        specialInstructions: specialInstructions || undefined,
        // Only meaningful for an existing order - lets the backend reject
        // the save with a clear conflict if someone else has already saved
        // since this page loaded, rather than silently overwriting them.
        version: !isNew ? existingOrder?.version : undefined,
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
      // Without these two, this page kept showing what was true *before*
      // the save - e.g. changing the delivery address to Ireland and
      // saving still showed the old (UK) list of DPD courier services,
      // because dpd-services is looked up from the order as it stands in
      // the database, and nothing here was telling that query (or the
      // single-order query the whole form is populated from) that the
      // order underneath it had just changed. navigate() below re-points
      // the URL at the same order, but React Router doesn't remount the
      // page just because the path resolved to is identical, so this is
      // what actually makes the page reflect the save - not the
      // navigation. Keyed off data.id (not the id from the URL) since a
      // brand new order only gets its real id back here.
      queryClient.invalidateQueries({ queryKey: ["order", String(data.id)] });
      queryClient.invalidateQueries({ queryKey: ["dpd-services", String(data.id)] });
      setError(null);
      showToast(isNew ? "Order created." : "Saved.");
      // Set only when this order originated from Shopify and something
      // changed that needed pushing back there - shown as a second toast
      // rather than replacing the "Saved." one, since the save itself always
      // succeeds locally regardless of whether the Shopify push did.
      if (data.shopifyAmendStatus) {
        showToast(data.shopifyAmendStatus);
      }
      navigate(`/sales-activity/${data.id}`, { replace: true });
    },
    onError: (err: Error & { status?: number }) => {
      setError(err.message);
      setErrorIsConflict(err.status === 409);
    },
  });

  const { data: settings } = useQuery({
    queryKey: ["settings"],
    queryFn: async () => (await api.get<Record<string, string>>("/settings")).data,
  });

  // Live DPD services for this order's actual delivery postcode/weight - only
  // fetched while there's an order to fetch it for and it's still On Hold
  // (no point once it's already been released). DPD's own docs say these
  // codes can change and shouldn't be hardcoded, so this is always a fresh
  // lookup, never a fixed list baked into the app - but the backend falls
  // back to the last list it fetched successfully (from any order) when the
  // live check itself fails, so this still gets real, previously-offered
  // options rather than nothing. `isError` here only fires for the one case
  // there's truly no fallback for - no delivery postcode/country set yet.
  const {
    data: dpdServiceResult,
    isLoading: dpdServicesLoading,
    isError: dpdServicesError,
    error: dpdServicesLookupError,
  } = useQuery({
    queryKey: ["dpd-services", id],
    queryFn: async () => (await api.get<DpdServiceLookupResult>(`/orders/${id}/dpd-services`)).data,
    // Not limited to ON_HOLD any more - the service stays editable (and so
    // needs a live options list) at any status up until the order's shipping
    // has actually been invoiced.
    enabled: !isNew && courier === "DPD" && !existingOrder?.shippingInvoiced,
    retry: false,
  });
  const dpdServices = dpdServiceResult?.services;

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
          dpdNetworkKey: dpdNetworkKey || undefined,
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
      if ((settings?.["auto_print_picking_note_on_release"] ?? "true") === "true") {
        printPickingNote();
      }
    },
    onError: (err: Error) => {
      setError(err.message);
      if (err.message.toLowerCase().includes("credit limit")) {
        setShowCreditOverride(true);
      }
    },
  });

  const [resetOrderDone, setResetOrderDone] = useState(false);
  const resetOrderMutation = useMutation({
    mutationFn: async () => api.post(`/admin/test-data-reset/orders/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["order", id] });
      queryClient.invalidateQueries({ queryKey: ["orders"] });
      setResetOrderDone(true);
      setTimeout(() => setResetOrderDone(false), 2500);
    },
    onError: (err: Error) => setError(err.message),
  });

  const reverseToDespatchMutation = useMutation({
    mutationFn: async () => (await api.post<Order>(`/orders/${id}/reversal/to-despatch`)).data,
    onSuccess: (data) => {
      setStatus(data.status);
      queryClient.invalidateQueries({ queryKey: ["order", id] });
      queryClient.invalidateQueries({ queryKey: ["orders"] });
      setError(null);
    },
    onError: (err: Error) => setError(err.message),
  });

  const cancelAndReturnMutation = useMutation({
    mutationFn: async () => (await api.post<Order>(`/orders/${id}/reversal/cancel`)).data,
    onSuccess: (data) => {
      setStatus(data.status);
      queryClient.invalidateQueries({ queryKey: ["order", id] });
      queryClient.invalidateQueries({ queryKey: ["orders"] });
      setError(null);
    },
    onError: (err: Error) => setError(err.message),
  });

  const [dpdError, setDpdError] = useState<string | null>(null);
  const bookDpdShipmentMutation = useMutation({
    mutationFn: async () => (await api.post(`/orders/${id}/dpd-shipment`)).data,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["order", id] });
      setDpdError(null);
      showToast("DPD shipment booked.");
    },
    // Shown right here, next to the button, rather than only in the
    // page's main error banner further down the page - that one's easy to
    // miss below a long line-items table, which made this look like the
    // button "did nothing" when it had actually failed with a clear reason
    // (e.g. missing DPD credentials or a missing delivery address).
    onError: (err: Error) => setDpdError(err.message),
  });

  const viewDpdLabel = async () => {
    setDpdError(null);
    try {
      const response = await api.get(`/orders/${id}/dpd-labels`, { responseType: "text" });
      const agentUrl = settings?.["print_agent_url"] || "http://localhost:9191/print";
      const printerName = settings?.["label_printer"] || "";
      const printResult = await printRaw(response.data, agentUrl, printerName);
      if (printResult.printed) {
        showToast("Label sent to printer.");
      } else {
        setDpdError("Print agent not reachable - start it on this PC (see Settings > DPD) and try again.");
      }
    } catch (err) {
      setDpdError((err as Error).message);
    }
  };

  const [printStatus, setPrintStatus] = useState<string | null>(null);

  const printPickingNote = async () => {
    setPrintStatus(null);
    const agentUrl = settings?.["print_agent_url"] || "http://localhost:9191/print";
    const printerName = settings?.["picking_note_printer"] || "";

    try {
      const pdfResponse = await api.get(`/orders/${id}/picking-note`, { responseType: "blob" });
      const result = await printPdf(pdfResponse.data, agentUrl, printerName);
      setPrintStatus(
        result.printed ? "Sent to printer." : "Print agent not reachable - opened in a new tab instead. See Settings for setup."
      );
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

  // Matches OrderService.update()'s own "wasLocked" check server-side -
  // once an order has actually gone through despatch and/or invoicing, its
  // status can only move by adding lines for an extra shipment (handled
  // automatically, not from this dropdown), never by picking a new value
  // here. Based on the order as it loaded, not the (identical, since the
  // dropdown is disabled below) local `status` state.
  const statusLocked = existingOrder?.status === "INVOICE_PENDING" || existingOrder?.status === "COMPLETED";

  const addingExtraLines = useMemo(
    () =>
      lines.some((l) => {
        const existingLine = existingOrder?.lines.find((el) => String(el.product.id) === l.productId);
        const requestedQty = Number(l.quantityOrdered) || 0;
        return !existingLine || requestedQty > existingLine.quantityOrdered;
      }),
    [lines, existingOrder]
  );

  // Live cost breakdown for the Despatch panel - goods net matches how
  // CompanyService.goodsTotal prices an order (unit price x quantity
  // ORDERED, not despatched), so this stays consistent with the credit
  // figures shown elsewhere for the same order. Recomputes on every
  // keystroke against the lines/shipping cost currently in the form, not
  // just whatever was last saved - the whole point being it tracks edits
  // before Save is even clicked.
  const selectedCompany = companies?.find((c) => String(c.id) === companyId);
  const vatRate = selectedCompany?.vatRate ?? Number(settings?.["vat_rate"] ?? "20");
  const goodsNet = useMemo(
    () =>
      lines.reduce((sum, line) => {
        const qty = Number(line.quantityOrdered) || 0;
        const price = Number(line.unitPrice) || 0;
        return sum + qty * price;
      }, 0),
    [lines]
  );
  const deliveryNet = Number(shippingCost) || 0;
  const totalNet = goodsNet + deliveryNet;
  const totalTax = (totalNet * vatRate) / 100;
  const totalOrder = totalNet + totalTax;

  return (
    // pb-24 keeps the last bit of page content (the error block, whatever's
    // last in the form) clear of the floating Save button below, which sits
    // fixed to the viewport rather than the page and would otherwise cover
    // it at the bottom of a short page or once scrolled all the way down.
    <div className="pb-24">
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-2xl font-semibold text-slate-800">
          {isNew ? "New Order" : `Edit Order ${existingOrder?.orderNumber ?? ""}`}
        </h2>
        <button onClick={() => navigate("/sales-activity")} className="text-sm text-slate-500 hover:text-slate-800">
          ← Back to Sales Activity
        </button>
      </div>

      {!isNew && (
        <div className="mb-6 flex flex-wrap items-center gap-2">
          {linkedTickets && linkedTickets.length > 0 ? (
            linkedTickets.map((t) => (
              <button
                key={t.id}
                onClick={() => navigate(`/tickets/${t.id}`)}
                className="text-sm bg-amber-50 text-amber-700 border border-amber-200 rounded-full px-3 py-1 hover:bg-amber-100"
              >
                Linked ticket: {t.ticketNumber} - {t.title} →
              </button>
            ))
          ) : (
            <button
              onClick={() => navigate(`/tickets/new?orderId=${id}`)}
              className="text-sm text-slate-500 hover:text-slate-800"
            >
              + Open a support ticket for this order
            </button>
          )}
          {existingOrder?.despatchedAt && (
            <button
              onClick={() => navigate(`/delivery-history/${existingOrder.id}`)}
              className="text-sm bg-blue-50 text-blue-700 border border-blue-200 rounded-full px-3 py-1 hover:bg-blue-100"
            >
              Delivery History →
            </button>
          )}
          {hasInvoices && (
            <button
              onClick={() => navigate(`/invoicing/history?order=${encodeURIComponent(existingOrder!.orderNumber)}`)}
              className="text-sm bg-emerald-50 text-emerald-700 border border-emerald-200 rounded-full px-3 py-1 hover:bg-emerald-100"
            >
              Invoice History →
            </button>
          )}
        </div>
      )}

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
          <Field label="Delivery Address Line 1">
            <input value={deliveryAddressLine1} onChange={(e) => setDeliveryAddressLine1(e.target.value)} className="input" />
          </Field>
          <Field label="Delivery Address Line 2">
            <input value={deliveryAddressLine2} onChange={(e) => setDeliveryAddressLine2(e.target.value)} className="input" />
          </Field>
          <Field label="Delivery Phone">
            <input value={deliveryPhone} onChange={(e) => setDeliveryPhone(e.target.value)} className="input" />
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
            <select
              value={status}
              onChange={(e) => setStatus(e.target.value as OrderStatus)}
              disabled={statusLocked}
              className="input disabled:bg-slate-100 disabled:text-slate-400"
            >
              {STATUSES.map((s) => (
                <option key={s} value={s}>
                  {s.replace(/_/g, " ")}
                </option>
              ))}
              {/* Not a real choice - added only so the dropdown can actually
                  show the order's current value when it's one of the three
                  the despatch/invoicing process sets on its own (see
                  SYSTEM_ONLY_STATUSES above and OrderService.update). */}
              {SYSTEM_ONLY_STATUSES.includes(status) && (
                <option value={status}>{status.replace(/_/g, " ")} (set automatically)</option>
              )}
            </select>
            {statusLocked ? (
              <p className="text-xs text-slate-400 mt-1">
                Locked - this order has already been {existingOrder?.status === "COMPLETED" ? "completed" : "despatched and is awaiting invoicing"}.
                Add order lines below for an extra shipment to reopen it, or use Reverse to Despatch / an RMA to
                correct what's already gone out.
              </p>
            ) : (
              addingExtraLines &&
              existingOrder && (
                <p className="text-xs text-amber-600 mt-1">
                  This adds stock beyond what's already on the order - saving will move it to Partially Despatched
                  for the extra shipment, and reopen shipping cost/courier/service for it.
                </p>
              )
            )}
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

      {!isNew && (
        <div className="bg-white border border-slate-200 rounded-lg p-5 mb-6">
          <h3 className="font-medium text-slate-700 mb-3">Despatch</h3>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div>
          {/* Once the delivery charge has actually been invoiced, the figures
              are locked (see OrderService.update) - shown read-only here,
              same as before. Until then, the editable form below is shown
              instead, at every status - not just On Hold - since Dan wants
              these changeable right up to the point of invoicing, including
              after release (e.g. the customer calls to switch courier
              service before despatch). */}
          {existingOrder?.shippingInvoiced && (shippingCost || courier || courierMethod) && (
            <div className="flex flex-wrap gap-x-6 gap-y-1 text-sm text-slate-600 mb-3">
              <span>
                <span className="text-slate-400">Shipping Cost:</span>{" "}
                {shippingCost ? `£${Number(shippingCost).toFixed(2)}` : "-"}
              </span>
              <span>
                <span className="text-slate-400">Courier:</span> {courier || "-"}
              </span>
              <span>
                <span className="text-slate-400">Service:</span> {courierMethod || "-"}
              </span>
              <span className="text-xs text-slate-400 italic">Locked - this order has been invoiced</span>
            </div>
          )}

          {!existingOrder?.shippingInvoiced && (
            <div>
              <p className="text-sm text-slate-500 mb-3">
                {status === "ON_HOLD"
                  ? 'Set the shipping cost and courier, then release - this replaces the old "untick On Hold" step.'
                  : "Shipping cost, courier and service stay editable until this order is invoiced - change them here, then click Save Order below. At despatch, whatever's saved on the order at that moment is what's used."}
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
                  <label className="block text-xs text-slate-400 mb-1">Courier</label>
                  <select
                    value={courier}
                    onChange={(e) => {
                      setCourier(e.target.value);
                      setDpdNetworkKey("");
                      setCourierMethod("");
                    }}
                    className="input w-32"
                  >
                    {COURIERS.map((c) => (
                      <option key={c} value={c}>
                        {c}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-xs text-slate-400 mb-1">Service</label>
                  {dpdServicesError ? (
                    <input
                      value={courierMethod}
                      onChange={(e) => setCourierMethod(e.target.value)}
                      placeholder="e.g. DPD Next Day"
                      title="Couldn't look up DPD services for this order - type the service manually."
                      className="input w-56"
                    />
                  ) : (
                    <div className="relative w-56">
                      <select
                        value={dpdNetworkKey}
                        onChange={(e) => {
                          const selected = dpdServices?.find((s) => s.networkKey === e.target.value);
                          setDpdNetworkKey(e.target.value);
                          setCourierMethod(selected ? `${selected.networkDesc} (${selected.serviceDesc})` : "");
                        }}
                        disabled={dpdServicesLoading || !dpdServices?.length}
                        className="input w-56 pr-14"
                      >
                        <option value="">
                          {dpdServicesLoading
                            ? "Looking up services..."
                            : dpdServices?.length
                            ? "Select a service..."
                            : "No services available"}
                        </option>
                        {dpdServices?.map((s) => (
                          <option key={s.networkKey} value={s.networkKey}>
                            {s.networkDesc} - {s.serviceDesc}
                          </option>
                        ))}
                      </select>
                      {/* Whether this list is DPD's live answer for this exact address/weight,
                          or the last list DPD gave us for anything, kept as a fallback so the
                          dropdown still has real options instead of forcing free text - see
                          dpdServiceResult.liveError (surfaced below) for why it isn't live. */}
                      {!dpdServicesLoading && dpdServiceResult && (
                        <span
                          title={
                            dpdServiceResult.live
                              ? "Live - checked against DPD just now for this address and weight"
                              : `Not live - showing the last services DPD offered. ${dpdServiceResult.liveError ?? ""}`
                          }
                          className={`absolute right-2 top-1/2 -translate-y-1/2 text-[10px] font-semibold px-1.5 py-0.5 rounded pointer-events-none ${
                            dpdServiceResult.live
                              ? "bg-emerald-100 text-emerald-700"
                              : "bg-amber-100 text-amber-700"
                          }`}
                        >
                          {dpdServiceResult.live ? "Live" : "Cached"}
                        </span>
                      )}
                    </div>
                  )}
                  {dpdServicesError && (
                    <p className="text-xs text-red-500 mt-1">
                      {dpdServicesLookupError instanceof Error
                        ? dpdServicesLookupError.message
                        : "Couldn't look up DPD services - check the delivery postcode and Settings > DPD."}
                    </p>
                  )}
                  {dpdServiceResult && !dpdServiceResult.live && dpdServiceResult.liveError && (
                    <p className="text-xs text-amber-600 mt-1">
                      Not live for this address: {dpdServiceResult.liveError}
                    </p>
                  )}
                </div>
                {status === "ON_HOLD" && (
                  <button
                    onClick={() => releaseMutation.mutate(undefined)}
                    disabled={releaseMutation.isPending}
                    className="bg-emerald-600 text-white text-sm px-4 py-2 rounded-md hover:bg-emerald-500 disabled:opacity-50"
                  >
                    {releaseMutation.isPending ? "Releasing..." : "Release for Despatch"}
                  </button>
                )}
              </div>

              {status === "ON_HOLD" && showCreditOverride && (
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
          )}

          {status !== "ON_HOLD" && (
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
              {testDataResetStatus?.enabled && (
                <div className="w-full border-t border-slate-100 pt-3 mt-1">
                  <button
                    onClick={() => {
                      if (confirm("Reset this order back to On Hold, undoing any picking/packing/despatch? For testing only.")) {
                        resetOrderMutation.mutate();
                      }
                    }}
                    disabled={resetOrderMutation.isPending}
                    className="bg-amber-100 text-amber-800 text-xs px-3 py-1.5 rounded hover:bg-amber-200 disabled:opacity-50"
                  >
                    {resetOrderMutation.isPending ? "Resetting..." : "Reset for Testing"}
                  </button>
                  <span className="text-xs text-slate-400 ml-2">
                    Puts this order back to On Hold - test data reset is enabled on this environment
                  </span>
                  {resetOrderDone && <span className="text-xs text-emerald-600 ml-2">Reset.</span>}
                </div>
              )}
              {(status === "COMPLETED" || status === "PARTIALLY_DESPATCHED") && (
                <div className="w-full border-t border-slate-100 pt-3 mt-1 flex flex-wrap gap-3 items-center">
                  <button
                    onClick={() => {
                      if (
                        confirm(
                          "Reverse this order back to ready-for-despatch? Picking and packing stay exactly as they " +
                            "are (nothing needs re-picking) - only the despatch itself is undone, so changes can be " +
                            "made before confirming despatch again."
                        )
                      ) {
                        reverseToDespatchMutation.mutate();
                      }
                    }}
                    disabled={reverseToDespatchMutation.isPending}
                    className="bg-slate-100 text-slate-700 text-xs px-3 py-1.5 rounded hover:bg-slate-200 disabled:opacity-50"
                  >
                    {reverseToDespatchMutation.isPending ? "Reversing..." : "Reverse to Despatch"}
                  </button>
                  <span className="text-xs text-slate-400">For a quantity or address change after despatch</span>
                </div>
              )}
              <div className="w-full flex flex-wrap gap-3 items-center">
                  <button
                    onClick={() => {
                      if (
                        confirm(
                          "Cancel this order and return every picked/despatched item to the bin it came from? " +
                            "This undoes picking and packing completely, back to On Hold - not just the despatch. " +
                            "This can't be undone by clicking again."
                        )
                      ) {
                        cancelAndReturnMutation.mutate();
                      }
                    }}
                    disabled={cancelAndReturnMutation.isPending}
                    className="bg-red-50 text-red-700 text-xs px-3 py-1.5 rounded hover:bg-red-100 disabled:opacity-50"
                  >
                    {cancelAndReturnMutation.isPending ? "Cancelling..." : "Cancel & Return to Stock"}
                  </button>
                  <span className="text-xs text-slate-400">For a full cancellation</span>
                </div>
            </div>
          )}

          {!isNew && existingOrder && (
            <div className="mt-4 border-t border-slate-100 pt-4">
              <div className="flex flex-wrap gap-3 items-center">
                {existingOrder.dpdConsignmentNumber ? (
                  <>
                    <span className="text-sm text-slate-700">
                      DPD consignment <span className="font-medium">{existingOrder.dpdConsignmentNumber}</span>
                      {existingOrder.dpdParcelNumbers ? ` (parcel ${existingOrder.dpdParcelNumbers})` : ""}
                    </span>
                    <button
                      onClick={viewDpdLabel}
                      className="bg-slate-100 text-slate-700 text-xs px-3 py-1.5 rounded hover:bg-slate-200"
                    >
                      Print Label
                    </button>
                    <a
                      href={dpdTrackingUrl(existingOrder.dpdConsignmentNumber, deliveryPostcode)}
                      target="_blank"
                      rel="noreferrer"
                      className="bg-slate-100 text-slate-700 text-xs px-3 py-1.5 rounded hover:bg-slate-200"
                    >
                      Track →
                    </a>
                  </>
                ) : (
                  <button
                    onClick={() => bookDpdShipmentMutation.mutate()}
                    disabled={bookDpdShipmentMutation.isPending}
                    className="bg-slate-800 text-white text-xs px-3 py-1.5 rounded hover:bg-slate-900 disabled:opacity-50"
                  >
                    {bookDpdShipmentMutation.isPending ? "Booking..." : "Book DPD Shipment"}
                  </button>
                )}
              </div>
              {dpdError && <p className="text-sm text-red-600 mt-2">{dpdError}</p>}
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

          <div className="md:border-l md:border-slate-100 md:pl-6">
            <h4 className="text-sm font-medium text-slate-600 mb-3">Cost Breakdown</h4>
            <dl className="space-y-1.5 text-sm">
              <div className="flex justify-between">
                <dt className="text-slate-400">Goods Net</dt>
                <dd className="text-slate-700">£{goodsNet.toFixed(2)}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-slate-400">Delivery</dt>
                <dd className="text-slate-700">£{deliveryNet.toFixed(2)}</dd>
              </div>
              <div className="flex justify-between border-t border-slate-100 pt-1.5">
                <dt className="text-slate-500 font-medium">Total Net</dt>
                <dd className="text-slate-800 font-medium">£{totalNet.toFixed(2)}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-slate-400">Total Tax ({vatRate.toString().replace(/\.0+$/, "")}%)</dt>
                <dd className="text-slate-700">£{totalTax.toFixed(2)}</dd>
              </div>
              <div className="flex justify-between border-t border-slate-200 pt-1.5">
                <dt className="text-slate-800 font-semibold">Total Order</dt>
                <dd className="text-slate-900 font-semibold">£{totalOrder.toFixed(2)}</dd>
              </div>
            </dl>
          </div>
          </div>
        </div>
      )}

      {error && (
        <div className="mb-4">
          <p className="text-sm text-red-600">{error}</p>
          {errorIsConflict && (
            <button
              onClick={() => window.location.reload()}
              className="mt-1 text-sm text-red-700 underline hover:text-red-800"
            >
              Reload this page
            </button>
          )}
        </div>
      )}

      {/* Fixed to the viewport (not the page) so it's reachable from any
          scroll position on what can be a genuinely long form - order
          details, lines, payments, DPD section etc. z-50 keeps it above
          everything else on the page; shadow-lg gives it visual separation
          from whatever's scrolling underneath it. */}
      <button
        onClick={() => saveMutation.mutate()}
        disabled={saveMutation.isPending || !customerName}
        className="fixed bottom-6 right-6 z-50 bg-emerald-600 text-white text-sm font-medium px-6 py-3 rounded-full shadow-lg hover:bg-emerald-500 disabled:opacity-50"
      >
        {saveMutation.isPending ? "Saving..." : isNew ? "Create Order" : "Save Order"}
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
