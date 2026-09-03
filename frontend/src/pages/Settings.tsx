import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { api } from "../api/client";
import { DEFAULT_STATUS_COLORS, ORDER_STATUSES, resolveStatusColors, statusColorSettingKey, statusLabel } from "../utils/statusColors";
import { OrderStatus } from "../types";
import { useAuth } from "../auth/AuthContext";

interface UserView {
  id: number;
  name: string;
}

function SettingsSection({ title, description, children }: { title: string; description?: React.ReactNode; children: React.ReactNode }) {
  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5 mb-6">
      <h3 className="font-medium text-slate-800 mb-1">{title}</h3>
      {description && <p className="text-sm text-slate-500 mb-4">{description}</p>}
      <div className="space-y-4">{children}</div>
    </div>
  );
}

export default function Settings() {
  const [printerName, setPrinterName] = useState("");
  const [printAgentUrl, setPrintAgentUrl] = useState("");
  const [autoAcknowledge, setAutoAcknowledge] = useState(true);
  const [packingMode, setPackingMode] = useState<"SPLIT" | "SERIAL">("SPLIT");
  const [nonFaultyReturnDays, setNonFaultyReturnDays] = useState("28");
  const [faultyWarrantyDays, setFaultyWarrantyDays] = useState("365");
  const [statusColors, setStatusColors] = useState<Record<OrderStatus, string>>(DEFAULT_STATUS_COLORS);
  const [saved, setSaved] = useState(false);
  const [colorsSaved, setColorsSaved] = useState(false);
  const queryClient = useQueryClient();
  const { user } = useAuth();

  const { data: settings } = useQuery({
    queryKey: ["settings"],
    queryFn: async () => (await api.get<Record<string, string>>("/settings")).data,
  });

  // Per-user, not global - this is the one thing in Settings that follows a
  // specific person rather than describing how the whole warehouse operates.
  const { data: myUserSettings } = useQuery({
    queryKey: ["my-user-settings"],
    queryFn: async () => (await api.get<Record<string, string>>("/users/me/settings")).data,
  });

  const { data: testDataResetStatus } = useQuery({
    queryKey: ["test-data-reset-status"],
    queryFn: async () => (await api.get<{ enabled: boolean }>("/admin/test-data-reset")).data,
  });

  const { data: users } = useQuery({
    queryKey: ["users"],
    queryFn: async () => (await api.get<UserView[]>("/users")).data,
  });

  useEffect(() => {
    if (!settings) return;
    setPrinterName(settings["picking_note_printer"] ?? "");
    setPrintAgentUrl(settings["print_agent_url"] ?? "http://localhost:9191/print");
    setAutoAcknowledge((settings["auto_acknowledge_on_release"] ?? "true") === "true");
    setPackingMode((settings["packing_mode"] as "SPLIT" | "SERIAL") ?? "SPLIT");
    setNonFaultyReturnDays(settings["rma_non_faulty_return_days"] ?? "28");
    setFaultyWarrantyDays(settings["rma_faulty_warranty_days"] ?? "365");
  }, [settings]);

  useEffect(() => {
    setStatusColors(resolveStatusColors(myUserSettings));
  }, [myUserSettings]);

  const saveMutation = useMutation({
    mutationFn: async () =>
      api.put("/settings", {
        picking_note_printer: printerName,
        print_agent_url: printAgentUrl,
        auto_acknowledge_on_release: String(autoAcknowledge),
        packing_mode: packingMode,
        rma_non_faulty_return_days: nonFaultyReturnDays,
        rma_faulty_warranty_days: faultyWarrantyDays,
      }),
    onSuccess: () => {
      setSaved(true);
      setTimeout(() => setSaved(false), 2500);
    },
  });

  const saveColorsMutation = useMutation({
    mutationFn: async () =>
      api.put("/users/me/settings", Object.fromEntries(ORDER_STATUSES.map((s) => [statusColorSettingKey(s), statusColors[s]]))),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["my-user-settings"] });
      setColorsSaved(true);
      setTimeout(() => setColorsSaved(false), 2500);
    },
  });

  const [resetConfirmText, setResetConfirmText] = useState("");
  const [resetDone, setResetDone] = useState(false);
  const resetMutation = useMutation({
    mutationFn: async () => api.post("/admin/test-data-reset"),
    onSuccess: () => {
      setResetConfirmText("");
      setResetDone(true);
      setTimeout(() => setResetDone(false), 4000);
      queryClient.invalidateQueries();
    },
  });

  const [clearProductsConfirmText, setClearProductsConfirmText] = useState("");
  const [clearProductsSummary, setClearProductsSummary] = useState<string[] | null>(null);
  const clearProductsMutation = useMutation({
    mutationFn: async () => (await api.post<string[]>("/admin/test-data-reset/demo-products")).data,
    onSuccess: (summary) => {
      setClearProductsConfirmText("");
      setClearProductsSummary(summary);
      queryClient.invalidateQueries();
    },
  });

  const [newUserName, setNewUserName] = useState("");
  const [newUserPassword, setNewUserPassword] = useState("");
  const [newUserError, setNewUserError] = useState<string | null>(null);
  const createUserMutation = useMutation({
    mutationFn: async () => api.post("/users", { name: newUserName, password: newUserPassword }),
    onSuccess: () => {
      setNewUserName("");
      setNewUserPassword("");
      setNewUserError(null);
      queryClient.invalidateQueries({ queryKey: ["users"] });
    },
    onError: (err: Error) => setNewUserError(err.message),
  });

  const [passwordChangeUserId, setPasswordChangeUserId] = useState<number | null>(null);
  const [newPassword, setNewPassword] = useState("");
  const [passwordChangeError, setPasswordChangeError] = useState<string | null>(null);
  const changePasswordMutation = useMutation({
    mutationFn: async () => api.put(`/users/${passwordChangeUserId}/password`, { newPassword }),
    onSuccess: () => {
      setPasswordChangeUserId(null);
      setNewPassword("");
      setPasswordChangeError(null);
    },
    onError: (err: Error) => setPasswordChangeError(err.message),
  });

  return (
    <div className="max-w-xl">
      <h2 className="text-2xl font-semibold text-slate-800 mb-2">Settings</h2>
      <p className="text-slate-500 mb-6">Grouped by area - printing, despatch, returns, and admin.</p>

      <SettingsSection
        title="Printing & Picking Notes"
        description={
          <>
            Requires the local print agent running on the warehouse PC - see{" "}
            <code className="bg-slate-100 px-1 rounded">print-agent/README.md</code> in the project for setup.
            Without it, "Print Picking Note" falls back to opening the PDF in a new tab.
          </>
        }
      >
        <div>
          <label className="block text-xs font-medium text-slate-500 mb-1">
            Printer name (leave blank to use the PC's default printer)
          </label>
          <input
            value={printerName}
            onChange={(e) => setPrinterName(e.target.value)}
            placeholder="e.g. Warehouse Label Printer"
            className="input"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-slate-500 mb-1">Print agent URL</label>
          <input value={printAgentUrl} onChange={(e) => setPrintAgentUrl(e.target.value)} className="input" />
        </div>
      </SettingsSection>

      <SettingsSection title="Despatch &amp; Packing">
        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input type="checkbox" checked={autoAcknowledge} onChange={(e) => setAutoAcknowledge(e.target.checked)} />
          Automatically send the acknowledgement email when an order is released for despatch
        </label>
        <p className="text-xs text-slate-400 -mt-2 ml-6">
          Removes the separate "Send Acknowledgement" step entirely for the normal case.
        </p>

        <div className="pt-2 border-t border-slate-100">
          <h4 className="text-sm font-medium text-slate-700 mb-1">Packing Mode</h4>
          <p className="text-xs text-slate-500 mb-2">
            How cartons are packed on the Despatch screen. Applies to every order going forward - switching
            mid-order isn't supported.
          </p>
          <div className="space-y-2">
            <label className="flex items-start gap-2 text-sm text-slate-700">
              <input
                type="radio"
                name="packing_mode"
                checked={packingMode === "SPLIT"}
                onChange={() => setPackingMode("SPLIT")}
                className="mt-1"
              />
              <span>
                <span className="font-medium">Split Packing</span> - split a line's required quantity across
                cartons (e.g. split 32 into 30 + 2, or by quantity into four lots of 8). Doesn't track which
                specific serial went in which box.
              </span>
            </label>
            <label className="flex items-start gap-2 text-sm text-slate-700">
              <input
                type="radio"
                name="packing_mode"
                checked={packingMode === "SERIAL"}
                onChange={() => setPackingMode("SERIAL")}
                className="mt-1"
              />
              <span>
                <span className="font-medium">Serial Packing</span> - assign each individual scanned unit to a
                carton by serial/MAC, so you know exactly which units are in which box.
              </span>
            </label>
          </div>
        </div>
      </SettingsSection>

      <SettingsSection
        title="Returns (RMA)"
        description="How long a customer can return an item, and which window applies. These drive the automatic warranty check on the public RMA form."
      >
        <div>
          <label className="block text-xs font-medium text-slate-500 mb-1">
            Non-faulty return window (days)
          </label>
          <input
            type="number"
            min={1}
            value={nonFaultyReturnDays}
            onChange={(e) => setNonFaultyReturnDays(e.target.value)}
            className="input max-w-[140px]"
          />
          <p className="text-xs text-slate-400 mt-1">Used when an RMA item isn't marked faulty. Default 28.</p>
        </div>
        <div>
          <label className="block text-xs font-medium text-slate-500 mb-1">Faulty RTB warranty (days)</label>
          <input
            type="number"
            min={1}
            value={faultyWarrantyDays}
            onChange={(e) => setFaultyWarrantyDays(e.target.value)}
            className="input max-w-[140px]"
          />
          <p className="text-xs text-slate-400 mt-1">
            Used when an RMA item is marked faulty. Default 365 (1 year). This is BNS's own return-to-base
            warranty - Grandstream's own warranty is separate and still needs checking by hand on their portal.
          </p>
        </div>
      </SettingsSection>

      <SettingsSection
        title="Users"
        description="Anyone with a login can manage other logins for now - there's no admin/staff distinction yet, matching how the rest of this app works."
      >
        <div className="space-y-2">
          {users?.map((u) => (
            <div key={u.id} className="flex items-center justify-between border border-slate-100 rounded px-3 py-2">
              <span className="text-sm text-slate-700">
                {u.name} {u.name === user?.name && <span className="text-xs text-emerald-600 ml-1">(you)</span>}
              </span>
              {passwordChangeUserId === u.id ? (
                <div className="flex items-center gap-2">
                  <input
                    type="password"
                    autoFocus
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    placeholder="New password"
                    className="input w-40 text-sm"
                  />
                  <button
                    onClick={() => changePasswordMutation.mutate()}
                    disabled={changePasswordMutation.isPending || newPassword.length < 6}
                    className="text-xs bg-slate-800 text-white px-3 py-1.5 rounded hover:bg-slate-700 disabled:opacity-50"
                  >
                    Save
                  </button>
                  <button
                    onClick={() => {
                      setPasswordChangeUserId(null);
                      setNewPassword("");
                      setPasswordChangeError(null);
                    }}
                    className="text-xs text-slate-400 hover:text-slate-600"
                  >
                    Cancel
                  </button>
                </div>
              ) : (
                <button
                  onClick={() => setPasswordChangeUserId(u.id)}
                  className="text-xs text-slate-500 hover:text-slate-700 border border-slate-300 rounded px-2 py-1"
                >
                  Change password
                </button>
              )}
            </div>
          ))}
        </div>
        {passwordChangeError && <p className="text-xs text-red-600">{passwordChangeError}</p>}

        <div className="pt-3 border-t border-slate-100">
          <h4 className="text-sm font-medium text-slate-700 mb-2">New Login</h4>
          <div className="flex gap-2 items-end flex-wrap">
            <div>
              <label className="block text-xs text-slate-400 mb-1">Name</label>
              <input value={newUserName} onChange={(e) => setNewUserName(e.target.value)} className="input w-44" />
            </div>
            <div>
              <label className="block text-xs text-slate-400 mb-1">Password (min. 6 characters)</label>
              <input
                type="password"
                value={newUserPassword}
                onChange={(e) => setNewUserPassword(e.target.value)}
                className="input w-44"
              />
            </div>
            <button
              onClick={() => createUserMutation.mutate()}
              disabled={createUserMutation.isPending || !newUserName || newUserPassword.length < 6}
              className="bg-emerald-600 text-white text-sm px-4 py-2 rounded-md hover:bg-emerald-500 disabled:opacity-50"
            >
              {createUserMutation.isPending ? "Adding..." : "Add Login"}
            </button>
          </div>
          {newUserError && <p className="text-xs text-red-600 mt-2">{newUserError}</p>}
        </div>
      </SettingsSection>

      <SettingsSection
        title="Customisation"
        description="Just for you - these follow your login, not shared with anyone else using the system."
      >
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
            {colorsSaved && <span className="text-xs text-emerald-600">Saved.</span>}
          </div>
        </div>
      </SettingsSection>

      <button
        onClick={() => saveMutation.mutate()}
        disabled={saveMutation.isPending}
        className="bg-emerald-600 text-white text-sm px-5 py-2.5 rounded-md hover:bg-emerald-500 disabled:opacity-50"
      >
        {saveMutation.isPending ? "Saving..." : "Save Settings"}
      </button>
      {saved && <span className="ml-3 text-sm text-emerald-600">Saved.</span>}

      <div className="bg-white border border-amber-200 rounded-lg p-5 mt-8">
        <h3 className="font-medium text-amber-700 mb-1">Bulk Stock Import</h3>
        <p className="text-sm text-slate-500 mb-3">
          One-off replacement of current on-hand stock from an OrderWise export - not a test-data tool, this stays
          available once real data is live too.
        </p>
        <Link
          to="/settings/stock-import"
          className="inline-block bg-slate-800 text-white text-sm px-4 py-2 rounded-md hover:bg-slate-700"
        >
          Open Stock Import
        </Link>
      </div>

      {testDataResetStatus?.enabled && (
        <div className="bg-white border border-red-200 rounded-lg p-5 mt-8">
          <h3 className="font-medium text-red-700 mb-1">Danger Zone - Reset Test Data</h3>
          <p className="text-sm text-slate-500 mb-3">
            Permanently deletes all stock items, stock movements, inventory, expected
            cartons/items, goods-in sessions, and purchase orders (and their lines). Products,
            locations, suppliers, sales orders, bug reports and API keys are left untouched.
            This cannot be undone.
          </p>
          <p className="text-xs text-slate-400 mb-3">
            This button is only available because <code className="bg-slate-100 px-1 rounded">ALLOW_TEST_DATA_RESET</code> is
            set for this environment - it should stay off anywhere real data is trusted.
          </p>
          <label className="block text-xs font-medium text-slate-500 mb-1">
            Type RESET to confirm
          </label>
          <div className="flex gap-3 items-center">
            <input
              value={resetConfirmText}
              onChange={(e) => setResetConfirmText(e.target.value)}
              placeholder="RESET"
              className="border border-slate-300 rounded px-3 py-2 text-sm w-40"
            />
            <button
              onClick={() => resetMutation.mutate()}
              disabled={resetConfirmText !== "RESET" || resetMutation.isPending}
              className="bg-red-600 text-white text-sm px-4 py-2 rounded-md hover:bg-red-500 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {resetMutation.isPending ? "Resetting..." : "Reset Stock & Purchase Orders"}
            </button>
          </div>
          {resetDone && (
            <p className="text-sm text-emerald-600 mt-2">Stock and purchase order data cleared.</p>
          )}
          {resetMutation.isError && (
            <p className="text-sm text-red-600 mt-2">{(resetMutation.error as Error).message}</p>
          )}

          <div className="border-t border-red-100 mt-5 pt-5">
            <h4 className="font-medium text-red-700 mb-1">Clear Demo Products</h4>
            <p className="text-sm text-slate-500 mb-3">
              Removes the four pre-seeded example products (GWN7802P, GRP2615, SFP-1G, PATCH-CAT6-1M) and their
              four demo sample orders - useful once real product data (e.g. from Shopify) is coming in and you
              want a clean catalogue. Anything you've actually built on top of the demo data (a pick, an RMA) is
              left in place rather than broken - you'll get a per-item outcome below either way. Run
              "Reset Stock &amp; Purchase Orders" above first for the cleanest result.
            </p>
            <label className="block text-xs font-medium text-slate-500 mb-1">Type CLEAR to confirm</label>
            <div className="flex gap-3 items-center">
              <input
                value={clearProductsConfirmText}
                onChange={(e) => setClearProductsConfirmText(e.target.value)}
                placeholder="CLEAR"
                className="border border-slate-300 rounded px-3 py-2 text-sm w-40"
              />
              <button
                onClick={() => clearProductsMutation.mutate()}
                disabled={clearProductsConfirmText !== "CLEAR" || clearProductsMutation.isPending}
                className="bg-red-600 text-white text-sm px-4 py-2 rounded-md hover:bg-red-500 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {clearProductsMutation.isPending ? "Clearing..." : "Clear Demo Products"}
              </button>
            </div>
            {clearProductsSummary && (
              <ul className="text-sm text-slate-600 mt-2 space-y-0.5">
                {clearProductsSummary.map((line, i) => (
                  <li key={i}>{line}</li>
                ))}
              </ul>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
