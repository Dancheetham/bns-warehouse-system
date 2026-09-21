import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useParams } from "react-router-dom";
import { api } from "../api/client";
import { DespatchConfirmationResult, PackedItemView, SerialCartonView, SerialPackingView } from "../types";
import { printPdf } from "../utils/printAgent";

export default function SerialPacking() {
  const { orderId } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<DespatchConfirmationResult | null>(null);
  const [printStatus, setPrintStatus] = useState<string | null>(null);

  const { data: view, isLoading } = useQuery({
    queryKey: ["serial-packing", orderId],
    queryFn: async () => (await api.get<SerialPackingView>(`/despatch/${orderId}/serial-packing`)).data,
  });

  const { data: settings } = useQuery({
    queryKey: ["settings"],
    queryFn: async () => (await api.get<Record<string, string>>("/settings")).data,
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ["serial-packing", orderId] });

  const addCartonMutation = useMutation({
    mutationFn: async () => api.post(`/despatch/${orderId}/serial-packing/cartons`),
    onSuccess: invalidate,
  });

  const deleteCartonMutation = useMutation({
    mutationFn: async (cartonId: number) => api.delete(`/despatch/${orderId}/serial-packing/cartons/${cartonId}`),
    onSuccess: invalidate,
    onError: (err: Error) => setError(err.message),
  });

  const assignMutation = useMutation({
    mutationFn: async ({ stockItemId, cartonId }: { stockItemId: number; cartonId: number | null }) =>
      api.post(`/despatch/${orderId}/serial-packing/assign`, { stockItemId, cartonId }),
    onSuccess: invalidate,
    onError: (err: Error) => setError(err.message),
  });

  const weightMutation = useMutation({
    mutationFn: async ({ cartonId, weightKg }: { cartonId: number; weightKg: number | null }) =>
      api.put(`/despatch/${orderId}/serial-packing/cartons/${cartonId}/weight`, { weightKg }),
    onSuccess: invalidate,
  });

  const confirmAndLabel = async () => {
    setError(null);
    setConfirming(true);
    // Two separate try/catches on purpose - a despatch that fails to confirm
    // is a real problem (stays on this screen with the error shown), but a
    // label that can't be fetched afterwards (DPD not booked and sample
    // labels turned off, say) is not - the despatch has already gone through
    // by that point, so that's reported on the confirmed screen instead via
    // printStatus, not as a top-level error.
    let confirmed: DespatchConfirmationResult;
    try {
      confirmed = (await api.post<DespatchConfirmationResult>(`/despatch/${orderId}/confirm`)).data;
      setResult(confirmed);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Something went wrong confirming despatch");
      setConfirming(false);
      return;
    }

    try {
      const labelResponse = await api.get(`/despatch/${orderId}/labels`, { responseType: "blob" });
      const contentType = labelResponse.headers?.["content-type"] || labelResponse.data.type || "";
      if (contentType.includes("html")) {
        // A real DPD label - HTML, not a PDF the print agent can send to a
        // printer, so it's opened directly for the operator to print from
        // the browser (Ctrl+P) instead.
        const blobUrl = window.URL.createObjectURL(labelResponse.data);
        window.open(blobUrl, "_blank");
        setPrintStatus("DPD label opened in a new tab - print from there.");
      } else {
        const agentUrl = settings?.["print_agent_url"] || "http://localhost:9191/print";
        const printerName = settings?.["label_printer"] || "";
        const printResult = await printPdf(labelResponse.data, agentUrl, printerName);
        setPrintStatus(
          printResult.printed ? "Labels sent to printer." : "Print agent not reachable - labels opened in a new tab instead."
        );
      }
    } catch (e) {
      setPrintStatus(e instanceof Error ? e.message : "No label could be printed for this order.");
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
          {result.order.orderNumber} · {printStatus ?? "Labels opened in a new tab."}
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

        <div className="rounded-lg p-4 mb-4 text-sm border bg-slate-50 border-slate-200 text-slate-600">
          <span className="font-medium">Shopify: </span>
          {result.shopifyFulfillmentStatus}
        </div>

        {result.dpdStatus && (
          <div
            className={`rounded-lg p-4 mb-6 text-sm border ${
              result.dpdStatus.startsWith("DPD shipment NOT booked")
                ? "bg-red-50 border-red-200 text-red-700"
                : "bg-emerald-50 border-emerald-200 text-emerald-700"
            }`}
          >
            {result.dpdStatus}
          </div>
        )}

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
        Serial Packing - assign each individually picked unit into a carton. Anything left unassigned is swept into
        a final catch-all carton automatically when you confirm.
      </p>

      {error && <div className="bg-red-50 text-red-700 text-sm rounded-lg px-4 py-2 mb-4">{error}</div>}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <div className="bg-white border border-slate-200 rounded-lg p-4">
          <div className="flex items-center justify-between mb-3">
            <h3 className="font-medium text-slate-700">Unassigned</h3>
            <span className="text-xs text-slate-400">{view.unassignedItems.length} unit(s)</span>
          </div>
          <div className="space-y-2 max-h-[500px] overflow-y-auto">
            {view.unassignedItems.length === 0 && (
              <p className="text-sm text-slate-400">Everything's assigned to a carton.</p>
            )}
            {view.unassignedItems.map((item) => (
              <UnassignedItemRow
                key={item.stockItemId}
                item={item}
                cartons={view.cartons}
                onAssign={(cartonId) => assignMutation.mutate({ stockItemId: item.stockItemId, cartonId })}
              />
            ))}
          </div>
        </div>

        <div className="lg:col-span-2 space-y-4">
          {view.cartons.map((carton) => (
            <CartonCard
              key={carton.cartonId}
              carton={carton}
              onRemoveItem={(stockItemId) => assignMutation.mutate({ stockItemId, cartonId: null })}
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

function UnassignedItemRow({
  item,
  cartons,
  onAssign,
}: {
  item: PackedItemView;
  cartons: SerialCartonView[];
  onAssign: (cartonId: number) => void;
}) {
  return (
    <div className="flex items-center justify-between bg-slate-50 rounded px-3 py-2 text-sm">
      <div>
        <p className="font-medium">{item.sku}</p>
        <p className="text-slate-500 text-xs">{item.identifier}</p>
      </div>
      <select
        defaultValue=""
        onChange={(e) => {
          if (e.target.value) onAssign(Number(e.target.value));
        }}
        className="border border-slate-300 rounded px-2 py-1 text-xs"
      >
        <option value="" disabled>
          Add to...
        </option>
        {cartons.map((c) => (
          <option key={c.cartonId} value={c.cartonId}>
            Carton {c.cartonNumber}
          </option>
        ))}
      </select>
    </div>
  );
}

function CartonCard({
  carton,
  onRemoveItem,
  onDelete,
  onWeightChange,
}: {
  carton: SerialCartonView;
  onRemoveItem: (stockItemId: number) => void;
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
            disabled={carton.items.length > 0}
            className="text-xs text-red-500 hover:text-red-700 disabled:opacity-30 disabled:cursor-not-allowed"
            title={carton.items.length > 0 ? "Remove items before deleting" : "Delete empty carton"}
          >
            Delete
          </button>
        </div>
      </div>
      {carton.items.length === 0 ? (
        <p className="text-sm text-slate-400">No items assigned yet.</p>
      ) : (
        <div className="space-y-2">
          {carton.items.map((item) => (
            <div key={item.stockItemId} className="flex items-center justify-between bg-slate-50 rounded px-3 py-2 text-sm">
              <div>
                <p className="font-medium">{item.sku}</p>
                <p className="text-slate-500 text-xs">{item.identifier}</p>
              </div>
              <button onClick={() => onRemoveItem(item.stockItemId)} className="text-xs text-slate-400 hover:text-slate-600">
                Remove
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
