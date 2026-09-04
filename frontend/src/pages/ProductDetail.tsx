import { useState, useEffect } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useParams } from "react-router-dom";
import { api } from "../api/client";
import { Product, TrackingType, LocationStockSummary, Location } from "../types";
import BinSelect from "../components/BinSelect";

const TRACKING_TYPES: TrackingType[] = ["NONE", "SERIAL", "MAC"];

export default function ProductDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const { data: product, isLoading } = useQuery({
    queryKey: ["product", id],
    queryFn: async () => (await api.get<Product>(`/products/${id}`)).data,
  });

  const { data: locations } = useQuery({
    queryKey: ["locations"],
    queryFn: async () => (await api.get<Location[]>("/locations")).data,
  });

  const { data: summary, isLoading: summaryLoading } = useQuery({
    queryKey: ["stock-summary", id],
    queryFn: async () => (await api.get<LocationStockSummary[]>(`/products/${id}/stock-summary`)).data,
  });

  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [trackingType, setTrackingType] = useState<TrackingType>("NONE");
  const [defaultPassword, setDefaultPassword] = useState("");
  const [defaultLocationId, setDefaultLocationId] = useState("");
  const [weightKg, setWeightKg] = useState("");
  const [active, setActive] = useState(true);

  // Populate the form once the product loads - a plain useState default can't
  // do this since the product isn't there yet on first render.
  useEffect(() => {
    if (!product) return;
    setName(product.name);
    setDescription(product.description ?? "");
    setTrackingType(product.trackingType);
    setDefaultPassword(product.defaultPassword ?? "");
    setDefaultLocationId(product.defaultLocation ? String(product.defaultLocation.id) : "");
    setWeightKg(product.weightKg != null ? String(product.weightKg) : "");
    setActive(product.active);
  }, [product]);

  const updateMutation = useMutation({
    mutationFn: async () =>
      api.put(`/products/${id}`, {
        sku: product?.sku,
        name,
        description: description || undefined,
        trackingType,
        defaultPassword: defaultPassword || undefined,
        defaultLocationId: defaultLocationId ? Number(defaultLocationId) : undefined,
        weightKg: weightKg ? Number(weightKg) : undefined,
        active,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["product", id] });
      queryClient.invalidateQueries({ queryKey: ["products"] });
      setError(null);
      setSaved(true);
      setTimeout(() => setSaved(false), 2500);
    },
    onError: (err: Error) => setError(err.message),
  });

  const totalAvailable = summary?.reduce((sum, s) => sum + s.available, 0) ?? 0;
  const totalQuarantined = summary?.reduce((sum, s) => sum + s.quarantined, 0) ?? 0;

  if (isLoading || !product) return <p className="text-slate-500">Loading...</p>;

  return (
    <div className="max-w-3xl">
      <button onClick={() => navigate("/products")} className="text-sm text-slate-500 hover:text-slate-700 mb-3">
        ← Back to Products
      </button>

      <div className="flex items-center gap-3 mb-1">
        <h2 className="text-2xl font-semibold text-slate-800">{product.sku}</h2>
        {product.needsReview && (
          <span className="text-xs px-2 py-1 rounded bg-amber-100 text-amber-700">Needs Review</span>
        )}
        {product.shopifyProductId && (
          <span className="text-xs px-2 py-1 rounded bg-emerald-100 text-emerald-700">Synced from Shopify</span>
        )}
      </div>
      <p className="text-slate-500 mb-6">{product.name}</p>

      {error && <div className="bg-red-50 text-red-700 text-sm rounded-lg px-4 py-2 mb-4">{error}</div>}

      <form
        onSubmit={(e) => {
          e.preventDefault();
          updateMutation.mutate();
        }}
        className="bg-white border border-slate-200 rounded-lg p-5 mb-6"
      >
        <h3 className="font-medium text-slate-700 mb-4">Details</h3>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-4">
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Name</label>
            <input required value={name} onChange={(e) => setName(e.target.value)} className="input" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Tracking Type</label>
            <select
              value={trackingType}
              onChange={(e) => setTrackingType(e.target.value as TrackingType)}
              className="input"
            >
              {TRACKING_TYPES.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
          </div>
          <div className="sm:col-span-2">
            <label className="block text-xs font-medium text-slate-500 mb-1">Description</label>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              rows={2}
              className="input"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Default Bin</label>
            <BinSelect bins={locations ?? []} value={defaultLocationId} onChange={setDefaultLocationId} placeholder="None" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Default Password</label>
            <input value={defaultPassword} onChange={(e) => setDefaultPassword(e.target.value)} className="input" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Weight (kg)</label>
            <input
              type="number"
              step="0.001"
              min={0}
              value={weightKg}
              onChange={(e) => setWeightKg(e.target.value)}
              className="input"
            />
          </div>
          <div className="flex items-center">
            <label className="flex items-center gap-2 text-sm text-slate-700 mt-4">
              <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
              Active
            </label>
          </div>
        </div>

        <button
          type="submit"
          disabled={updateMutation.isPending}
          className="bg-emerald-600 text-white text-sm px-5 py-2.5 rounded-md hover:bg-emerald-500 disabled:opacity-50"
        >
          {updateMutation.isPending ? "Saving..." : "Save Changes"}
        </button>
        {saved && <span className="ml-3 text-sm text-emerald-600">Saved.</span>}
      </form>

      <div className="bg-white border border-slate-200 rounded-lg p-5">
        <h3 className="font-medium text-slate-700 mb-4">Stock Levels</h3>
        {summaryLoading && <p className="text-sm text-slate-400">Loading stock...</p>}
        {summary && summary.length === 0 && (
          <p className="text-sm text-slate-400">No stock recorded for this product yet.</p>
        )}
        {summary && summary.length > 0 && (
          <table className="w-full text-sm">
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
            <tbody className="divide-y divide-slate-100">
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
          To move stock, use the Stock Movement screen - it lets you scan MACs, serials, or whole batches directly.
        </p>
      </div>
    </div>
  );
}
