import { useRef, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { api } from "../api/client";
import { Location, MoveItemsResult, StockItemSummary } from "../types";
import { useAuth } from "../auth/AuthContext";
import ScanInput from "./components/ScanInput";
import HandheldBinSelect from "./components/HandheldBinSelect";

// Same scan/lookup/move mechanics as the desktop Stock Movement screen -
// just the handheld dark full-screen treatment and a real logged-in name
// for movedBy instead of a hardcoded placeholder.
export default function HandheldStockMovement() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [scanValue, setScanValue] = useState("");
  const [pending, setPending] = useState<StockItemSummary[]>([]);
  const [scanError, setScanError] = useState<string | null>(null);
  const [moveError, setMoveError] = useState<string | null>(null);
  const [toLocationId, setToLocationId] = useState("");
  const [result, setResult] = useState<MoveItemsResult | null>(null);
  const scanInputRef = useRef<HTMLInputElement>(null);

  const { data: locations } = useQuery({
    queryKey: ["locations"],
    queryFn: async () => (await api.get<Location[]>("/locations")).data,
  });

  const addToPending = (items: StockItemSummary[]) => {
    setPending((prev) => {
      const existingIds = new Set(prev.map((i) => i.id));
      const newOnes = items.filter((i) => !existingIds.has(i.id));
      return [...prev, ...newOnes];
    });
  };

  const scanMutation = useMutation({
    mutationFn: async (value: string) => {
      const looksLikeMac = /^[0-9a-fA-F]{2}([:\-]?[0-9a-fA-F]{2}){5}$/.test(value.trim());
      if (looksLikeMac) {
        return [(await api.get<StockItemSummary>(`/stock-items/mac/${encodeURIComponent(value)}`)).data];
      }
      try {
        return [(await api.get<StockItemSummary>(`/stock-items/serial/${encodeURIComponent(value)}`)).data];
      } catch {
        return (await api.get<StockItemSummary[]>(`/stock-items/batch/${encodeURIComponent(value)}`)).data;
      }
    },
    onSuccess: (items) => {
      addToPending(items);
      setScanError(null);
      setScanValue("");
      scanInputRef.current?.focus();
    },
    onError: (err: Error) => {
      setScanError(err.message);
      setScanValue("");
      scanInputRef.current?.focus();
    },
  });

  const moveMutation = useMutation({
    mutationFn: async () =>
      (
        await api.post<MoveItemsResult>("/stock-items/move", {
          stockItemIds: pending.map((i) => i.id),
          toLocationId: Number(toLocationId),
          movedBy: user?.name ?? "handheld",
        })
      ).data,
    onSuccess: (data) => {
      setResult(data);
      setMoveError(null);
      setPending([]);
      setToLocationId("");
    },
    onError: (err: Error) => {
      setMoveError(err.message);
      setResult(null);
    },
  });

  const removeItem = (id: number) => {
    setPending((prev) => prev.filter((i) => i.id !== id));
  };

  return (
    <div className="min-h-screen bg-slate-950 text-white flex flex-col">
      <header className="px-4 py-5 border-b border-slate-800 flex items-center gap-3">
        <button onClick={() => navigate("/handheld")} className="text-slate-400 active:text-slate-200 text-lg">
          ←
        </button>
        <div>
          <h1 className="text-lg font-bold">Stock Movement</h1>
          <p className="text-sm text-slate-400">Scan items, then pick a bin to move them to</p>
        </div>
      </header>

      <div className="flex-1 p-4 flex flex-col">
        {moveError && <div className="bg-red-950 text-red-300 text-sm rounded-xl px-4 py-3 mb-4">{moveError}</div>}

        {result && (
          <div
            className={`text-sm rounded-xl px-4 py-3 mb-4 ${
              result.skippedCount > 0 ? "bg-amber-950 text-amber-300" : "bg-emerald-950 text-emerald-300"
            }`}
          >
            <p>
              Moved {result.movedCount} item{result.movedCount === 1 ? "" : "s"}
              {result.skippedCount > 0 ? `, skipped ${result.skippedCount}` : ""}.
            </p>
            {result.skippedReasons.length > 0 && (
              <ul className="list-disc pl-5 mt-1">
                {result.skippedReasons.map((r, i) => (
                  <li key={i}>{r}</li>
                ))}
              </ul>
            )}
          </div>
        )}

        <form
          onSubmit={(e) => {
            e.preventDefault();
            if (scanValue.trim()) scanMutation.mutate(scanValue.trim());
          }}
          className="mb-2"
        >
          <ScanInput
            ref={scanInputRef}
            autoFocus
            value={scanValue}
            onChange={(e) => setScanValue(e.target.value)}
            placeholder="Scan MAC / serial / batch"
            className="w-full text-center text-lg bg-slate-900 rounded-lg px-4 py-4"
          />
        </form>
        {scanError && <p className="text-sm text-red-400 mb-3">{scanError}</p>}
        {!scanError && <div className="mb-3" />}

        <div className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden mb-4 flex-1">
          <div className="px-4 py-2 border-b border-slate-800 text-sm font-medium text-slate-400 flex justify-between">
            <span>Ready to move</span>
            <span>{pending.length} item(s)</span>
          </div>
          <div className="divide-y divide-slate-800 max-h-64 overflow-y-auto">
            {pending.length === 0 && <p className="px-4 py-6 text-sm text-slate-500">Nothing scanned yet.</p>}
            {pending.map((item) => (
              <div key={item.id} className="flex justify-between items-center px-4 py-3 text-sm">
                <div>
                  <p className="font-medium font-mono">{item.macAddress ?? item.serialNumber ?? `Item ${item.id}`}</p>
                  <p className="text-slate-400">
                    {item.productSku} - {item.productName}
                    {item.batchCode ? ` · Batch ${item.batchCode}` : ""}
                    {item.locationCode ? ` · Currently at ${item.locationCode}` : ""}
                  </p>
                </div>
                <button onClick={() => removeItem(item.id)} className="text-slate-500 active:text-red-400 px-2">
                  ✕
                </button>
              </div>
            ))}
          </div>
        </div>

        <div className="bg-slate-900 border border-slate-800 rounded-xl p-4">
          <label className="block text-sm text-slate-400 mb-2">Move to</label>
          <HandheldBinSelect bins={locations ?? []} value={toLocationId} onChange={setToLocationId} className="mb-3" />
          <button
            onClick={() => moveMutation.mutate()}
            disabled={pending.length === 0 || !toLocationId || moveMutation.isPending}
            className="w-full bg-emerald-600 text-white py-4 rounded-xl text-lg font-medium active:bg-emerald-500 disabled:opacity-50"
          >
            {moveMutation.isPending ? "Moving..." : `Move ${pending.length} Item${pending.length === 1 ? "" : "s"}`}
          </button>
        </div>
      </div>
    </div>
  );
}
