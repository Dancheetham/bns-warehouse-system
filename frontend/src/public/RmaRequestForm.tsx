import { useMemo, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { api } from "../api/client";
import { Product, RmaItemSubmission, RmaLookupResult, RmaSubmissionRequest } from "../types";

interface ItemRow {
  key: string;
  productId: number | null;
  productQuery: string;
  identifier: string;
  quantity: string;
  faulty: boolean;
  grandstreamTicketNumber: string;
  reasonForReturn: string;
  lookup: RmaLookupResult | null;
  looking: boolean;
}

function newRow(): ItemRow {
  return {
    key: Math.random().toString(36).slice(2),
    productId: null,
    productQuery: "",
    identifier: "",
    quantity: "1",
    faulty: false,
    grandstreamTicketNumber: "",
    reasonForReturn: "",
    lookup: null,
    looking: false,
  };
}

export default function RmaRequestForm() {
  const [customerName, setCustomerName] = useState("");
  const [customerCompany, setCustomerCompany] = useState("");
  const [customerAddress, setCustomerAddress] = useState("");
  const [contactName, setContactName] = useState("");
  const [contactPhone, setContactPhone] = useState("");
  const [contactEmail, setContactEmail] = useState("");
  const [deliveryName, setDeliveryName] = useState("");
  const [deliveryTown, setDeliveryTown] = useState("");
  const [deliveryCountry, setDeliveryCountry] = useState("");
  const [deliveryPostcode, setDeliveryPostcode] = useState("");
  const [deliveryCountryCode, setDeliveryCountryCode] = useState("");
  const [rows, setRows] = useState<ItemRow[]>([newRow()]);
  const [reference, setReference] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const { data: products } = useQuery({
    queryKey: ["products"],
    queryFn: async () => (await api.get<Product[]>("/products")).data,
  });

  const anyFaulty = rows.some((r) => r.faulty);

  const updateRow = (key: string, patch: Partial<ItemRow>) => {
    setRows((prev) => prev.map((r) => (r.key === key ? { ...r, ...patch } : r)));
  };

  const runLookup = async (key: string, identifier: string, faulty: boolean) => {
    if (!identifier.trim()) {
      updateRow(key, { lookup: null });
      return;
    }
    updateRow(key, { looking: true });
    try {
      const res = await api.get<RmaLookupResult>("/rma-requests/lookup", { params: { identifier, faulty } });
      updateRow(key, { lookup: res.data, looking: false });
    } catch {
      updateRow(key, { looking: false });
    }
  };

  const submitMutation = useMutation({
    mutationFn: async () => {
      const items: RmaItemSubmission[] = rows.map((r) => ({
        productId: r.productId as number,
        identifier: r.identifier || undefined,
        quantity: Number(r.quantity) || 1,
        faulty: r.faulty,
        grandstreamTicketNumber: r.grandstreamTicketNumber || undefined,
        reasonForReturn: r.reasonForReturn || undefined,
      }));
      const body: RmaSubmissionRequest = {
        customerName,
        customerCompany: customerCompany || undefined,
        customerAddress: customerAddress || undefined,
        contactName: contactName || undefined,
        contactPhone: contactPhone || undefined,
        contactEmail: contactEmail || undefined,
        deliveryName: deliveryName || undefined,
        deliveryTown: deliveryTown || undefined,
        deliveryCountry: deliveryCountry || undefined,
        deliveryPostcode: deliveryPostcode || undefined,
        deliveryCountryCode: deliveryCountryCode || undefined,
        items,
      };
      return (await api.post<{ publicReference: string }>("/rma-requests", body)).data;
    },
    onSuccess: (data) => setReference(data.publicReference),
    onError: (err: Error) => setError(err.message),
  });

  const canSubmit =
    customerName.trim() &&
    rows.length > 0 &&
    rows.every((r) => r.productId) &&
    (!anyFaulty || (deliveryName.trim() && deliveryTown.trim() && deliveryPostcode.trim()));

  if (reference) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center p-6">
        <div className="bg-white border border-slate-200 rounded-xl p-8 max-w-md text-center shadow-sm">
          <h1 className="text-2xl font-semibold text-slate-800 mb-2">Request submitted</h1>
          <p className="text-slate-600 mb-4">
            Thanks - your reference number is:
          </p>
          <p className="text-2xl font-mono font-bold text-emerald-600 mb-4">{reference}</p>
          <p className="text-slate-500 text-sm">
            We'll review your request and be in touch by email once it's been checked.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50 py-10 px-4">
      <div className="max-w-2xl mx-auto bg-white border border-slate-200 rounded-xl shadow-sm p-6 sm:p-8">
        <h1 className="text-2xl font-semibold text-slate-800 mb-1">Return / RMA Request</h1>
        <p className="text-slate-500 mb-6">
          Fill in your details and the item(s) you'd like to return. We'll match them against your order
          automatically where we can.
        </p>

        {error && <div className="bg-red-50 text-red-700 text-sm rounded-lg px-4 py-2 mb-4">{error}</div>}

        <section className="mb-6">
          <h2 className="font-medium text-slate-700 mb-3">Your Details</h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Field label="Name *">
              <input value={customerName} onChange={(e) => setCustomerName(e.target.value)} className="input" />
            </Field>
            <Field label="Company">
              <input value={customerCompany} onChange={(e) => setCustomerCompany(e.target.value)} className="input" />
            </Field>
            <Field label="Address" full>
              <textarea value={customerAddress} onChange={(e) => setCustomerAddress(e.target.value)} rows={2} className="input" />
            </Field>
            <Field label="Contact Name">
              <input value={contactName} onChange={(e) => setContactName(e.target.value)} className="input" />
            </Field>
            <Field label="Phone">
              <input value={contactPhone} onChange={(e) => setContactPhone(e.target.value)} className="input" />
            </Field>
            <Field label="Email" full>
              <input value={contactEmail} onChange={(e) => setContactEmail(e.target.value)} className="input" />
            </Field>
          </div>
        </section>

        <section className="mb-6">
          <h2 className="font-medium text-slate-700 mb-3">Items to Return</h2>
          <div className="space-y-4">
            {rows.map((row, idx) => (
              <ItemRowEditor
                key={row.key}
                row={row}
                index={idx}
                products={products ?? []}
                onChange={(patch) => updateRow(row.key, patch)}
                onLookup={(identifier, faulty) => runLookup(row.key, identifier, faulty)}
                onRemove={() => setRows((prev) => prev.filter((r) => r.key !== row.key))}
                removable={rows.length > 1}
              />
            ))}
          </div>
          <button
            onClick={() => setRows((prev) => [...prev, newRow()])}
            className="mt-3 text-sm text-emerald-700 hover:text-emerald-800 font-medium"
          >
            + Add another item
          </button>
        </section>

        {anyFaulty && (
          <section className="mb-6 bg-amber-50 border border-amber-200 rounded-lg p-4">
            <h2 className="font-medium text-slate-700 mb-1">Delivery Address for Replacement</h2>
            <p className="text-sm text-slate-500 mb-3">
              At least one item is marked faulty - if approved, a replacement may be shipped here.
            </p>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <Field label="Delivery Name *">
                <input value={deliveryName} onChange={(e) => setDeliveryName(e.target.value)} className="input" />
              </Field>
              <Field label="Town/City *">
                <input value={deliveryTown} onChange={(e) => setDeliveryTown(e.target.value)} className="input" />
              </Field>
              <Field label="Postcode *">
                <input value={deliveryPostcode} onChange={(e) => setDeliveryPostcode(e.target.value)} className="input" />
              </Field>
              <Field label="Country">
                <input value={deliveryCountry} onChange={(e) => setDeliveryCountry(e.target.value)} className="input" />
              </Field>
              <Field label="Country Code">
                <input value={deliveryCountryCode} onChange={(e) => setDeliveryCountryCode(e.target.value)} className="input" />
              </Field>
            </div>
          </section>
        )}

        <button
          onClick={() => submitMutation.mutate()}
          disabled={!canSubmit || submitMutation.isPending}
          className="w-full bg-emerald-600 text-white py-3 rounded-lg font-medium hover:bg-emerald-500 disabled:opacity-40"
        >
          {submitMutation.isPending ? "Submitting..." : "Submit Return Request"}
        </button>
      </div>
    </div>
  );
}

