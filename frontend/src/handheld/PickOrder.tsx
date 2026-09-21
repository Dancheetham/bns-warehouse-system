import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useParams } from "react-router-dom";
import { api } from "../api/client";
import { PickOrderView, PickScanResult } from "../types";
import ScanInput from "./components/ScanInput";
import { useAuth } from "../auth/AuthContext";

export default function PickOrder() {
  const { orderId } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const inputRef = useRef<HTMLInputElement>(null);
  const { user } = useAuth();

  const [scanValue, setScanValue] = useState("");
  const [message, setMessage] = useState<{ type: "error" | "info"; text: string } | null>(null);
  const [lastAllocatedIds, setLastAllocatedIds] = useState<number[]>([]);
  const [qtyEntry, setQtyEntry] = useState("");

  const { data: view } = useQuery({
    queryKey: ["pick-order", orderId],
    queryFn: async () => (await api.get<PickOrderView>(`/orders/${orderId}/picking`)).data,
  });

  const startMutation = useMutation({
    // Who's picking is attributed server-side from the actual logged-in
    // session, not anything sent here - see PickingController.
    mutationFn: async () => api.post(`/orders/${orderId}/picking/start`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["pick-order", orderId] }),
  });

  // Kick off (idempotent) picking as soon as the order's loaded.
  useEffect(() => {
    if (view && view.pickingStatus === "NOT_STARTED") {
      startMutation.mutate();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [view?.pickingStatus]);

  useEffect(() => {
    inputRef.current?.focus();
  }, [view]);

  const currentLine = view?.lines.find((l) => !l.complete);

  const scanMutation = useMutation({
    mutationFn: async (code: string) =>
      (
        await api.post<PickScanResult>(`/orders/${orderId}/picking/scan`, {
          orderLineId: currentLine?.orderLineId,
          code,
        })
      ).data,
    onSuccess: (result) => {
      queryClient.setQueryData(["pick-order", orderId], result.view);
      setLastAllocatedIds(result.allocatedStockItemIds);
      setMessage(null);
      setScanValue("");
    },
    onError: (err: Error) => {
      setMessage({ type: "error", text: err.message });
      setScanValue("");
    },
  });

  const qtyMutation = useMutation({
    mutationFn: async (quantity: number) =>
      (
        await api.post<PickScanResult>(`/orders/${orderId}/picking/quantity`, {
          orderLineId: currentLine?.orderLineId,
          quantity,
        })
      ).data,
    onSuccess: (result) => {
      queryClient.setQueryData(["pick-order", orderId], result.view);
      setLastAllocatedIds(result.allocatedStockItemIds);
      setMessage(null);
      setQtyEntry("");
    },
    onError: (err: Error) => setMessage({ type: "error", text: err.message }),
  });

  const undoMutation = useMutation({
    mutationFn: async (stockItemId: number) => api.post(`/orders/${orderId}/picking/undo`, { stockItemId }),
  });

  const completeMutation = useMutation({
    mutationFn: async () => api.post(`/orders/${orderId}/picking/complete`),
    onSuccess: () => navigate("/handheld/pick", { replace: true }),
  });

  const handleUndoLast = async () => {
    for (const id of lastAllocatedIds) {
      await undoMutation.mutateAsync(id);
    }
    setLastAllocatedIds([]);
    queryClient.invalidateQueries({ queryKey: ["pick-order", orderId] });
  };

  if (!view) {
    return <div className="min-h-screen bg-slate-950 text-white flex items-center justify-center">Loading...</div>;
  }

  const allComplete = view.lines.every((l) => l.complete);

  return (
    <div className="min-h-screen bg-slate-950 text-white flex flex-col">
      <header className="px-4 py-3 flex items-center justify-between border-b border-slate-800">
        <button onClick={() => navigate("/handheld/pick")} className="text-slate-400 text-sm">
          ← Orders
        </button>
        <span className="text-lg font-bold">{view.orderNumber}</span>
        <span className="text-xs text-slate-500">{user?.name}</span>
      </header>
      <p className="text-center text-slate-400 text-sm py-1 border-b border-slate-900">{view.customerName}</p>

      {/* Progress across all lines */}
      <div className="flex gap-1 p-3">
        {view.lines.map((l) => (
          <div
            key={l.orderLineId}
            className={`h-1.5 flex-1 rounded-full ${l.complete ? "bg-emerald-500" : "bg-slate-700"}`}
          />
        ))}
      </div>

      <div className="flex-1 flex flex-col p-4">
        {currentLine ? (
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 flex-1 flex flex-col">
            <p className="text-slate-400 text-sm">{currentLine.sku}</p>
            <h2 className="text-2xl font-bold mb-3 leading-snug">{currentLine.productName}</h2>

            <div className="bg-slate-950 rounded-xl p-4 mb-4 text-center">
              <p className="text-slate-500 text-xs uppercase tracking-wide">Default Bin</p>
              <p className="text-4xl font-extrabold my-1">{currentLine.defaultBinCode ?? "—"}</p>
              <p className="text-slate-400 text-sm">
                {currentLine.defaultBinAvailable} here · {currentLine.totalAvailable} total available
              </p>
            </div>

            <div className="flex items-center justify-center gap-2 mb-4">
              <span className="text-3xl font-extrabold">{currentLine.quantityPicked}</span>
              <span className="text-xl text-slate-500">/ {currentLine.quantityOrdered} picked</span>
            </div>

            {message && (
              <div
                className={`rounded-lg px-3 py-2 mb-3 text-sm text-center ${
                  message.type === "error" ? "bg-red-900/60 text-red-200" : "bg-slate-800 text-slate-200"
                }`}
              >
                {message.text}
              </div>
            )}

            {currentLine.requiresScan ? (
              <ScanInput
                ref={inputRef}
                value={scanValue}
                onChange={(e) => setScanValue(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter" && scanValue.trim()) {
                    scanMutation.mutate(scanValue.trim());
                  }
                }}
                autoFocus
                placeholder="Scan MAC / serial / batch"
                className="w-full text-center text-lg bg-slate-800 rounded-lg px-4 py-4 mb-3"
              />
            ) : (
              <div className="flex items-center gap-2 mb-3">
                <input
                  ref={inputRef}
                  type="number"
                  value={qtyEntry}
                  onChange={(e) => setQtyEntry(e.target.value)}
                  placeholder={`Qty (up to ${currentLine.quantityOrdered - currentLine.quantityPicked})`}
                  className="flex-1 text-center text-lg bg-slate-800 rounded-lg px-4 py-4"
                />
                <button
                  onClick={() => qtyEntry && qtyMutation.mutate(Number(qtyEntry))}
                  className="bg-emerald-600 px-5 py-4 rounded-lg font-semibold active:bg-emerald-500"
                >
                  Confirm
                </button>
              </div>
            )}

            <div className="mt-auto flex gap-2 pt-3">
              <button
                onClick={handleUndoLast}
                disabled={lastAllocatedIds.length === 0}
                className="flex-1 bg-slate-800 py-3 rounded-lg text-sm disabled:opacity-40"
              >
                Undo Last Scan
              </button>
              <button
                onClick={() => setMessage({ type: "info", text: "Short-pick this line and move on from Finish Pick below." })}
                className="flex-1 bg-slate-800 py-3 rounded-lg text-sm"
              >
                Can't Find Stock
              </button>
            </div>
          </div>
        ) : (
          <div className="flex-1 flex items-center justify-center text-center text-slate-300">
            <p className="text-xl">All lines picked.</p>
          </div>
        )}

        <button
          onClick={() => completeMutation.mutate()}
          disabled={completeMutation.isPending}
          className={`mt-4 py-4 rounded-xl font-bold text-lg ${
            allComplete ? "bg-emerald-600 active:bg-emerald-500" : "bg-amber-700 active:bg-amber-600"
          }`}
        >
          {allComplete ? "Finish Pick" : "Finish Pick (short on stock)"}
        </button>
      </div>
    </div>
  );
}
