import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { api } from "../api/client";
import { Product, TrackingType, LocationStockSummary, Location } from "../types";

const TRACKING_TYPES: TrackingType[] = ["NONE", "SERIAL", "MAC"];

function ProductRow({ product }: { product: Product }) {
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();

  const { data: summary, isLoading } = useQuery({
    queryKey: ["stock-summary", product.id],
    queryFn: async () =>
      (await api.get<LocationStockSummary[]>(`/products/${product.id}/stock-summary`)).data,
    enabled: open,
  });

  const totalAvailable = summary?.reduce((sum, s) => sum + s.available, 0) ?? 0;
  const totalQuarantined = summary?.reduce((sum, s) => sum + s.quarantined, 0) ?? 0;

  return (
    <div>
      <div className="w-full flex justify-between items-center px-4 py-3 hover:bg-slate-50">
        <button onClick={() => setOpen((v) => !v)} className="flex items-center gap-4 flex-1 text-left">
          <span className="text-slate-400 text-sm w-4">{open ? "▼" : "▶"}</span>
          <div>
            <p className="font-medium text-slate-800">{product.sku}</p>
            <p className="text-sm text-slate-500">{product.name}</p>
            {product.defaultLocation && (
              <p className="text-xs text-slate-400">Default bin: {product.defaultLocation.code}</p>
            )}
          </div>
        </button>
        <div className="flex items-center gap-3">
          <span className="text-xs px-2 py-1 rounded bg-slate-100 text-slate-600">
            {product.trackingType}
          </span>
          {product.needsReview && (
            <span className="text-xs px-2 py-1 rounded bg-amber-100 text-amber-700" title="New from Shopify - confirm tracking type">
              Needs Review
            </span>
          )}
          {!product.active && (
            <span className="text-xs px-2 py-1 rounded bg-slate-200 text-slate-500">Inactive</span>
          )}
          <button
            onClick={() => navigate(`/products/${product.id}`)}
            className="text-xs text-slate-500 hover:text-slate-800 border border-slate-300 rounded px-2 py-1"
          >
            Edit
          </button>
        </div>
      </div>

      {open && (
        <div className="px-4 pb-4 bg-slate-50 border-t border-slate-100">
          {isLoading && <p className="text-sm text-slate-400 py-3">Loading stock...</p>}

          {summary && summary.length === 0 && (
            <p className="text-sm text-slate-400 py-3">No stock recorded for this product yet.</p>
          )}

          {summary && summary.length > 0 && (
            <table className="w-full text-sm mt-3">
              <thead className="text-left text-slate-500">
                <tr>
                  <th className="py-1.5 pr-4">Location</th>
                  <th className="py-1.5 pr-4">Available</th>
                  <th className="py-1.5 pr-4">Quarantined</th>
                  <th className="py-1.5 pr-4">Allocated</th>
                  <th className="py-1.5 pr-4">Despatched</th>
                  <th className="py-1.5 pr-4">Returned</th>
                  <th className="py-1.5">Total</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-200">
                {summary.map((s) => (
                  <tr key={s.locationId}>
                    <td className="py-1.5 pr-4 font-medium text-slate-800">{s.locationCode}</td>
                    <td className="py-1.5 pr-4 text-emerald-700">{s.available}</td>
                    <td className="py-1.5 pr-4 text-amber-700">{s.quarantined}</td>
                    <td className="py-1.5 pr-4">{s.allocated}</td>
                    <td className="py-1.5 pr-4">{s.despatched}</td>
                    <td className="py-1.5 pr-4">{s.returned}</td>
                    <td className="py-1.5 font-medium">{s.total}</td>
                  </tr>
                ))}
                <tr className="font-medium text-slate-700">
                  <td className="py-1.5 pr-4">All locations</td>
                  <td className="py-1.5 pr-4 text-emerald-700">{totalAvailable}</td>
                  <td className="py-1.5 pr-4 text-amber-700">{totalQuarantined}</td>
                  <td colSpan={4}></td>
                </tr>
              </tbody>
            </table>
          )}

          <p className="text-xs text-slate-400 mt-3">
            To move stock, use the Stock Movement screen - it lets you scan MACs, serials, or
            whole batches directly.
          </p>
        </div>
      )}
    </div>
  );
}

