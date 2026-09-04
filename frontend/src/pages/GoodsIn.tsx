import { useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../api/client";
import { PurchaseOrder, Location, ScanCartonResult, GoodsInSession } from "../types";
import BinSelect from "../components/BinSelect";

interface ScanLogEntry extends ScanCartonResult {
  batchCode: string;
  key: string;
}

export default function GoodsIn() {
  const queryClient = useQueryClient();
  const [purchaseOrderId, setPurchaseOrderId] = useState("");
  const [locationId, setLocationId] = useState("");
  const [session, setSession] = useState<GoodsInSession | null>(null);
  const [scanValue, setScanValue] = useState("");
  const [log, setLog] = useState<ScanLogEntry[]>([]);
  const [savedMessage, setSavedMessage] = useState<string | null>(null);
  const scanInputRef = useRef<HTMLInputElement>(null);

  const { data: purchaseOrders } = useQuery({
    queryKey: ["purchase-orders"],
    queryFn: async () => (await api.get<PurchaseOrder[]>("/purchase-orders")).data,
  });

  const eligiblePOs = purchaseOrders?.filter(
    (po) => po.status === "AWAITING_STOCK" || po.status === "PART_RECEIVED"
  );

  const { data: locations } = useQuery({
    queryKey: ["locations"],
    queryFn: async () => (await api.get<Location[]>("/locations")).data,
  });

  const startSessionMutation = useMutation({
    mutationFn: async () =>
      (
        await api.post<GoodsInSession>("/goods-in/sessions", {
          purchaseOrderId: Number(purchaseOrderId),
          locationId: Number(locationId),
          startedBy: "warehouse",
        })
      ).data,
    onSuccess: (data) => {
      setSession(data);
      setLog([]);
      setSavedMessage(null);
      setTimeout(() => scanInputRef.current?.focus(), 50);
    },
  });

  const scanMutation = useMutation({
    mutationFn: async (batchCode: string) =>
      (
        await api.post<ScanCartonResult>(`/goods-in/sessions/${session?.id}/scan`, {
          batchCode,
          scannedBy: "warehouse",
        })
      ).data,
    onSuccess: (data, batchCode) => {
      // Mirrors the current system: a duplicate scan within the same session is
      // simply not shown / not beeped - the operator never leaves the scan flow.
      if (data.status !== "ALREADY_IN_SESSION") {
        setLog((prev) => [
          { ...data, batchCode, key: `${batchCode}-${Date.now()}` },
          ...prev,
        ]);
      }
      setScanValue("");
      scanInputRef.current?.focus();
    },
    onError: () => {
      setScanValue("");
      scanInputRef.current?.focus();
    },
  });

  const saveMutation = useMutation({
    mutationFn: async () =>
      (await api.post<GoodsInSession>(`/goods-in/sessions/${session?.id}/save`, { savedBy: "warehouse" })).data,
    onSuccess: () => {
      setSavedMessage("Session saved. Stock has been booked in.");
      setSession(null);
      setLog([]);
      queryClient.invalidateQueries({ queryKey: ["purchase-orders"] });
    },
  });

  const handleScanSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!scanValue.trim() || !session) return;
    scanMutation.mutate(scanValue.trim());
  };

  const addedCount = log.filter((l) => l.status === "ADDED").length;

  if (!session) {
    return (
      <div>
        <h2 className="text-2xl font-semibold text-slate-800 mb-6">Goods In</h2>

        {savedMessage && (
          <p className="mb-4 text-sm text-emerald-700 bg-emerald-50 border border-emerald-200 rounded px-4 py-2">
            {savedMessage}
          </p>
        )}

        <div className="bg-white rounded-lg shadow-sm border border-slate-200 p-5 max-w-lg">
          <h3 className="font-medium text-slate-700 mb-4">Start a booking-in session</h3>
          <div className="space-y-3">
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1">Purchase Order</label>
              <select
                value={purchaseOrderId}
                onChange={(e) => setPurchaseOrderId(e.target.value)}
                className="w-full border border-slate-300 rounded px-3 py-2 text-sm"
              >
                <option value="">Select a PO awaiting stock...</option>
                {eligiblePOs?.map((po) => (
                  <option key={po.id} value={po.id}>
                    {po.poNumber} - {po.supplier.name}
                  </option>
                ))}
              </select>
              {eligiblePOs?.length === 0 && (
                <p className="text-xs text-slate-400 mt-1">
                  No purchase orders are ready - import a shipment spreadsheet first.
                </p>
              )}
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1">Destination Bin</label>
              <BinSelect bins={locations ?? []} value={locationId} onChange={setLocationId} />
            </div>
            <button
              disabled={!purchaseOrderId || !locationId || startSessionMutation.isPending}
              onClick={() => startSessionMutation.mutate()}
              className="bg-slate-800 text-white text-sm px-4 py-2 rounded-md hover:bg-slate-700 disabled:opacity-50"
            >
              {startSessionMutation.isPending ? "Starting..." : "Start Session"}
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div>
      <div className="flex justify-between items-start mb-6">
        <div>
          <h2 className="text-2xl font-semibold text-slate-800">
            Booking in {session.purchaseOrder.poNumber}
          </h2>
          <p className="text-slate-500">Into {session.location.code}</p>
        </div>
        <div className="text-right">
          <p className="text-3xl font-semibold text-emerald-600">{addedCount}</p>
          <p className="text-xs text-slate-400">cartons scanned</p>
        </div>
      </div>

      <form onSubmit={handleScanSubmit} className="mb-6">
        <input
          ref={scanInputRef}
          autoFocus
          value={scanValue}
          onChange={(e) => setScanValue(e.target.value)}
          placeholder="Scan carton barcode..."
          className="w-full text-lg border-2 border-slate-300 focus:border-emerald-500 rounded-lg px-4 py-4 outline-none"
        />
      </form>

      <div className="flex gap-3 mb-6">
        <button
          onClick={() => saveMutation.mutate()}
          disabled={addedCount === 0 || saveMutation.isPending}
          className="bg-emerald-600 text-white text-sm px-4 py-2 rounded-md hover:bg-emerald-500 disabled:opacity-50"
        >
          {saveMutation.isPending ? "Saving..." : "Save & Complete"}
        </button>
        <button
          onClick={() => setSession(null)}
          className="text-sm text-slate-500 hover:text-slate-800 px-4 py-2"
        >
          Pause (part-book and come back later)
        </button>
      </div>

      <div className="bg-white rounded-lg shadow-sm border border-slate-200 overflow-hidden">
        <div className="px-4 py-2 border-b border-slate-200 text-sm font-medium text-slate-600">
          Scan log
        </div>
        <div className="divide-y divide-slate-100 max-h-96 overflow-y-auto">
          {log.length === 0 && (
            <p className="px-4 py-6 text-sm text-slate-400">Scan a carton barcode to begin.</p>
          )}
          {log.map((entry) => (
            <div key={entry.key} className="flex justify-between items-center px-4 py-2 text-sm">
              <div>
                <p className="font-medium text-slate-800">{entry.batchCode}</p>
                <p className="text-slate-500">
                  {entry.status === "ADDED" && entry.productSku
                    ? `${entry.productSku} - ${entry.itemCount} item(s)`
                    : entry.message}
                </p>
              </div>
              <span
                className={`text-xs px-2 py-1 rounded font-medium ${
                  entry.status === "ADDED"
                    ? "bg-emerald-100 text-emerald-700"
                    : "bg-red-100 text-red-700"
                }`}
              >
                {entry.status === "ADDED" ? "✓ Added" : entry.status.replace(/_/g, " ")}
              </span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
