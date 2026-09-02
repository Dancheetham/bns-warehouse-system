import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api } from "../api/client";
import { BinProductGroup, Location, Product, StockItemSummary } from "../types";

const STATUS_COLORS: Record<string, string> = {
  AVAILABLE: "bg-emerald-100 text-emerald-700",
  QUARANTINED: "bg-amber-100 text-amber-700",
  ALLOCATED: "bg-blue-100 text-blue-700",
  DESPATCHED: "bg-slate-200 text-slate-600",
  RETURNED: "bg-purple-100 text-purple-700",
};

function StatusBadge({ status }: { status: string }) {
  return (
    <span className={`text-xs px-2 py-0.5 rounded font-medium ${STATUS_COLORS[status] ?? "bg-slate-100 text-slate-600"}`}>
      {status}
    </span>
  );
}

function UnitTable({
  items,
  defaultPassword,
  showLocation,
}: {
  items: { id: number; macAddress?: string; serialNumber?: string; wifiMacAddress?: string; batchCode?: string; status: string; locationCode?: string }[];
  defaultPassword?: string;
  showLocation?: boolean;
}) {
  const [filter, setFilter] = useState("");

  const filteredItems = useMemo(() => {
    const term = filter.trim().toLowerCase();
    if (!term) return items;
    return items.filter((item) =>
      [item.macAddress, item.serialNumber, item.wifiMacAddress, item.batchCode, item.status, item.locationCode, defaultPassword]
        .filter(Boolean)
        .some((field) => field!.toLowerCase().includes(term))
    );
  }, [items, filter, defaultPassword]);

  return (
    <div>
      {items.length > 3 && (
        <input
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
          placeholder="Filter by MAC, serial, batch, status, bin..."
          className="w-full text-sm border border-slate-300 focus:border-emerald-500 rounded px-3 py-1.5 mb-2 outline-none"
        />
      )}
      <table className="w-full text-sm">
        <thead className="text-left text-slate-500">
          <tr>
            <th className="py-1.5 pr-4">MAC</th>
            <th className="py-1.5 pr-4">Serial</th>
            <th className="py-1.5 pr-4">WiFi MAC</th>
            <th className="py-1.5 pr-4">Batch</th>
            {showLocation && <th className="py-1.5 pr-4">Bin</th>}
            <th className="py-1.5 pr-4">Password</th>
            <th className="py-1.5">Status</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-200">
          {filteredItems.map((item) => (
            <tr key={item.id}>
              <td className="py-1.5 pr-4 font-mono">{item.macAddress ?? "-"}</td>
              <td className="py-1.5 pr-4 font-mono">{item.serialNumber ?? "-"}</td>
              <td className="py-1.5 pr-4 font-mono">{item.wifiMacAddress ?? "-"}</td>
              <td className="py-1.5 pr-4">{item.batchCode ?? "-"}</td>
              {showLocation && <td className="py-1.5 pr-4">{item.locationCode ?? "-"}</td>}
              <td className="py-1.5 pr-4 font-mono">{defaultPassword ?? "-"}</td>
              <td className="py-1.5">
                <StatusBadge status={item.status} />
              </td>
            </tr>
          ))}
          {filteredItems.length === 0 && (
            <tr>
              <td colSpan={showLocation ? 7 : 6} className="py-3 text-slate-400">
                No units match "{filter}".
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

function BinProductRow({ group }: { group: BinProductGroup }) {
  const [open, setOpen] = useState(false);
  return (
    <div>
      <button
        onClick={() => setOpen((v) => !v)}
        className="w-full flex justify-between items-center px-4 py-2.5 hover:bg-white text-left"
      >
        <div className="flex items-center gap-3">
          <span className="text-slate-400 text-xs w-4">{open ? "▼" : "▶"}</span>
          <span className="font-medium text-slate-800">{group.productSku}</span>
          <span className="text-sm text-slate-500">{group.productName}</span>
        </div>
        <span className="text-xs text-slate-400">{group.items.length} unit(s)</span>
      </button>
      {open && (
        <div className="px-4 pb-3 pl-11">
          <UnitTable items={group.items} defaultPassword={group.defaultPassword} />
        </div>
      )}
    </div>
  );
}

function BinRow({ location }: { location: Location }) {
  const [open, setOpen] = useState(false);
  const { data: groups, isLoading } = useQuery({
    queryKey: ["bin-contents", location.id],
    queryFn: async () => (await api.get<BinProductGroup[]>(`/stock-items/location/${location.id}`)).data,
    enabled: open,
  });

  const totalUnits = groups?.reduce((sum, g) => sum + g.items.length, 0) ?? 0;

  return (
    <div>
      <button
        onClick={() => setOpen((v) => !v)}
        className="w-full flex justify-between items-center px-4 py-3 hover:bg-slate-50 text-left"
      >
        <div className="flex items-center gap-4">
          <span className="text-slate-400 text-sm w-4">{open ? "▼" : "▶"}</span>
          <div>
            <p className="font-medium text-slate-800">{location.code}</p>
            {location.description && <p className="text-sm text-slate-500">{location.description}</p>}
          </div>
        </div>
        {open && groups && <span className="text-xs text-slate-400">{totalUnits} unit(s) total</span>}
      </button>
      {open && (
        <div className="bg-slate-50 border-t border-slate-100">
          {isLoading && <p className="px-4 py-3 text-sm text-slate-400">Loading...</p>}
          {groups && groups.length === 0 && (
            <p className="px-4 py-3 text-sm text-slate-400">This bin is empty.</p>
          )}
          {groups && groups.length > 0 && (
            <div className="divide-y divide-slate-200">
              {groups.map((g) => (
                <BinProductRow key={g.productId} group={g} />
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function ProductLocationRow({ locationCode, items }: { locationCode: string; items: StockItemSummary[] }) {
  const [open, setOpen] = useState(false);
  return (
    <div>
      <button
        onClick={() => setOpen((v) => !v)}
        className="w-full flex justify-between items-center px-4 py-2.5 hover:bg-white text-left"
      >
        <div className="flex items-center gap-3">
          <span className="text-slate-400 text-xs w-4">{open ? "▼" : "▶"}</span>
          <span className="font-medium text-slate-800">{locationCode}</span>
        </div>
        <span className="text-xs text-slate-400">{items.length} unit(s)</span>
      </button>
      {open && (
        <div className="px-4 pb-3 pl-11">
          <UnitTable items={items} />
        </div>
      )}
    </div>
  );
}

function ProductRow({ product }: { product: Product }) {
  const [open, setOpen] = useState(false);
  const { data: items, isLoading } = useQuery({
    queryKey: ["product-items", product.id],
    queryFn: async () => (await api.get<StockItemSummary[]>(`/stock-items/product/${product.id}`)).data,
    enabled: open,
  });

  const byLocation = useMemo(() => {
    if (!items) return [];
    const map = new Map<string, StockItemSummary[]>();
    for (const item of items) {
      const key = item.locationCode ?? "Unassigned";
      if (!map.has(key)) map.set(key, []);
      map.get(key)!.push(item);
    }
    return [...map.entries()].sort(([a], [b]) => a.localeCompare(b));
  }, [items]);

  return (
    <div>
      <button
        onClick={() => setOpen((v) => !v)}
        className="w-full flex justify-between items-center px-4 py-3 hover:bg-slate-50 text-left"
      >
        <div className="flex items-center gap-4">
          <span className="text-slate-400 text-sm w-4">{open ? "▼" : "▶"}</span>
          <div>
            <p className="font-medium text-slate-800">{product.sku}</p>
            <p className="text-sm text-slate-500">{product.name}</p>
          </div>
        </div>
        {product.defaultPassword && (
          <span className="text-xs text-slate-400 font-mono">Password: {product.defaultPassword}</span>
        )}
      </button>
      {open && (
        <div className="bg-slate-50 border-t border-slate-100">
          {isLoading && <p className="px-4 py-3 text-sm text-slate-400">Loading...</p>}
          {items && items.length === 0 && (
            <p className="px-4 py-3 text-sm text-slate-400">No stock recorded for this product yet.</p>
          )}
          {byLocation.length > 0 && (
            <div className="divide-y divide-slate-200">
              {byLocation.map(([locationCode, locItems]) => (
                <ProductLocationRow key={locationCode} locationCode={locationCode} items={locItems} />
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export default function StockOverview() {
  const [tab, setTab] = useState<"bins" | "products">("bins");
  const [search, setSearch] = useState("");

  const { data: locations, isLoading: locationsLoading } = useQuery({
    queryKey: ["locations"],
    queryFn: async () => (await api.get<Location[]>("/locations")).data,
  });

  const { data: products, isLoading: productsLoading } = useQuery({
    queryKey: ["products"],
    queryFn: async () => (await api.get<Product[]>("/products")).data,
  });

  const sortedLocations = useMemo(
    () => [...(locations ?? [])].sort((a, b) => a.code.localeCompare(b.code)),
    [locations]
  );

  const filteredProducts = useMemo(() => {
    const list = products ?? [];
    const term = search.trim().toLowerCase();
    const filtered = term
      ? list.filter((p) => p.sku.toLowerCase().includes(term) || p.name.toLowerCase().includes(term))
      : list;
    return [...filtered].sort((a, b) => a.sku.localeCompare(b.sku));
  }, [products, search]);

  return (
    <div>
      <h2 className="text-2xl font-semibold text-slate-800 mb-6">Stock Overview</h2>

      <div className="flex gap-2 mb-4">
        <button
          onClick={() => setTab("bins")}
          className={`text-sm px-3 py-1.5 rounded-md font-medium ${
            tab === "bins" ? "bg-slate-800 text-white" : "bg-slate-100 text-slate-600 hover:bg-slate-200"
          }`}
        >
          By Bin
        </button>
        <button
          onClick={() => setTab("products")}
          className={`text-sm px-3 py-1.5 rounded-md font-medium ${
            tab === "products" ? "bg-slate-800 text-white" : "bg-slate-100 text-slate-600 hover:bg-slate-200"
          }`}
        >
          By Product
        </button>
      </div>

      {tab === "bins" && (
        <div className="bg-white rounded-lg shadow-sm border border-slate-200 divide-y divide-slate-100 overflow-hidden">
          {locationsLoading && <p className="px-4 py-4 text-slate-400 text-sm">Loading...</p>}
          {sortedLocations.map((loc) => (
            <BinRow key={loc.id} location={loc} />
          ))}
        </div>
      )}

      {tab === "products" && (
        <div>
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search by SKU or name..."
            className="w-full border-2 border-slate-300 focus:border-emerald-500 rounded-lg px-4 py-3 mb-4 outline-none text-sm"
          />
          <div className="bg-white rounded-lg shadow-sm border border-slate-200 divide-y divide-slate-100 overflow-hidden">
            {productsLoading && <p className="px-4 py-4 text-slate-400 text-sm">Loading...</p>}
            {!productsLoading && filteredProducts.length === 0 && (
              <p className="px-4 py-4 text-slate-400 text-sm">No products match "{search}".</p>
            )}
            {filteredProducts.map((p) => (
              <ProductRow key={p.id} product={p} />
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
