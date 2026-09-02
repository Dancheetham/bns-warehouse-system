import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useParams } from "react-router-dom";
import { api } from "../api/client";
import { CartonView, DespatchConfirmationResult, PackLineView, PackingView } from "../types";

export default function SplitPacking() {
  const { orderId } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<DespatchConfirmationResult | null>(null);

  const { data: view, isLoading } = useQuery({
    queryKey: ["packing", orderId],
    queryFn: async () => (await api.get<PackingView>(`/despatch/${orderId}/packing`)).data,
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ["packing", orderId] });
  const onError = (err: Error) => setError(err.message);

  const addCartonMutation = useMutation({
    mutationFn: async () => api.post(`/despatch/${orderId}/packing/cartons`),
    onSuccess: invalidate,
  });

  const deleteCartonMutation = useMutation({
    mutationFn: async (cartonId: number) => api.delete(`/despatch/${orderId}/packing/cartons/${cartonId}`),
    onSuccess: invalidate,
    onError,
  });

  const assignMutation = useMutation({
    mutationFn: async ({ cartonLineId, cartonId }: { cartonLineId: number; cartonId: number | null }) =>
      api.post(`/despatch/${orderId}/packing/assign`, { cartonLineId, cartonId }),
    onSuccess: invalidate,
    onError,
  });

  const splitMutation = useMutation({
    mutationFn: async ({ cartonLineId, amount }: { cartonLineId: number; amount: number }) =>
      api.post(`/despatch/${orderId}/packing/split`, { cartonLineId, amount }),
    onSuccess: invalidate,
    onError,
  });

  const splitByQuantityMutation = useMutation({
    mutationFn: async ({ cartonLineId, boxSize }: { cartonLineId: number; boxSize: number }) =>
      api.post(`/despatch/${orderId}/packing/split-by-quantity`, { cartonLineId, boxSize }),
    onSuccess: invalidate,
    onError,
  });

  const weightMutation = useMutation({
    mutationFn: async ({ cartonId, weightKg }: { cartonId: number; weightKg: number | null }) =>
      api.put(`/despatch/${orderId}/packing/cartons/${cartonId}/weight`, { weightKg }),
    onSuccess: invalidate,
  });

  const confirmAndLabel = async () => {
    setError(null);
    setConfirming(true);
    try {
      const confirmResponse = await api.post<DespatchConfirmationResult>(`/despatch/${orderId}/confirm`);
      setResult(confirmResponse.data);
      const pdfResponse = await api.get(`/despatch/${orderId}/labels`, { responseType: "blob" });
      const blobUrl = window.URL.createObjectURL(pdfResponse.data);
      window.open(blobUrl, "_blank");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Something went wrong confirming despatch");
    } finally {
      setConfirming(false);
    }
  };

  if (isLoading || !view) {
    return <p className="text-slate-500">Loading...</p>;
  }

  if (result) {
    return (
      <div className="max-w-2xl">
        <h2 className="text-2xl font-semibold text-slate-800 mb-1">Despatch Confirmed</h2>
        <p className="text-slate-500 mb-6">
          {result.order.orderNumber} · Labels opened in a new tab.
        </p>

        <div
          className={`rounded-lg p-4 mb-4 text-sm border ${
            result.despatchEmail.emailSent
              ? "bg-emerald-50 border-emerald-200 text-emerald-700"
              : "bg-amber-50 border-amber-200 text-amber-800"
          }`}
        >
          <p className="font-medium mb-1">
            Despatch email: {result.despatchEmail.emailSent ? "Sent" : "Not sent"} - {result.despatchEmail.reason}
          </p>
          {result.despatchEmail.toAddress && <p>To: {result.despatchEmail.toAddress}</p>}
          <details className="mt-2">
            <summary className="cursor-pointer text-xs">View composed email</summary>
            <p className="mt-2 font-medium">{result.despatchEmail.subject}</p>
            <pre className="whitespace-pre-wrap text-xs mt-1 font-sans">{result.despatchEmail.body}</pre>
          </details>
        </div>

        <div className="rounded-lg p-4 mb-6 text-sm border bg-slate-50 border-slate-200 text-slate-600">
          <span className="font-medium">Shopify: </span>
          {result.shopifyFulfillmentStatus}
        </div>

        <button
          onClick={() => navigate("/despatch")}
          className="bg-slate-800 text-white px-5 py-2.5 rounded-md hover:bg-slate-700 font-medium"
        >
          Back to Despatch
        </button>
      </div>
    );
  }

  return (
    <div>
      <button onClick={() => navigate("/despatch")} className="text-sm text-slate-500 hover:text-slate-700 mb-3">
        ← Back to Despatch
      </button>
      <h2 className="text-2xl font-semibold text-slate-800 mb-1">
        Pack {view.orderNumber} <span className="text-slate-400 font-normal">· {view.customerName}</span>
      </h2>
      <p className="text-slate-500 mb-6">
        Pack a line's full quantity straight into a carton, or split it first - by an amount (e.g. 32 required, split
        30 off leaves a line of 30 and a line of 2) or by quantity (e.g. split by 8 turns 32 into four lines of 8).
        Anything left unassigned is swept into a final catch-all carton automatically when you confirm.
      </p>

      {error && <div className="bg-red-50 text-red-700 text-sm rounded-lg px-4 py-2 mb-4">{error}</div>}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <div className="bg-white border border-slate-200 rounded-lg p-4">
          <div className="flex items-center justify-between mb-3">
            <h3 className="font-medium text-slate-700">Unassigned</h3>
            <span className="text-xs text-slate-400">{view.unassignedLines.length} line(s)</span>
          </div>
          <div className="space-y-3 max-h-[600px] overflow-y-auto">
            {view.unassignedLines.length === 0 && (
              <p className="text-sm text-slate-400">Everything's assigned to a carton.</p>
            )}
            {view.unassignedLines.map((line) => (
              <UnassignedLineRow
                key={line.cartonLineId}
                line={line}
                cartons={view.cartons}
                onAssign={(cartonId) => assignMutation.mutate({ cartonLineId: line.cartonLineId, cartonId })}
                onSplit={(amount) => splitMutation.mutate({ cartonLineId: line.cartonLineId, amount })}
                onSplitByQuantity={(boxSize) =>
                  splitByQuantityMutation.mutate({ cartonLineId: line.cartonLineId, boxSize })
                }
              />
            ))}
          </div>
        </div>

        <div className="lg:col-span-2 space-y-4">
          {view.cartons.map((carton) => (
            <CartonCard
              key={carton.cartonId}
              carton={carton}
              onRemoveLine={(cartonLineId) => assignMutation.mutate({ cartonLineId, cartonId: null })}
              onDelete={() => deleteCartonMutation.mutate(carton.cartonId)}
              onWeightChange={(weightKg) => weightMutation.mutate({ cartonId: carton.cartonId, weightKg })}
            />
          ))}

          <button
            onClick={() => addCartonMutation.mutate()}
            className="w-full border-2 border-dashed border-slate-300 text-slate-500 rounded-lg py-4 hover:border-slate-400 hover:text-slate-600"
          >
            + Add Carton
          </button>
        </div>
      </div>

      <button
        onClick={confirmAndLabel}
        disabled={confirming}
        className="mt-6 bg-emerald-600 text-white px-5 py-2.5 rounded-md hover:bg-emerald-500 disabled:opacity-50 font-medium"
      >
        {confirming ? "Confirming..." : "Confirm Despatch & Print Labels"}
      </button>
    </div>
  );
}

