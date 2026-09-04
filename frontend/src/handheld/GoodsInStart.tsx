import { useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { api } from "../api/client";
import { GoodsInSession, Location, PurchaseOrder } from "../types";
import HandheldBinField from "./components/HandheldBinField";

export default function GoodsInStart() {
  const navigate = useNavigate();
  const [purchaseOrderId, setPurchaseOrderId] = useState("");
  const [locationId, setLocationId] = useState("");

  const { data: openSessions } = useQuery({
    queryKey: ["goods-in-open-sessions"],
    queryFn: async () => (await api.get<GoodsInSession[]>("/goods-in/sessions/open")).data,
  });

  const { data: purchaseOrders } = useQuery({
    queryKey: ["purchase-orders"],
    queryFn: async () => (await api.get<PurchaseOrder[]>("/purchase-orders")).data,
  });
  const eligiblePOs = purchaseOrders?.filter((po) => po.status === "AWAITING_STOCK" || po.status === "PART_RECEIVED");

  const { data: locations } = useQuery({
    queryKey: ["locations"],
    queryFn: async () => (await api.get<Location[]>("/locations")).data,
  });

  const startMutation = useMutation({
    mutationFn: async () =>
      (
        await api.post<GoodsInSession>("/goods-in/sessions", {
          purchaseOrderId: Number(purchaseOrderId),
          locationId: Number(locationId),
          startedBy: "handheld",
        })
      ).data,
    onSuccess: (session) => navigate(`/handheld/goods-in/${session.id}`),
  });

  return (
    <div className="min-h-screen bg-slate-950 text-white flex flex-col">
      <header className="px-4 py-4 border-b border-slate-800">
        <button onClick={() => navigate("/handheld")} className="text-xs text-slate-500 mb-1 block">
          ← Handheld Home
        </button>
        <h1 className="text-lg font-semibold">Goods In</h1>
      </header>

      <div className="flex-1 overflow-y-auto p-4 space-y-6">
        {openSessions && openSessions.length > 0 && (
          <div>
            <p className="text-slate-400 text-sm mb-2">Resume a paused session</p>
            <div className="space-y-2">
              {openSessions.map((s) => (
                <button
                  key={s.id}
                  onClick={() => navigate(`/handheld/goods-in/${s.id}`)}
                  className="w-full text-left bg-slate-900 border border-slate-800 rounded-xl p-4 active:bg-slate-800"
                >
                  <p className="font-bold">{s.purchaseOrder.poNumber}</p>
                  <p className="text-slate-400 text-sm">Into {s.location.code}</p>
                </button>
              ))}
            </div>
          </div>
        )}

        <div>
          <p className="text-slate-400 text-sm mb-2">Start a new session</p>
          <div className="bg-slate-900 border border-slate-800 rounded-xl p-4 space-y-3">
            <div>
              <label className="block text-xs text-slate-500 mb-1">Purchase Order</label>
              <select
                value={purchaseOrderId}
                onChange={(e) => setPurchaseOrderId(e.target.value)}
                className="w-full bg-slate-800 rounded-lg px-3 py-3 text-sm"
              >
                <option value="">Select a PO awaiting stock...</option>
                {eligiblePOs?.map((po) => (
                  <option key={po.id} value={po.id}>
                    {po.poNumber} - {po.supplier.name}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-xs text-slate-500 mb-1">Destination Bin</label>
              <HandheldBinField bins={locations ?? []} value={locationId} onChange={setLocationId} />
            </div>
            <button
              disabled={!purchaseOrderId || !locationId || startMutation.isPending}
              onClick={() => startMutation.mutate()}
              className="w-full bg-emerald-600 py-3 rounded-lg font-semibold active:bg-emerald-500 disabled:opacity-40"
            >
              {startMutation.isPending ? "Starting..." : "Start Session"}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