export default function Products() {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [sku, setSku] = useState("");
  const [name, setName] = useState("");
  const [trackingType, setTrackingType] = useState<TrackingType>("NONE");
  const [defaultLocationId, setDefaultLocationId] = useState("");
  const [weightKg, setWeightKg] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState("");

  const { data: products, isLoading } = useQuery({
    queryKey: ["products"],
    queryFn: async () => (await api.get<Product[]>("/products")).data,
  });

  const { data: locations } = useQuery({
    queryKey: ["locations"],
    queryFn: async () => (await api.get<Location[]>("/locations")).data,
  });

  const createMutation = useMutation({
    mutationFn: async () =>
      (
        await api.post("/products", {
          sku,
          name,
          trackingType,
          defaultLocationId: defaultLocationId ? Number(defaultLocationId) : undefined,
          weightKg: weightKg ? Number(weightKg) : undefined,
        })
      ).data,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["products"] });
      setSku("");
      setName("");
      setTrackingType("NONE");
      setDefaultLocationId("");
      setWeightKg("");
      setShowForm(false);
      setError(null);
    },
    onError: (err: Error) => setError(err.message),
  });

  const filteredProducts = useMemo(() => {
    const list = products ?? [];
    const term = search.trim().toLowerCase();
    const filtered = term
      ? list.filter(
          (p) => p.sku.toLowerCase().includes(term) || p.name.toLowerCase().includes(term)
        )
      : list;
    return [...filtered].sort((a, b) => a.sku.localeCompare(b.sku));
  }, [products, search]);

  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <h2 className="text-2xl font-semibold text-slate-800">Products</h2>
        <button
          onClick={() => setShowForm((v) => !v)}
          className="bg-slate-800 text-white text-sm px-4 py-2 rounded-md hover:bg-slate-700"
        >
          {showForm ? "Cancel" : "New Product"}
        </button>
      </div>

      {showForm && (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            createMutation.mutate();
          }}
          className="bg-white border border-slate-200 rounded-lg p-5 mb-6 grid grid-cols-4 gap-3 items-end"
        >
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">SKU</label>
            <input
              required
              value={sku}
              onChange={(e) => setSku(e.target.value)}
              className="w-full border border-slate-300 rounded px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Name</label>
            <input
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="w-full border border-slate-300 rounded px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Tracking Type</label>
            <select
              value={trackingType}
              onChange={(e) => setTrackingType(e.target.value as TrackingType)}
              className="w-full border border-slate-300 rounded px-3 py-2 text-sm"
            >
              {TRACKING_TYPES.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Default Bin</label>
            <select
              value={defaultLocationId}
              onChange={(e) => setDefaultLocationId(e.target.value)}
              className="w-full border border-slate-300 rounded px-3 py-2 text-sm"
            >
              <option value="">None</option>
              {locations?.map((l) => (
                <option key={l.id} value={l.id}>
                  {l.code}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Weight (kg)</label>
            <input
              type="number"
              step="0.001"
              min={0}
              value={weightKg}
              onChange={(e) => setWeightKg(e.target.value)}
              className="w-full border border-slate-300 rounded px-3 py-2 text-sm"
            />
          </div>
          <button
            type="submit"
            disabled={createMutation.isPending}
            className="bg-emerald-600 text-white text-sm px-4 py-2 rounded-md hover:bg-emerald-500 disabled:opacity-50"
          >
            {createMutation.isPending ? "Saving..." : "Save"}
          </button>
          {error && <p className="col-span-4 text-sm text-red-600">{error}</p>}
        </form>
      )}

      <input
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        placeholder="Search by SKU or name..."
        className="w-full border-2 border-slate-300 focus:border-emerald-500 rounded-lg px-4 py-3 mb-4 outline-none text-sm"
      />

      <div className="bg-white rounded-lg shadow-sm border border-slate-200 divide-y divide-slate-100 overflow-hidden">
        {isLoading && <p className="px-4 py-4 text-slate-400 text-sm">Loading...</p>}
        {!isLoading && filteredProducts.length === 0 && (
          <p className="px-4 py-4 text-slate-400 text-sm">No products match "{search}".</p>
        )}
        {filteredProducts.map((p) => (
          <ProductRow key={p.id} product={p} />
        ))}
      </div>
    </div>
  );
}
