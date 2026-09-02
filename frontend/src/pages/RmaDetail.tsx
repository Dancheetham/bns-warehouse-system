import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useParams } from "react-router-dom";
import { api } from "../api/client";
import { RmaDetailView } from "../types";

export default function RmaDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);

  const { data: rma, isLoading } = useQuery({
    queryKey: ["rma", id],
    queryFn: async () => (await api.get<RmaDetailView>(`/rma/${id}`)).data,
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ["rma", id] });
  const onError = (err: Error) => setError(err.message);

  const [approvedBy, setApprovedBy] = useState("");
  const [deliveryName, setDeliveryName] = useState("");
  const [deliveryTown, setDeliveryTown] = useState("");
  const [deliveryCountry, setDeliveryCountry] = useState("");
  const [deliveryPostcode, setDeliveryPostcode] = useState("");
  const [deliveryCountryCode, setDeliveryCountryCode] = useState("");

  const approveMutation = useMutation({
    mutationFn: async () =>
      api.post(`/rma/${id}/approve`, {
        approvedBy: approvedBy || undefined,
        deliveryName: deliveryName || undefined,
        deliveryTown: deliveryTown || undefined,
        deliveryCountry: deliveryCountry || undefined,
        deliveryPostcode: deliveryPostcode || undefined,
        deliveryCountryCode: deliveryCountryCode || undefined,
      }),
    onSuccess: invalidate,
    onError,
  });

  const [rejectReason, setRejectReason] = useState("");
  const [rejectedBy, setRejectedBy] = useState("");
  const rejectMutation = useMutation({
    mutationFn: async () => api.post(`/rma/${id}/reject`, { rejectedBy: rejectedBy || undefined, reason: rejectReason }),
    onSuccess: invalidate,
    onError,
  });

  const [receivedBy, setReceivedBy] = useState("");
  const [receiveState, setReceiveState] = useState<Record<number, { received: boolean; rsfApplied: boolean }>>({});
  const receiveMutation = useMutation({
    mutationFn: async () =>
      api.post(`/rma/${id}/receive`, {
        receivedBy: receivedBy || undefined,
        items: Object.entries(receiveState).map(([itemId, s]) => ({
          rmaItemId: Number(itemId),
          received: s.received,
          rsfApplied: s.rsfApplied,
          grandstreamWarrantyChecked: true,
        })),
      }),
    onSuccess: invalidate,
    onError,
  });

  const printCoverSheet = async () => {
    const res = await api.get(`/rma/${id}/cover-sheet`, { responseType: "blob" });
    window.open(window.URL.createObjectURL(res.data), "_blank");
  };

  if (isLoading || !rma) return <p className="text-slate-500">Loading...</p>;

  const anyFaulty = rma.items.some((i) => i.faulty);

  return (
    <div className="max-w-4xl">
      <button onClick={() => navigate("/rmas")} className="text-sm text-slate-500 hover:text-slate-700 mb-3">
        ← Back to RMAs
      </button>

      <div className="flex items-center justify-between mb-1">
        <h2 className="text-2xl font-semibold text-slate-800">
          {rma.rmaNumber ?? rma.publicReference} <span className="text-slate-400 font-normal">· {rma.status}</span>
        </h2>
        {rma.rmaNumber && (
          <button onClick={printCoverSheet} className="text-sm text-slate-600 border border-slate-300 rounded px-3 py-1.5 hover:bg-slate-50">
            Print Cover Sheet
          </button>
        )}
      </div>
      <p className="text-slate-500 mb-6">{rma.customerName}</p>

      {error && <div className="bg-red-50 text-red-700 text-sm rounded-lg px-4 py-2 mb-4">{error}</div>}

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-6">
        <div className="bg-white border border-slate-200 rounded-lg p-4 text-sm">
          <h3 className="font-medium text-slate-700 mb-2">Customer</h3>
          <p>{rma.customerCompany}</p>
          <p className="whitespace-pre-line text-slate-500">{rma.customerAddress}</p>
          <p className="mt-2">{rma.contactName}</p>
          <p className="text-slate-500">{rma.contactPhone}</p>
          <p className="text-slate-500">{rma.contactEmail}</p>
        </div>
        <div className="bg-white border border-slate-200 rounded-lg p-4 text-sm space-y-1">
          <h3 className="font-medium text-slate-700 mb-2">Linked Orders</h3>
          <p>Original: {rma.originalOrderNumber ?? "—"}</p>
          <p>Replacement: {rma.replacementOrderNumber ?? "—"}</p>
          <p>Credit: {rma.creditOrderNumber ?? "—"}</p>
          {anyFaulty && (
            <>
              <h3 className="font-medium text-slate-700 mt-3 mb-1">Replacement Delivery</h3>
              <p>{rma.deliveryName}</p>
              <p className="text-slate-500">
                {rma.deliveryTown}, {rma.deliveryPostcode}, {rma.deliveryCountry}
              </p>
            </>
          )}
        </div>
      </div>

      <div className="bg-white border border-slate-200 rounded-lg overflow-hidden mb-6">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-slate-500 text-left">
            <tr>
              <th className="px-3 py-2">Product</th>
              <th className="px-3 py-2">Identifier</th>
              <th className="px-3 py-2">Qty</th>
              <th className="px-3 py-2">Faulty</th>
              <th className="px-3 py-2">Matched Order</th>
              <th className="px-3 py-2">Return Window</th>
              <th className="px-3 py-2">GS Ticket</th>
              {rma.status === "APPROVED" && <th className="px-3 py-2">Receive</th>}
            </tr>
          </thead>
          <tbody>
            {rma.items.map((item) => (
              <tr key={item.id} className="border-t border-slate-100">
                <td className="px-3 py-2">
                  <p className="font-medium">{item.sku}</p>
                  <p className="text-slate-500 text-xs">{item.productName}</p>
                  {item.reasonForReturn && <p className="text-slate-400 text-xs mt-1">{item.reasonForReturn}</p>}
                </td>
                <td className="px-3 py-2 font-mono text-xs">{item.identifier ?? "—"}</td>
                <td className="px-3 py-2">{item.quantity}</td>
                <td className="px-3 py-2">{item.faulty ? "Yes" : "No"}</td>
                <td className="px-3 py-2">
                  {item.matchedOrderNumber ? (
                    item.matchedOrderNumber
                  ) : (
                    <span className="text-red-600 text-xs">Not matched - verify manually</span>
                  )}
                </td>
                <td className="px-3 py-2">
                  {item.returnWindowExpiresAt ? (
                    <span className={item.returnWindowValid ? "text-emerald-600" : "text-red-600"}>
                      {item.returnWindowValid ? "Valid" : "Expired"} ({item.returnWindowExpiresAt})
                    </span>
                  ) : (
                    "—"
                  )}
                </td>
                <td className="px-3 py-2 text-xs">{item.grandstreamTicketNumber ?? "—"}</td>
                {rma.status === "APPROVED" && (
                  <td className="px-3 py-2">
                    <label className="flex items-center gap-1 text-xs mb-1">
                      <input
                        type="checkbox"
                        checked={receiveState[item.id]?.received ?? false}
                        onChange={(e) =>
                          setReceiveState((prev) => ({
                            ...prev,
                            [item.id]: { received: e.target.checked, rsfApplied: prev[item.id]?.rsfApplied ?? false },
                          }))
                        }
                      />
                      Received
                    </label>
                    {!item.faulty && (
                      <label className="flex items-center gap-1 text-xs">
                        <input
                          type="checkbox"
                          checked={receiveState[item.id]?.rsfApplied ?? false}
                          onChange={(e) =>
                            setReceiveState((prev) => ({
                              ...prev,
                              [item.id]: { received: prev[item.id]?.received ?? false, rsfApplied: e.target.checked },
                            }))
                          }
                        />
                        Apply 15% RSF
                      </label>
                    )}
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {rma.status === "SUBMITTED" && (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div className="bg-white border border-slate-200 rounded-lg p-4">
            <h3 className="font-medium text-slate-700 mb-3">Approve</h3>
            {anyFaulty && (
              <div className="grid grid-cols-2 gap-2 mb-3">
                <input placeholder="Delivery name" value={deliveryName} onChange={(e) => setDeliveryName(e.target.value)} className="input col-span-2" />
                <input placeholder="Town" value={deliveryTown} onChange={(e) => setDeliveryTown(e.target.value)} className="input" />
                <input placeholder="Postcode" value={deliveryPostcode} onChange={(e) => setDeliveryPostcode(e.target.value)} className="input" />
                <input placeholder="Country" value={deliveryCountry} onChange={(e) => setDeliveryCountry(e.target.value)} className="input" />
                <input placeholder="Country code" value={deliveryCountryCode} onChange={(e) => setDeliveryCountryCode(e.target.value)} className="input" />
              </div>
            )}
            <input placeholder="Approved by" value={approvedBy} onChange={(e) => setApprovedBy(e.target.value)} className="input mb-3" />
            <button
              onClick={() => approveMutation.mutate()}
              disabled={approveMutation.isPending}
              className="w-full bg-emerald-600 text-white py-2 rounded-md text-sm hover:bg-emerald-500 disabled:opacity-50"
            >
              Approve RMA
            </button>
          </div>
          <div className="bg-white border border-slate-200 rounded-lg p-4">
            <h3 className="font-medium text-slate-700 mb-3">Reject</h3>
            <input placeholder="Rejected by" value={rejectedBy} onChange={(e) => setRejectedBy(e.target.value)} className="input mb-2" />
            <textarea placeholder="Reason" value={rejectReason} onChange={(e) => setRejectReason(e.target.value)} rows={3} className="input mb-3" />
            <button
              onClick={() => rejectMutation.mutate()}
              disabled={rejectMutation.isPending}
              className="w-full bg-red-600 text-white py-2 rounded-md text-sm hover:bg-red-500 disabled:opacity-50"
            >
              Reject RMA
            </button>
          </div>
        </div>
      )}

      {rma.status === "APPROVED" && (
        <div className="bg-white border border-slate-200 rounded-lg p-4">
          <h3 className="font-medium text-slate-700 mb-3">Record Return &amp; Receipt</h3>
          <input placeholder="Received by" value={receivedBy} onChange={(e) => setReceivedBy(e.target.value)} className="input mb-3 max-w-xs" />
          <button
            onClick={() => receiveMutation.mutate()}
            disabled={receiveMutation.isPending}
            className="bg-emerald-600 text-white px-4 py-2 rounded-md text-sm hover:bg-emerald-500 disabled:opacity-50"
          >
            Process Receipt &amp; Credit
          </button>
        </div>
      )}
    </div>
  );
}
