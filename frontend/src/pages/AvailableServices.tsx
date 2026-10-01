import { useEffect, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useNavigate, useParams } from "react-router-dom";
import { api } from "../api/client";
import { ServiceToggleOption } from "../types";
import { useToast } from "../components/ToastContext";

// Settings > Couriers > DPD/APC > "Available services" - lets Dan permanently
// hide specific service codes (e.g. APC's Liquid product codes, which BNS
// never uses) from the order screen's Service dropdown, without losing the
// ability to turn them back on later. The list itself is whatever codes that
// courier has actually offered so far (live or cached - see
// DpdShippingService/ApcShippingService.listAllKnownServicesForToggle()) -
// there's no complete master catalog of every possible courier service code
// anywhere in this app, so a brand new code only appears here once a live
// lookup has returned it at least once (already ticked, since it's enabled
// by default).
const COURIER_LABELS: Record<string, string> = { dpd: "DPD", apc: "APC" };

export default function AvailableServices() {
  const { courier } = useParams<{ courier: string }>();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const courierKey = courier === "apc" ? "apc" : "dpd";
  const courierLabel = COURIER_LABELS[courierKey];

  const { data: services, isLoading } = useQuery({
    queryKey: ["available-services", courierKey],
    queryFn: async () => (await api.get<ServiceToggleOption[]>(`/settings/${courierKey}/available-services`)).data,
  });

  const [enabled, setEnabled] = useState<Record<string, boolean>>({});

  useEffect(() => {
    if (!services) return;
    setEnabled(Object.fromEntries(services.map((s) => [s.code, s.enabled])));
  }, [services]);

  const saveMutation = useMutation({
    mutationFn: async () => {
      const disabledCodes = Object.entries(enabled)
        .filter(([, isEnabled]) => !isEnabled)
        .map(([code]) => code);
      return (await api.put<ServiceToggleOption[]>(`/settings/${courierKey}/available-services`, disabledCodes)).data;
    },
    onSuccess: () => showToast("Saved."),
  });

  return (
    <div className="max-w-2xl">
      <button onClick={() => navigate("/settings")} className="text-sm text-slate-500 hover:text-slate-700 mb-3">
        ← Back to Settings
      </button>
      <h2 className="text-2xl font-semibold text-slate-800 mb-2">{courierLabel} Available Services</h2>
      <p className="text-slate-500 mb-6">
        Every {courierLabel} service code seen so far, ticked by default. Untick one to stop it appearing in the
        Service dropdown on the order screen - it isn't deleted, so it can be ticked again later if you need it back.
        {courierKey === "apc" &&
          " A code only shows up here once a live lookup has actually returned it at least once - there's no fixed master list to seed this from."}
      </p>

      <div className="bg-white rounded-lg shadow-sm border border-slate-200">
        {isLoading && <p className="px-4 py-4 text-sm text-slate-400">Loading...</p>}
        {!isLoading && !services?.length && (
          <p className="px-4 py-4 text-sm text-slate-400">
            No {courierLabel} services have been seen yet - look up a service on an order first, then come back here.
          </p>
        )}
        {!isLoading && !!services?.length && (
          <ul className="divide-y divide-slate-100">
            {services.map((s) => (
              <li key={s.code} className="flex items-center gap-3 px-4 py-2.5">
                <input
                  type="checkbox"
                  checked={enabled[s.code] ?? true}
                  onChange={(e) => setEnabled((prev) => ({ ...prev, [s.code]: e.target.checked }))}
                />
                <span className="text-sm text-slate-700">{s.label}</span>
                <span className="text-xs text-slate-400 ml-auto">{s.code}</span>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="mt-4 flex items-center gap-3">
        <button
          onClick={() => saveMutation.mutate()}
          disabled={saveMutation.isPending || isLoading}
          className="bg-emerald-600 text-white text-sm px-4 py-2 rounded-md hover:bg-emerald-500 disabled:opacity-50"
        >
          {saveMutation.isPending ? "Saving..." : "Save"}
        </button>
        {saveMutation.isError && <p className="text-sm text-red-600">{(saveMutation.error as Error).message}</p>}
      </div>
    </div>
  );
}
