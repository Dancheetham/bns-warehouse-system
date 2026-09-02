import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useNavigate, useParams } from "react-router-dom";
import { api } from "../api/client";
import { GoodsInSession, ScanCartonResult } from "../types";

interface ScanLogEntry extends ScanCartonResult {
  batchCode: string;
  key: string;
}

export default function GoodsInScan() {
  const { sessionId } = useParams();
  const navigate = useNavigate();
  const inputRef = useRef<HTMLInputElement>(null);
  const [scanValue, setScanValue] = useState("");
  const [log, setLog] = useState<ScanLogEntry[]>([]);

  const { data: session } = useQuery({
    queryKey: ["goods-in-session", sessionId],
    queryFn: async () => (await api.get<GoodsInSession>(`/goods-in/sessions/${sessionId}`)).data,
  });

  useEffect(() => {
    inputRef.current?.focus();
  }, [session]);

  const scanMutation = useMutation({
    mutationFn: async (batchCode: string) =>
      (
        await api.post<ScanCartonResult>(`/goods-in/sessions/${sessionId}/scan`, {
          batchCode,
          scannedBy: "handheld",
        })
      ).data,
    onSuccess: (data, batchCode) => {
      // Mirrors the desktop flow: a duplicate scan within the same session never
      // interrupts the operator - it's just not logged.
      if (data.status !== "ALREADY_IN_SESSION") {
        setLog((prev) => [{ ...data, batchCode, key: `${batchCode}-${Date.now()}` }, ...prev]);
      }
      setScanValue("");
      inputRef.current?.focus();
    },
    onError: () => {
      setScanValue("");
      inputRef.current?.focus();
    },
  });

  const saveMutation = useMutation({
    mutationFn: async () => api.post(`/goods-in/sessions/${sessionId}/save`, { savedBy: "handheld" }),
    onSuccess: () => navigate("/handheld/goods-in", { replace: true }),
  });

  const addedCount = log.filter((l) => l.status === "ADDED").length;

  if (!session) {
    return <div className="min-h-screen bg-slate-950 text-white flex items-center justify-center">Loading...</div>;
  }

  return (
    <div className="min-h-screen bg-slate-950 text-white flex flex-col">
      <header className="px-4 py-3 flex items-center justify-between border-b border-slate-800">
        <button onClick={() => navigate("/handheld/goods-in")} className="text-slate-400 text-sm">
          ← Sessions
        </button>
        <span className="text-lg font-bold">{session.purchaseOrder.poNumber}</span>
        <span className="text-xs text-slate-500">→ {session.location.code}</span>
      </header>

      <div className="flex-1 flex flex-col p-4">
        <div className="text-center mb-4">
          <p className="text-4xl font-extrabold text-emerald-400">{addedCount}</p>
          <p className="text-slate-500 text-sm">cartons scanned</p>
        </div>

        <form
          onSubmit={(e) => {
            e.preventDefault();
            if (scanValue.trim()) scanMutation.mutate(scanValue.trim());
          }}
        >
          <input
            ref={inputRef}
            autoFocus
            value={scanValue}
            onChange={(e) => setScanValue(e.target.value)}
            placeholder="Scan carton barcode"
            className="w-full text-center text-lg bg-slate-800 rounded-lg px-4 py-4 mb-4"
          />
        </form>

        <div className="flex gap-2 mb-4">
          <button
            onClick={() => saveMutation.mutate()}
            disabled={addedCount === 0 || saveMutation.isPending}
            className="flex-1 bg-emerald-600 py-3 rounded-lg font-semibold active:bg-emerald-500 disabled:opacity-40"
          >
            {saveMutation.isPending ? "Saving..." : "Save & Complete"}
          </button>
          <button
            onClick={() => navigate("/handheld/goods-in")}
            className="flex-1 bg-slate-800 py-3 rounded-lg text-sm"
          >
            Pause
          </button>
        </div>

        <div className="flex-1 overflow-y-auto space-y-2">
          {log.length === 0 && <p className="text-slate-500 text-sm text-center py-6">Scan a carton to begin.</p>}
          {log.map((entry) => (
            <div key={entry.key} className="flex items-center justify-between bg-slate-900 border border-slate-800 rounded-lg px-3 py-2">
              <div>
                <p className="font-medium text-sm">{entry.batchCode}</p>
                <p className="text-slate-500 text-xs">
                  {entry.status === "ADDED" && entry.productSku
                    ? `${entry.productSku} · ${entry.itemCount} item(s)`
                    : entry.message}
                </p>
              </div>
              <span
                className={`text-xs px-2 py-1 rounded-full font-medium ${
                  entry.status === "ADDED" ? "bg-emerald-900 text-emerald-300" : "bg-red-900 text-red-300"
                }`}
              >
                {entry.status === "ADDED" ? "Added" : entry.status.replace(/_/g, " ")}
              </span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