function Field({ label, full, children }: { label: string; full?: boolean; children: React.ReactNode }) {
  return (
    <div className={full ? "sm:col-span-2" : ""}>
      <label className="block text-xs font-medium text-slate-500 mb-1">{label}</label>
      {children}
    </div>
  );
}

function ItemRowEditor({
  row,
  index,
  products,
  onChange,
  onLookup,
  onRemove,
  removable,
}: {
  row: ItemRow;
  index: number;
  products: Product[];
  onChange: (patch: Partial<ItemRow>) => void;
  onLookup: (identifier: string, faulty: boolean) => void;
  onRemove: () => void;
  removable: boolean;
}) {
  const matches = useMemo(() => {
    if (!row.productQuery.trim()) return [];
    const q = row.productQuery.toLowerCase();
    return products.filter((p) => p.sku.toLowerCase().includes(q) || p.name.toLowerCase().includes(q)).slice(0, 8);
  }, [row.productQuery, products]);

  const selectedProduct = products.find((p) => p.id === row.productId);

  return (
    <div className="border border-slate-200 rounded-lg p-4 relative">
      {removable && (
        <button onClick={onRemove} className="absolute top-3 right-3 text-xs text-slate-400 hover:text-red-500">
          Remove
        </button>
      )}
      <p className="text-xs font-medium text-slate-400 mb-2">Item {index + 1}</p>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mb-3">
        <Field label="Product *">
          {selectedProduct ? (
            <div className="flex items-center justify-between border border-slate-300 rounded px-3 py-2 text-sm bg-slate-50">
              <span>
                {selectedProduct.sku} - {selectedProduct.name}
              </span>
              <button onClick={() => onChange({ productId: null, productQuery: "" })} className="text-xs text-slate-400">
                change
              </button>
            </div>
          ) : (
            <div className="relative">
              <input
                value={row.productQuery}
                onChange={(e) => onChange({ productQuery: e.target.value })}
                placeholder="Search by SKU or name..."
                className="input"
              />
              {matches.length > 0 && (
                <div className="absolute z-10 bg-white border border-slate-200 rounded shadow-sm mt-1 w-full max-h-48 overflow-y-auto">
                  {matches.map((p) => (
                    <button
                      key={p.id}
                      onClick={() => onChange({ productId: p.id, productQuery: "" })}
                      className="block w-full text-left px-3 py-2 text-sm hover:bg-slate-50"
                    >
                      {p.sku} - {p.name}
                    </button>
                  ))}
                </div>
              )}
            </div>
          )}
        </Field>
        <Field label="Quantity">
          <input
            type="number"
            min={1}
            value={row.quantity}
            onChange={(e) => onChange({ quantity: e.target.value })}
            className="input"
          />
        </Field>
      </div>

      <Field label="MAC Address / Serial Number">
        <input
          value={row.identifier}
          onChange={(e) => onChange({ identifier: e.target.value })}
          onBlur={() => onLookup(row.identifier, row.faulty)}
          className="input"
        />
      </Field>
      {row.looking && <p className="text-xs text-slate-400 mt-1">Checking...</p>}
      {row.lookup && !row.looking && (
        <p className={`text-xs mt-1 ${row.lookup.orderMatched ? "text-emerald-600" : "text-amber-600"}`}>
          {row.lookup.orderMatched
            ? `Matched to order ${row.lookup.orderNumber} (${row.lookup.orderDate}). ${
                row.lookup.returnWindowValid ? "Within" : "Outside"
              } the ${row.lookup.returnWindowDays}-day ${row.faulty ? "RTB warranty" : "return"} window.`
            : "Not matched to an order automatically - we'll verify this by hand."}
        </p>
      )}

      <div className="mt-3">
        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input
            type="checkbox"
            checked={row.faulty}
            onChange={(e) => {
              onChange({ faulty: e.target.checked });
              if (row.identifier.trim()) onLookup(row.identifier, e.target.checked);
            }}
          />
          This item is faulty
        </label>
      </div>

      {row.faulty && (
        <div className="mt-2">
          <Field label="Grandstream Support Ticket Number">
            <input
              value={row.grandstreamTicketNumber}
              onChange={(e) => onChange({ grandstreamTicketNumber: e.target.value })}
              placeholder="e.g. 20240115093422"
              className="input"
            />
          </Field>
        </div>
      )}

      <div className="mt-3">
        <Field label="Reason for Return">
          <textarea
            value={row.reasonForReturn}
            onChange={(e) => onChange({ reasonForReturn: e.target.value })}
            rows={2}
            className="input"
          />
        </Field>
      </div>
    </div>
  );
}
