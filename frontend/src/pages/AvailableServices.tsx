import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useParams } from "react-router-dom";
import { api } from "../api/client";
import { AvailableServicesRefreshResult, ServiceToggleOption } from "../types";
import { useToast } from "../components/ToastContext";
import SavedBadge from "../components/SavedBadge";

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
  const queryClient = useQueryClient();
  const courierKey = courier === "apc" ? "apc" : "dpd";
  const courierLabel = COURIER_LABELS[courierKey];
  const queryKey = ["available-services", courierKey];

  const { data: services, isLoading } = useQuery({
    queryKey,
    queryFn: async () => (await api.get<ServiceToggleOption[]>(`/settings/${courierKey}/available-services`)).data,
  });

  const [enabled, setEnabled] = useState<Record<string, boolean>>({});
  const [search, setSearch] = useState("");
  const [refreshWarnings, setRefreshWarnings] = useState<string[] | null>(null);
  const [saved, setSaved] = useState(false);

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
    onSuccess: (data) => {
      // Keeps the list's own saved enabled-state in sync with what was just
      // persisted, same reason the refresh mutation below writes straight
      // into the query cache rather than just updating local checkbox state.
      queryClient.setQueryData(queryKey, data);
      setSaved(true);
      setTimeout(() => setSaved(false), 2500);
    },
  });

  // Rather than waiting for real orders of every weight/destination to
  // trickle through and gradually fill this list in, this runs a sweep of
  // live lookups against a fixed spread of representative postcodes (BNS's
  // own, Northern Ireland, Scottish Highlands, a Scottish island, the
  // Channel Islands, Isle of Man, Isle of Wight, Isles of Scilly, the
  // Republic of Ireland) at a nominal light weight server-side, so every
  // small-item service tier that region can offer gets pulled in in one go.
  // See DpdShippingService/ApcShippingService.refreshAllKnownServices().
  const refreshMutation = useMutation({
    mutationFn: async () =>
      (await api.post<AvailableServicesRefreshResult>(`/settings/${courierKey}/available-services/refresh`)).data,
    onSuccess: (data) => {
      // Writes the refreshed list straight into the "available-services"
      // query's own cache, not just local checkbox state - the rows on
      // screen are rendered from that query's data (services/
      // filteredServices below), so without this, a code the sweep just
      // added for the first time didn't actually appear until the page was
      // left and re-opened (which re-ran the query from scratch). The
      // useEffect above still handles rebuilding `enabled` off the back of
      // this, since it already reruns on any change to `services`.
      queryClient.setQueryData(queryKey, data.services);
      setRefreshWarnings(data.warnings.length ? data.warnings : null);
      showToast(data.warnings.length ? "Refreshed, with some postcodes failing - see below." : "Refreshed from live lookup.");
    },
  });

  const filteredServices = useMemo(() => {
    if (!services) return services;
    const q = search.trim().toLowerCase();
    if (!q) return services;
    return services.filter((s) => s.label.toLowerCase().includes(q) || s.code.toLowerCase().includes(q));
  }, [services, search]);

  return (
    // pb-24 clears the floating Save button below, same reason Settings.tsx
    // and OrderEdit.tsx use it - it's fixed to the viewport, not the page,
    // and would otherwise sit over the last bit of content on a short page
    // or once scrolled all the way down.
    <div className="max-w-2xl pb-24">
      <button onClick={() => navigate("/settings")} className="text-sm text-slate-500 hover:text-slate-700 mb-3">
        ← Back to Settings
      </button>
      <h2 className="text-2xl font-semibold text-slate-800 mb-2">{courierLabel} Available Services</h2>
      <p className="text-slate-500 mb-4">
        Every {courierLabel} service code seen so far, ticked by default. Untick one to stop it appearing in the
        Service dropdown on the order screen - it isn't deleted, so it can be ticked again later if you need it back.
        {courierKey === "apc" &&
          " A code only shows up here once a live lookup has actually returned it at least once - there's no fixed master list to seed this from."}
      </p>

      <div className="flex items-center gap-3 mb-4">
        <button
          onClick={() => refreshMutation.mutate()}
          disabled={refreshMutation.isPending}
          className="bg-slate-100 text-slate-700 text-sm px-3 py-1.5 rounded hover:bg-slate-200 disabled:opacity-50"
        >
          {refreshMutation.isPending ? "Refreshing..." : "Refresh from live lookup"}
        </button>
        <span className="text-xs text-slate-400">
          Pulls a fresh list from a spread of UK/islands postcodes - mainland, Northern Ireland, the Highlands,
          a Scottish island, the Channel Islands, Isle of Man, Isle of Wight and the Scillies - at a light
          weight, instead of waiting for real orders to add to this one at a time.
        </span>
      </div>
      {refreshWarnings && (
        <div className="mb-4 bg-amber-50 border border-amber-200 rounded-md px-3 py-2 text-xs text-amber-800">
          <p className="font-medium mb-1">Some postcodes in the sweep failed - their services weren't added:</p>
          <ul className="list-disc pl-4 space-y-0.5">
            {refreshWarnings.map((w) => (
              <li key={w}>{w}</li>
            ))}
          </ul>
        </div>
      )}

      {!isLoading && !!services?.length && (
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search services..."
          className="w-full mb-3 border border-slate-300 rounded-md px-3 py-1.5 text-sm"
        />
      )}

      <div className="bg-white rounded-lg shadow-sm border border-slate-200">
        {isLoading && <p className="px-4 py-4 text-sm text-slate-400">Loading...</p>}
        {!isLoading && !services?.length && (
          <p className="px-4 py-4 text-sm text-slate-400">
            No {courierLabel} services have been seen yet - try "Refresh from live lookup" above, or look up a
            service on an order first, then come back here.
          </p>
        )}
        {!isLoading && !!services?.length && !filteredServices?.length && (
          <p className="px-4 py-4 text-sm text-slate-400">No services match "{search}".</p>
        )}
        {!isLoading && !!filteredServices?.length && (
          <ul className="divide-y divide-slate-100">
            {filteredServices.map((s) => (
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

      {saveMutation.isError && <p className="mt-4 text-sm text-red-600">{(saveMutation.error as Error).message}</p>}
      {refreshMutation.isError && <p className="mt-4 text-sm text-red-600">{(refreshMutation.error as Error).message}</p>}

      {/* Floating rather than sitting at the bottom of the page, matching
          Settings.tsx and the order screen's own Save button - see the
          pb-24 comment above. */}
      <div className="fixed bottom-6 right-6 z-50 flex items-center gap-3">
        <SavedBadge show={saved} />
        <button
          onClick={() => saveMutation.mutate()}
          disabled={saveMutation.isPending || isLoading}
          className="bg-emerald-600 text-white text-sm font-medium px-6 py-3 rounded-full shadow-lg hover:bg-emerald-500 disabled:opacity-50"
        >
          {saveMutation.isPending ? "Saving..." : "Save"}
        </button>
      </div>
    </div>
  );
}
