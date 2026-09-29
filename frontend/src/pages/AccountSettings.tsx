import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../api/client";
import { DEFAULT_STATUS_COLORS, ORDER_STATUSES, resolveStatusColors, statusColorSettingKey, statusLabel } from "../utils/statusColors";
import { OrderStatus } from "../types";
import SettingsSection from "../components/SettingsSection";
import SavedBadge from "../components/SavedBadge";

/**
 * Just-for-you settings, split out of the main Settings page in v0.107 -
 * previously "My Email" and "Customisation" lived inside Admin > Settings
 * alongside the shared/global configuration, which made them easy to miss
 * (buried among DPD credentials, invoice terms etc.) and easy to mistake for
 * something that affected everyone. This page holds only the two sections
 * that were always per-user under the hood (GET/PUT /users/me/settings) -
 * reached from the sidebar via the settings icon next to your name, not
 * through Admin.
 */
export default function AccountSettings() {
  const [statusColors, setStatusColors] = useState<Record<OrderStatus, string>>(DEFAULT_STATUS_COLORS);
  const [colorsSaved, setColorsSaved] = useState(false);
  const [myEmailUsername, setMyEmailUsername] = useState("");
  const [myEmailPassword, setMyEmailPassword] = useState("");
  const [myEmailFromAddress, setMyEmailFromAddress] = useState("");
  const [myEmailCc, setMyEmailCc] = useState("");
  const [myEmailSaved, setMyEmailSaved] = useState(false);
  const queryClient = useQueryClient();

  const { data: myUserSettings } = useQuery({
    queryKey: ["my-user-settings"],
    queryFn: async () => (await api.get<Record<string, string>>("/users/me/settings")).data,
  });

  useEffect(() => {
    setStatusColors(resolveStatusColors(myUserSettings));
    setMyEmailUsername(myUserSettings?.["email_username"] ?? "");
    // email_password deliberately never populated back - the same reasoning
    // as smtp_password/dpd_api_secret on the main Settings page.
    setMyEmailFromAddress(myUserSettings?.["email_from_address"] ?? "");
    setMyEmailCc(myUserSettings?.["email_cc_address"] ?? "");
  }, [myUserSettings]);

  const saveColorsMutation = useMutation({
    mutationFn: async () =>
      api.put("/users/me/settings", Object.fromEntries(ORDER_STATUSES.map((s) => [statusColorSettingKey(s), statusColors[s]]))),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["my-user-settings"] });
      setColorsSaved(true);
      setTimeout(() => setColorsSaved(false), 2500);
    },
  });

  const saveMyEmailMutation = useMutation({
    mutationFn: async () =>
      api.put("/users/me/settings", {
        email_username: myEmailUsername,
        ...(myEmailPassword ? { email_password: myEmailPassword } : {}),
        email_from_address: myEmailFromAddress,
        email_cc_address: myEmailCc,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["my-user-settings"] });
      setMyEmailPassword("");
      setMyEmailSaved(true);
      setTimeout(() => setMyEmailSaved(false), 2500);
    },
  });

  return (
    <div className="max-w-5xl pb-24">
      <h2 className="text-2xl font-semibold text-slate-800 mb-2">Account Settings</h2>
      <p className="text-slate-500 mb-6">
        Just for you - these follow your login, not shared with anyone else using the system. For shared/global
        configuration (printing, DPD, invoicing, other users' accounts...) see Admin &gt; Settings.
      </p>

      <SettingsSection
        title="My Email"
        description="Acknowledgement and despatch confirmation emails you send go out through these, not the shared Settings > Email account. Leave any field blank to fall back to the shared account for that part."
        defaultOpen
      >
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Email username</label>
            <input value={myEmailUsername} onChange={(e) => setMyEmailUsername(e.target.value)} className="input" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">
              Email password (leave blank to keep the current one)
            </label>
            <input
              type="password"
              value={myEmailPassword}
              onChange={(e) => setMyEmailPassword(e.target.value)}
              placeholder="••••••••"
              className="input"
            />
          </div>
        </div>
        <div>
          <label className="block text-xs font-medium text-slate-500 mb-1">From address</label>
          <input
            value={myEmailFromAddress}
            onChange={(e) => setMyEmailFromAddress(e.target.value)}
            placeholder="e.g. dan@bnsdistribution.co.uk"
            className="input"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-slate-500 mb-1">CC (optional)</label>
          <input
            value={myEmailCc}
            onChange={(e) => setMyEmailCc(e.target.value)}
            placeholder="e.g. orders@bnsdistribution.co.uk"
            className="input"
          />
        </div>
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => saveMyEmailMutation.mutate()}
            disabled={saveMyEmailMutation.isPending}
            className="bg-slate-800 text-white text-xs px-3 py-1.5 rounded hover:bg-slate-700 disabled:opacity-50"
          >
            {saveMyEmailMutation.isPending ? "Saving..." : "Save My Email"}
          </button>
          <SavedBadge show={myEmailSaved} />
        </div>
      </SettingsSection>

      <SettingsSection title="Customisation" description="Order status colours used across the app." defaultOpen>
        <div>
          <h4 className="text-sm font-medium text-slate-700 mb-2">Order status colours</h4>
          <p className="text-xs text-slate-500 mb-3">
            Used for the row colour on Sales Activity, so a screen full of orders is scannable at a glance.
          </p>
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
            {ORDER_STATUSES.map((status) => (
              <div key={status} className="flex items-center gap-2">
                <input
                  type="color"
                  value={statusColors[status]}
                  onChange={(e) => setStatusColors((prev) => ({ ...prev, [status]: e.target.value }))}
                  className="w-9 h-9 rounded border border-slate-300 cursor-pointer shrink-0"
                />
                <span className="text-sm text-slate-600">{statusLabel(status)}</span>
              </div>
            ))}
          </div>
          <div className="flex items-center gap-3 mt-3">
            <button
              type="button"
              onClick={() => setStatusColors(DEFAULT_STATUS_COLORS)}
              className="text-xs text-slate-500 hover:text-slate-700"
            >
              Reset to defaults
            </button>
            <button
              type="button"
              onClick={() => saveColorsMutation.mutate()}
              disabled={saveColorsMutation.isPending}
              className="bg-slate-800 text-white text-xs px-3 py-1.5 rounded hover:bg-slate-700 disabled:opacity-50"
            >
              {saveColorsMutation.isPending ? "Saving..." : "Save Colours"}
            </button>
            <SavedBadge show={colorsSaved} />
          </div>
        </div>
      </SettingsSection>
    </div>
  );
}