function UnassignedLineRow({
  line,
  cartons,
  onAssign,
  onSplit,
  onSplitByQuantity,
}: {
  line: PackLineView;
  cartons: CartonView[];
  onAssign: (cartonId: number) => void;
  onSplit: (amount: number) => void;
  onSplitByQuantity: (boxSize: number) => void;
}) {
  const [mode, setMode] = useState<"none" | "split" | "split-qty">("none");
  const [value, setValue] = useState("");

  const submit = () => {
    const n = Number(value);
    if (!n || n <= 0) return;
    if (mode === "split") onSplit(n);
    if (mode === "split-qty") onSplitByQuantity(n);
    setMode("none");
    setValue("");
  };

  return (
    <div className="bg-slate-50 rounded px-3 py-2 text-sm">
      <div className="flex items-center justify-between mb-2">
        <div>
          <p className="font-medium">{line.sku}</p>
          <p className="text-slate-500 text-xs">{line.productName}</p>
        </div>
        <span className="font-bold text-lg text-slate-700">{line.quantity}</span>
      </div>

      {mode === "none" ? (
        <div className="flex flex-wrap gap-2">
          <select
            defaultValue=""
            onChange={(e) => {
              if (e.target.value) onAssign(Number(e.target.value));
            }}
            className="border border-slate-300 rounded px-2 py-1 text-xs"
          >
            <option value="" disabled>
              Pack full qty into...
            </option>
            {cartons.map((c) => (
              <option key={c.cartonId} value={c.cartonId}>
                Carton {c.cartonNumber}
              </option>
            ))}
          </select>
          <button
            onClick={() => setMode("split")}
            disabled={line.quantity <= 1}
            className="text-xs border border-slate-300 rounded px-2 py-1 hover:bg-slate-100 disabled:opacity-40"
          >
            Split
          </button>
          <button
            onClick={() => setMode("split-qty")}
            disabled={line.quantity <= 1}
            className="text-xs border border-slate-300 rounded px-2 py-1 hover:bg-slate-100 disabled:opacity-40"
          >
            Split by Qty
          </button>
        </div>
      ) : (
        <div className="flex items-center gap-2">
          <input
            autoFocus
            type="number"
            value={value}
            onChange={(e) => setValue(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && submit()}
            placeholder={mode === "split" ? `Split off, e.g. ${Math.min(line.quantity - 1, 1)}` : "Box size, e.g. 8"}
            className="flex-1 border border-slate-300 rounded px-2 py-1 text-xs"
          />
          <button onClick={submit} className="text-xs bg-slate-800 text-white rounded px-2 py-1">
            Go
          </button>
          <button onClick={() => setMode("none")} className="text-xs text-slate-400">
            Cancel
          </button>
        </div>
      )}
    </div>
  );
}

function CartonCard({
  carton,
  onRemoveLine,
  onDelete,
  onWeightChange,
}: {
  carton: CartonView;
  onRemoveLine: (cartonLineId: number) => void;
  onDelete: () => void;
  onWeightChange: (weightKg: number | null) => void;
}) {
  const [weight, setWeight] = useState(carton.weightKg != null ? String(carton.weightKg) : "");

  return (
    <div className="bg-white border border-slate-200 rounded-lg p-4">
      <div className="flex items-center justify-between mb-3">
        <h3 className="font-medium text-slate-700">Carton {carton.cartonNumber}</h3>
        <div className="flex items-center gap-2">
          <input
            value={weight}
            onChange={(e) => setWeight(e.target.value)}
            onBlur={() => onWeightChange(weight ? Number(weight) : null)}
            placeholder={carton.computedWeightKg != null ? `~${carton.computedWeightKg} kg` : "Weight (kg)"}
            className="w-24 border border-slate-300 rounded px-2 py-1 text-xs text-right"
          />
          <button
            onClick={onDelete}
            disabled={carton.lines.length > 0}
            className="text-xs text-red-500 hover:text-red-700 disabled:opacity-30 disabled:cursor-not-allowed"
            title={carton.lines.length > 0 ? "Remove lines before deleting" : "Delete empty carton"}
          >
            Delete
          </button>
        </div>
      </div>
      {carton.lines.length === 0 ? (
        <p className="text-sm text-slate-400">No lines assigned yet.</p>
      ) : (
        <div className="space-y-2">
          {carton.lines.map((line) => (
            <div key={line.cartonLineId} className="flex items-center justify-between bg-slate-50 rounded px-3 py-2 text-sm">
              <div>
                <p className="font-medium">{line.sku}</p>
                <p className="text-slate-500 text-xs">{line.productName}</p>
              </div>
              <div className="flex items-center gap-3">
                <span className="font-bold">{line.quantity}</span>
                <button onClick={() => onRemoveLine(line.cartonLineId)} className="text-xs text-slate-400 hover:text-slate-600">
                  Remove
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
