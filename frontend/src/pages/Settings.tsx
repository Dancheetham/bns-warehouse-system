import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { api } from "../api/client";
import { DEFAULT_STATUS_COLORS, ORDER_STATUSES, resolveStatusColors, statusColorSettingKey, statusLabel } from "../utils/statusColors";
import { OrderStatus } from "../types";
import { useAuth } from "../auth/AuthContext";
import { useToast } from "../components/ToastContext";

interface UserView {
  id: number;
  name: string;
}

interface RestoreResult {
  success: boolean;
  backedUpAt: string | null;
  backedUpFromDatabase: string | null;
  secretKeysInBackup: string[];
}

/**
 * One collapsible block of settings. Everything starts collapsed so the page
 * opens as a short list of headings you can scan, rather than a very long
 * scroll - open just the area you came here to change. Collapsed state is
 * per-section and deliberately not remembered between visits: the useful
 * default is always "show me the list", not "show me whatever I left open
 * last time".
 */
function SettingsSection({
  title,
  description,
  defaultOpen = false,
  children,
}: {
  title: string;
  description?: React.ReactNode;
  defaultOpen?: boolean;
  children: React.ReactNode;
}) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <div className="bg-white border border-slate-200 rounded-lg mb-4">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="w-full flex items-center justify-between gap-3 px-5 py-4 text-left hover:bg-slate-50 rounded-lg"
      >
        <span className="font-medium text-slate-800">{title}</span>
        <span className={`text-slate-400 text-xs transition-transform ${open ? "rotate-90" : ""}`}>▶</span>
      </button>
      {open && (
        <div className="px-5 pb-5">
          {description && <p className="text-sm text-slate-500 mb-4">{description}</p>}
          <div className="space-y-4">{children}</div>
        </div>
      )}
    </div>
  );
}

export default function Settings() {
  const [printerName, setPrinterName] = useState("");
  const [labelPrinterName, setLabelPrinterName] = useState("");
  const [labelPrinterDpi, setLabelPrinterDpi] = useState("203");
  const [printAgentUrl, setPrintAgentUrl] = useState("");
  const [smtpHost, setSmtpHost] = useState("");
  const [smtpPort, setSmtpPort] = useState("587");
  const [smtpUsername, setSmtpUsername] = useState("");
  const [smtpPassword, setSmtpPassword] = useState("");
  const [mailFromAddress, setMailFromAddress] = useState("");
  const [dpdApiKey, setDpdApiKey] = useState("");
  const [dpdApiSecret, setDpdApiSecret] = useState("");
  const [dpdEnvironment, setDpdEnvironment] = useState<"sandbox" | "live">("sandbox");
  const [dpdNetworkCode, setDpdNetworkCode] = useState("");
  const [dpdMaxLookupWeightKg, setDpdMaxLookupWeightKg] = useState("");
  const [dpdSenderOrganisation, setDpdSenderOrganisation] = useState("");
  const [dpdSenderStreet, setDpdSenderStreet] = useState("");
  const [dpdSenderLocality, setDpdSenderLocality] = useState("");
  const [dpdSenderTown, setDpdSenderTown] = useState("");
  const [dpdSenderCounty, setDpdSenderCounty] = useState("");
  const [dpdSenderPostcode, setDpdSenderPostcode] = useState("");
  const [dpdSenderCountryCode, setDpdSenderCountryCode] = useState("GB");
  const [dpdSenderContactName, setDpdSenderContactName] = useState("");
  const [dpdSenderContactPhone, setDpdSenderContactPhone] = useState("");
  const [dpdEoriNumber, setDpdEoriNumber] = useState("");
  const [dpdSenderVatNumber, setDpdSenderVatNumber] = useState("");
  const [dpdGoodsDescription, setDpdGoodsDescription] = useState("");
  const [dpdCurrency, setDpdCurrency] = useState("GBP");
  const [dpdExtendedLiability, setDpdExtendedLiability] = useState(false);
  const [printSampleLabels, setPrintSampleLabels] = useState(true);
  const [autoAcknowledge, setAutoAcknowledge] = useState(true);
  const [autoPrintPickingNote, setAutoPrintPickingNote] = useState(true);
  const [packingMode, setPackingMode] = useState<"SPLIT" | "SERIAL">("SPLIT");
  const [nonFaultyReturnDays, setNonFaultyReturnDays] = useState("28");
  const [faultyWarrantyDays, setFaultyWarrantyDays] = useState("365");
  const [statusColors, setStatusColors] = useState<Record<OrderStatus, string>>(DEFAULT_STATUS_COLORS);
  const [saved, setSaved] = useState(false);
  const [colorsSaved, setColorsSaved] = useState(false);
  const [myEmailUsername, setMyEmailUsername] = useState("");
  const [myEmailPassword, setMyEmailPassword] = useState("");
  const [myEmailFromAddress, setMyEmailFromAddress] = useState("");
  const [myEmailCc, setMyEmailCc] = useState("");
  const [myEmailSaved, setMyEmailSaved] = useState(false);
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const { showToast } = useToast();

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
    setLabelPrinterName(settings["label_printer"] ?? "");
    setLabelPrinterDpi(settings["dpd_label_printer_dpi"] ?? "203");
    setPrintAgentUrl(settings["print_agent_url"] ?? "http://localhost:9191/print");
    setSmtpHost(settings["smtp_host"] ?? "");
    setSmtpPort(settings["smtp_port"] ?? "587");
    setSmtpUsername(settings["smtp_username"] ?? "");
    // smtp_password is deliberately never populated back into the form, even
    // though it comes back from GET /settings like everything else here -
    // pre-filling a password field with the real stored value on every page
    // load is the wrong default. Left blank and only sent on save if the
    // user actually types a new one - see saveMutation below.
    setMailFromAddress(settings["mail_from_address"] ?? "");
    setDpdApiKey(settings["dpd_api_key"] ?? "");
    // dpd_api_secret deliberately never populated back, same reasoning as
    // smtp_password above.
    setDpdEnvironment((settings["dpd_environment"] as "sandbox" | "live") ?? "sandbox");
    setDpdNetworkCode(settings["dpd_network_code"] ?? "");
    setDpdMaxLookupWeightKg(settings["dpd_max_lookup_weight_kg"] ?? "");
    setDpdSenderOrganisation(settings["dpd_sender_organisation"] ?? "");
    setDpdSenderStreet(settings["dpd_sender_street"] ?? "");
    setDpdSenderLocality(settings["dpd_sender_locality"] ?? "");
    setDpdSenderTown(settings["dpd_sender_town"] ?? "");
    setDpdSenderCounty(settings["dpd_sender_county"] ?? "");
    setDpdSenderPostcode(settings["dpd_sender_postcode"] ?? "");
    setDpdSenderCountryCode(settings["dpd_sender_country_code"] ?? "GB");
    setDpdSenderContactName(settings["dpd_sender_contact_name"] ?? "");
    setDpdSenderContactPhone(settings["dpd_sender_contact_phone"] ?? "");
    setDpdEoriNumber(settings["dpd_eori_number"] ?? "");
    setDpdSenderVatNumber(settings["dpd_sender_vat_number"] ?? "");
    setDpdGoodsDescription(settings["dpd_goods_description"] ?? "Telecoms and networking equipment");
    setDpdCurrency(settings["dpd_currency"] ?? "GBP");
    setDpdExtendedLiability((settings["dpd_extended_liability"] ?? "false") === "true");
    setPrintSampleLabels((settings["print_sample_labels"] ?? "true") === "true");
    setAutoAcknowledge((settings["auto_acknowledge_on_release"] ?? "true") === "true");
    setAutoPrintPickingNote((settings["auto_print_picking_note_on_release"] ?? "true") === "true");
    setPackingMode((settings["packing_mode"] as "SPLIT" | "SERIAL") ?? "SPLIT");
    setNonFaultyReturnDays(settings["rma_non_faulty_return_days"] ?? "28");
    setFaultyWarrantyDays(settings["rma_faulty_warranty_days"] ?? "365");
  }, [settings]);

  useEffect(() => {
    setStatusColors(resolveStatusColors(myUserSettings));
    setMyEmailUsername(myUserSettings?.["email_username"] ?? "");
    // email_password deliberately never populated back, same reasoning as
    // the global smtp_password above.
    setMyEmailFromAddress(myUserSettings?.["email_from_address"] ?? "");
    setMyEmailCc(myUserSettings?.["email_cc_address"] ?? "");
  }, [myUserSettings]);

  const saveMutation = useMutation({
    mutationFn: async () =>
      api.put("/settings", {
        picking_note_printer: printerName,
        label_printer: labelPrinterName,
        dpd_label_printer_dpi: labelPrinterDpi,
        print_agent_url: printAgentUrl,
        smtp_host: smtpHost,
        smtp_port: smtpPort,
        smtp_username: smtpUsername,
        // Only included when actually typed - omitting it entirely (rather
        // than sending an empty string) means the existing stored password
        // is left untouched when saving any other setting on this page.
        ...(smtpPassword ? { smtp_password: smtpPassword } : {}),
        mail_from_address: mailFromAddress,
        dpd_api_key: dpdApiKey,
        // Only included when actually typed - same reasoning as smtp_password
        // above, so saving anything else on this page doesn't wipe the secret.
        ...(dpdApiSecret ? { dpd_api_secret: dpdApiSecret } : {}),
        dpd_environment: dpdEnvironment,
        dpd_network_code: dpdNetworkCode,
        dpd_max_lookup_weight_kg: dpdMaxLookupWeightKg,
        dpd_sender_organisation: dpdSenderOrganisation,
        dpd_sender_street: dpdSenderStreet,
        dpd_sender_locality: dpdSenderLocality,
        dpd_sender_town: dpdSenderTown,
        dpd_sender_county: dpdSenderCounty,
        dpd_sender_postcode: dpdSenderPostcode,
        dpd_sender_country_code: dpdSenderCountryCode,
        dpd_sender_contact_name: dpdSenderContactName,
        dpd_sender_contact_phone: dpdSenderContactPhone,
        dpd_eori_number: dpdEoriNumber,
        dpd_sender_vat_number: dpdSenderVatNumber,
        dpd_goods_description: dpdGoodsDescription,
        dpd_currency: dpdCurrency,
        dpd_extended_liability: String(dpdExtendedLiability),
        print_sample_labels: String(printSampleLabels),
        auto_acknowledge_on_release: String(autoAcknowledge),
        auto_print_picking_note_on_release: String(autoPrintPickingNote),
        packing_mode: packingMode,
        rma_non_faulty_return_days: nonFaultyReturnDays,
        rma_faulty_warranty_days: faultyWarrantyDays,
      }),
    onSuccess: () => {
      setSaved(true);
      showToast("Saved.");
      setTimeout(() => setSaved(false), 2500);
    },
  });

  const saveColorsMutation = useMutation({
    mutationFn: async () =>
      api.put("/users/me/settings", Object.fromEntries(ORDER_STATUSES.map((s) => [statusColorSettingKey(s), statusColors[s]]))),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["my-user-settings"] });
      setColorsSaved(true);
      showToast("Saved.");
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
      showToast("Saved.");
      setTimeout(() => setMyEmailSaved(false), 2500);
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

  const [backupDownloading, setBackupDownloading] = useState(false);
  const [backupError, setBackupError] = useState<string | null>(null);
  const [restoreFile, setRestoreFile] = useState<File | null>(null);
  const [restoreConfirmText, setRestoreConfirmText] = useState("");
  const [restoreResult, setRestoreResult] = useState<RestoreResult | null>(null);
  const restoreMutation = useMutation({
    mutationFn: async () => {
      if (!restoreFile) throw new Error("Choose a backup .zip file first.");
      const formData = new FormData();
      formData.append("file", restoreFile);
      return (await api.post<RestoreResult>("/admin/backup/restore", formData, {
        headers: { "Content-Type": "multipart/form-data" },
      })).data;
    },
    onSuccess: (result) => {
      setRestoreResult(result);
      setRestoreConfirmText("");
      setRestoreFile(null);
      // A restore replaces literally everything in the database, so every
      // screen's cached data is stale now - not just one query key.
      queryClient.invalidateQueries();
    },
  });

  const downloadBackup = async () => {
    setBackupError(null);
    setBackupDownloading(true);
    try {
      const res = await api.get("/admin/backup", { responseType: "blob" });
      const url = window.URL.createObjectURL(res.data);
      const a = document.createElement("a");
      a.href = url;
      a.download = `bns-warehouse-backup-${new Date().toISOString().slice(0, 10)}.zip`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.URL.revokeObjectURL(url);
    } catch (err) {
      setBackupError((err as Error).message);
    } finally {
      setBackupDownloading(false);
    }
  };

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
    // Uses the full page width rather than a narrow left-hand column, so the
    // two-column field grids inside each section have room to breathe, and
    // leaves space at the bottom for the floating Save button to sit over.
    <div className="max-w-5xl pb-24">
      <h2 className="text-2xl font-semibold text-slate-800 mb-2">Settings</h2>
      <p className="text-slate-500 mb-6">
        Grouped by area - printing, despatch, returns, and admin. Click a heading to open it.
      </p>

      <SettingsSection
        title="Printing"
        description={
          <>
            Requires the local print agent running on the warehouse PC - see{" "}
            <code className="bg-slate-100 px-1 rounded">print-agent/README.md</code> in the project for setup.
            Without it, printing falls back to opening the PDF in a new tab instead. Picking notes and shipping
            labels can go to two different printers - most warehouses have a label printer right at the despatch
            bench, separate from wherever picking notes come out.
          </>
        }
      >
        <div>
          <label className="block text-xs font-medium text-slate-500 mb-1">
            Picking note printer (leave blank to use the PC's default printer)
          </label>
          <input
            value={printerName}
            onChange={(e) => setPrinterName(e.target.value)}
            placeholder="e.g. Office Printer"
            className="input"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-slate-500 mb-1">
            Shipping label printer (leave blank to use the PC's default printer)
          </label>
          <input
            value={labelPrinterName}
            onChange={(e) => setLabelPrinterName(e.target.value)}
            placeholder="e.g. Despatch Label Printer"
            className="input"
          />
          <p className="text-xs text-slate-400 mt-1">
            DPD shipping labels print as raw ZPL straight to this printer via the print agent (Print Agent URL
            below) - needs a ZPL-compatible thermal printer (e.g. Zebra) and pywin32 installed alongside the
            agent. See the print-agent README for setup.
          </p>
        </div>
        <div>
          <label className="block text-xs font-medium text-slate-500 mb-1">Label printer DPI</label>
          <select
            value={labelPrinterDpi}
            onChange={(e) => setLabelPrinterDpi(e.target.value)}
            className="input w-32"
          >
            <option value="203">203 dpi</option>
            <option value="300">300 dpi</option>
          </select>
          <p className="text-xs text-slate-400 mt-1">
            Must match the shipping label printer's actual resolution (check the printer or its datasheet) -
            a mismatch here can throw off barcode scaling on the printed label.
          </p>
        </div>
        <div>
          <label className="block text-xs font-medium text-slate-500 mb-1">Print agent URL</label>
          <input value={printAgentUrl} onChange={(e) => setPrintAgentUrl(e.target.value)} className="input" />
        </div>
      </SettingsSection>

      <SettingsSection
        title="Email"
        description="The shared/fallback email account - used for anyone who hasn't set up their own under My Email below. Changing this takes effect on the very next email sent, no restart needed."
      >
        <div>
          <label className="block text-xs font-medium text-slate-500 mb-1">SMTP host</label>
          <input
            value={smtpHost}
            onChange={(e) => setSmtpHost(e.target.value)}
            placeholder="e.g. smtp.office365.com"
            className="input"
          />
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">SMTP port</label>
            <input value={smtpPort} onChange={(e) => setSmtpPort(e.target.value)} className="input" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">SMTP username</label>
            <input value={smtpUsername} onChange={(e) => setSmtpUsername(e.target.value)} className="input" />
          </div>
        </div>
        <div>
          <label className="block text-xs font-medium text-slate-500 mb-1">
            SMTP password (leave blank to keep the current one)
          </label>
          <input
            type="password"
            value={smtpPassword}
            onChange={(e) => setSmtpPassword(e.target.value)}
            placeholder="••••••••"
            className="input"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-slate-500 mb-1">From address</label>
          <input
            value={mailFromAddress}
            onChange={(e) => setMailFromAddress(e.target.value)}
            placeholder="e.g. sales@bnsdistribution.co.uk"
            className="input"
          />
        </div>
      </SettingsSection>

      <SettingsSection
        title="DPD"
        description="API credentials and sender details for creating DPD shipments and printing labels directly from an order. The API key/secret pair comes from your DPD developer account (My DPD > API Access), not your normal DPD login."
      >
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">API key (Client-Id)</label>
            <input value={dpdApiKey} onChange={(e) => setDpdApiKey(e.target.value)} className="input" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">
              API secret (leave blank to keep the current one)
            </label>
            <input
              type="password"
              value={dpdApiSecret}
              onChange={(e) => setDpdApiSecret(e.target.value)}
              placeholder="••••••••"
              className="input"
            />
          </div>
        </div>
        <div>
          <label className="block text-xs font-medium text-slate-500 mb-1">Environment</label>
          <select
            value={dpdEnvironment}
            onChange={(e) => setDpdEnvironment(e.target.value as "sandbox" | "live")}
            className="input w-48"
          >
            <option value="sandbox">Sandbox (testing)</option>
            <option value="live">Live</option>
          </select>
        </div>
        <div>
          <label className="block text-xs font-medium text-slate-500 mb-1">Preferred service (optional)</label>
          <input
            value={dpdNetworkCode}
            onChange={(e) => setDpdNetworkCode(e.target.value)}
            placeholder="e.g. 1^06 for Next Day"
            className="input w-48"
          />
          <p className="text-xs text-slate-400 mt-1">
            DPD looks up the real available services for every shipment automatically (they can change route to
            route and aren't safe to hardcode). If you set a code here and it's one of the services DPD offers for
            that shipment, it's used - otherwise DPD's first available service is used instead, so this is a
            preference, not a requirement.
          </p>
        </div>
        <div>
          <label className="block text-xs font-medium text-slate-500 mb-1">
            Never offer Freight - cap the weight sent for the service check at (kg)
          </label>
          <input
            value={dpdMaxLookupWeightKg}
            onChange={(e) => setDpdMaxLookupWeightKg(e.target.value)}
            placeholder="Leave blank to send the real weight"
            className="input w-48"
          />
          <p className="text-xs text-slate-400 mt-1">
            A heavy order can tip DPD's "what services are available" check into only offering its freight/pallet
            service, even when it's really going out as ordinary parcels - this affects the Service dropdown here
            (and on the order screen, which is what the picking note shows to the picker) and which service actually
            gets booked, but never the weight declared on the real shipment itself at despatch - that always uses
            each carton's genuine weight. Set a value below whatever weight tips your account into freight (start
            around 19.9 and adjust from what you see happening) and Freight stops being offered at all; leave blank
            to let DPD decide normally.
          </p>
        </div>

        <h4 className="text-sm font-medium text-slate-700 pt-2">Sender / collection address</h4>
        <p className="text-xs text-slate-400 -mt-3">
          Used as both the collection address and the exporter details on any customs declaration (e.g. for
          shipments to Ireland).
        </p>
        <div className="grid grid-cols-2 lg:grid-cols-3 gap-4">
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Organisation</label>
            <input
              value={dpdSenderOrganisation}
              onChange={(e) => setDpdSenderOrganisation(e.target.value)}
              placeholder="BNS Distribution"
              className="input"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Contact name</label>
            <input value={dpdSenderContactName} onChange={(e) => setDpdSenderContactName(e.target.value)} className="input" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">
              Address line 1 <span className="text-slate-400">(street - required)</span>
            </label>
            <input value={dpdSenderStreet} onChange={(e) => setDpdSenderStreet(e.target.value)} maxLength={35} className="input" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">
              Address line 2 <span className="text-slate-400">(optional)</span>
            </label>
            <input value={dpdSenderLocality} onChange={(e) => setDpdSenderLocality(e.target.value)} maxLength={35} className="input" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">
              Address line 3 <span className="text-slate-400">(town - required)</span>
            </label>
            <input value={dpdSenderTown} onChange={(e) => setDpdSenderTown(e.target.value)} maxLength={35} className="input" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">
              Address line 4 <span className="text-slate-400">(county - optional)</span>
            </label>
            <input value={dpdSenderCounty} onChange={(e) => setDpdSenderCounty(e.target.value)} maxLength={35} className="input" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Postcode</label>
            <input value={dpdSenderPostcode} onChange={(e) => setDpdSenderPostcode(e.target.value)} maxLength={8} className="input" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Country code</label>
            <input
              value={dpdSenderCountryCode}
              onChange={(e) => setDpdSenderCountryCode(e.target.value.toUpperCase())}
              maxLength={2}
              className="input uppercase w-24"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Contact phone</label>
            <input value={dpdSenderContactPhone} onChange={(e) => setDpdSenderContactPhone(e.target.value)} className="input" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">
              EORI number <span className="text-slate-400">(required for Ireland)</span>
            </label>
            <input
              value={dpdEoriNumber}
              onChange={(e) => setDpdEoriNumber(e.target.value.toUpperCase())}
              placeholder="GB123456789000"
              maxLength={14}
              className="input"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">
              VAT number <span className="text-slate-400">(optional)</span>
            </label>
            <input
              value={dpdSenderVatNumber}
              onChange={(e) => setDpdSenderVatNumber(e.target.value.toUpperCase())}
              placeholder="GB123456789"
              className="input"
            />
          </div>
        </div>
        <p className="text-xs text-slate-400">
          DPD's address lines map to street / locality / town / county - only lines 1 and 3 and the country code are
          mandatory. Phone numbers are sent to DPD as digits only (spaces and brackets are stripped automatically),
          since DPD rejects anything else. There's no sender email field because DPD's shipment API doesn't accept
          one.
        </p>
        <h4 className="text-sm font-medium text-slate-700 pt-2">Customs declaration</h4>
        <p className="text-xs text-slate-400 -mt-3">
          Used on shipments that need a customs declaration (currently Ireland).
        </p>
        <div className="grid grid-cols-2 lg:grid-cols-3 gap-4">
          <div className="lg:col-span-2">
            <label className="block text-xs font-medium text-slate-500 mb-1">Goods description</label>
            <input
              value={dpdGoodsDescription}
              onChange={(e) => setDpdGoodsDescription(e.target.value)}
              maxLength={45}
              placeholder="e.g. IP telephones and network switches"
              className="input"
            />
            <p className="text-xs text-slate-400 mt-1">
              Describes the contents of the whole consignment, not individual products - DPD require it on every
              shipment outside mainland UK and reject the booking without it. Keep it specific: DPD warn that vague
              descriptions get parcels delayed or returned at customs (their own example is "women's cotton dresses"
              rather than just "clothing"). Max 45 characters.
            </p>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Currency</label>
            <select value={dpdCurrency} onChange={(e) => setDpdCurrency(e.target.value)} className="input w-32">
              <option value="GBP">GBP</option>
              <option value="EUR">EUR</option>
              <option value="USD">USD</option>
            </select>
            <p className="text-xs text-slate-400 mt-1">
              The currency the declared values are in. The customs value itself is worked out from the order lines
              (goods only, excluding VAT and shipping).
            </p>
          </div>
        </div>
        <label className="flex items-center gap-2 text-sm text-slate-700 pt-2 border-t border-slate-100">
          <input
            type="checkbox"
            checked={dpdExtendedLiability}
            onChange={(e) => setDpdExtendedLiability(e.target.checked)}
          />
          Request DPD's extended liability cover on every shipment
        </label>
        <p className="text-xs text-slate-400 -mt-2 ml-6">
          A chargeable DPD insurance option - check with DPD whether this is already covered by your account terms
          before turning it on, since it isn't something to enable without knowing that. When on, the insured value
          sent is the order's goods value (capped at DPD's £5,000 maximum), not a separately maintained figure.
        </p>
        <p className="text-xs text-slate-400">
          A white-label/no-return-address label is a DPD account-level template setting, not something this
          integration can control - ask your DPD account manager to configure it if you need one.
        </p>
        <label className="flex items-center gap-2 text-sm text-slate-700 pt-2 border-t border-slate-100">
          <input type="checkbox" checked={printSampleLabels} onChange={(e) => setPrintSampleLabels(e.target.checked)} />
          Print a placeholder sample label at despatch when no DPD shipment could be booked
        </label>
        <p className="text-xs text-slate-400 -mt-2 ml-6">
          Turn this off once DPD is fully set up, so a despatch never accidentally prints an old test label instead
          of failing loudly - "Confirm Despatch" will simply not offer a label to print if DPD wasn't booked.
        </p>
      </SettingsSection>

      <SettingsSection title="Despatch & Packing">
        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input type="checkbox" checked={autoAcknowledge} onChange={(e) => setAutoAcknowledge(e.target.checked)} />
          Automatically send the acknowledgement email when an order is released for despatch
        </label>
        <p className="text-xs text-slate-400 -mt-2 ml-6">
          Removes the separate "Send Acknowledgement" step entirely for the normal case.
        </p>

        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input
            type="checkbox"
            checked={autoPrintPickingNote}
            onChange={(e) => setAutoPrintPickingNote(e.target.checked)}
          />
          Automatically print the picking note (to the picking note printer above) when an order is released for
          despatch
        </label>
        <p className="text-xs text-slate-400 -mt-2 ml-6">
          Removes the separate "Print Picking Note" step for the normal case, same as acknowledgement above.
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
        title="My Email"
        description="Just for you - acknowledgement and despatch confirmation emails you send go out through these, not the shared Settings > Email account. Leave any field blank to fall back to the shared account for that part."
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
          {myEmailSaved && <span className="text-xs text-emerald-600">Saved.</span>}
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

      {/*
        Floating rather than sitting at the bottom of the page, matching the
        order screen - with every section collapsible you can be anywhere in
        the page when you finish editing, and having to scroll to the end to
        find Save is exactly the annoyance this removes. Note this saves the
        shared/global settings only; the "My Email" and "Customisation"
        sections have their own Save buttons because they're per-user.
      */}
      <div className="fixed bottom-6 right-6 z-50 flex items-center gap-3">
        {saved && (
          <span className="text-sm text-emerald-700 bg-white border border-emerald-200 px-3 py-1.5 rounded-full shadow-sm">
            Saved.
          </span>
        )}
        <button
          onClick={() => saveMutation.mutate()}
          disabled={saveMutation.isPending}
          className="bg-emerald-600 text-white text-sm font-medium px-6 py-3 rounded-full shadow-lg hover:bg-emerald-500 disabled:opacity-50"
        >
          {saveMutation.isPending ? "Saving..." : "Save Settings"}
        </button>
      </div>

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

      <div className="bg-white border border-amber-200 rounded-lg p-5 mt-8">
        <h3 className="font-medium text-amber-700 mb-1">Company &amp; Contact Import</h3>
        <p className="text-sm text-slate-500 mb-3">
          Bulk create/update Companies and their Contacts from an OrderWise customer export - stays available for
          re-runs as OrderWise data changes, not just a one-off go-live tool.
        </p>
        <Link
          to="/settings/company-import"
          className="inline-block bg-slate-800 text-white text-sm px-4 py-2 rounded-md hover:bg-slate-700"
        >
          Open Company Import
        </Link>
      </div>

      <div className="bg-white border border-slate-200 rounded-lg p-5 mt-8">
        <h3 className="font-medium text-slate-800 mb-1">Backup &amp; Restore</h3>
        <p className="text-sm text-slate-500 mb-4">
          One file with everything needed to stand this system back up elsewhere: the full database (every order,
          product, stock item, ticket, company and setting) plus the connection secrets that live only in this
          server's environment and never touch the database - Postgres credentials, the SMTP account, and the
          Shopify app's Client ID/Secret.
        </p>

        <div className="mb-6">
          <button
            onClick={downloadBackup}
            disabled={backupDownloading}
            className="bg-slate-800 text-white text-sm font-medium px-4 py-2 rounded-md hover:bg-slate-700 disabled:opacity-50"
          >
            {backupDownloading ? "Building backup..." : "Download Full Backup"}
          </button>
          {backupError && <p className="text-sm text-red-600 mt-2">{backupError}</p>}
          <p className="text-xs text-slate-400 mt-2">
            To bring up a brand new machine from this file, copy this project onto it and run{" "}
            <code className="bg-slate-100 px-1 rounded">./restore.sh</code> with the downloaded .zip - it writes the
            secrets into a fresh <code className="bg-slate-100 px-1 rounded">.env</code>, brings up the database,
            and imports everything before starting the rest of the system. See{" "}
            <code className="bg-slate-100 px-1 rounded">docs/BNS_Warehouse_Setup_Guide.pdf</code>.
          </p>
        </div>

        <div className="border-t border-slate-100 pt-5">
          <h4 className="font-medium text-red-700 mb-1">Restore into THIS system</h4>
          <p className="text-sm text-slate-500 mb-3">
            Replaces every product, order, stock item, ticket, company and setting currently in this system's
            database with whatever is in the backup file - this cannot be undone. Only the database is restored
            this way; the backup's connection secrets are never applied automatically to an already-running
            container (Docker only reads <code className="bg-slate-100 px-1 rounded">.env</code> when a container
            starts) - copy them into this machine's own <code className="bg-slate-100 px-1 rounded">.env</code> by
            hand afterwards and run <code className="bg-slate-100 px-1 rounded">docker compose up --build</code> if
            this system also needs those.
          </p>
          <input
            type="file"
            accept=".zip"
            onChange={(e) => setRestoreFile(e.target.files?.[0] ?? null)}
            className="block text-sm mb-3"
          />
          <label className="block text-xs font-medium text-slate-500 mb-1">Type RESTORE to confirm</label>
          <div className="flex gap-3 items-center">
            <input
              value={restoreConfirmText}
              onChange={(e) => setRestoreConfirmText(e.target.value)}
              placeholder="RESTORE"
              className="border border-slate-300 rounded px-3 py-2 text-sm w-40"
            />
            <button
              onClick={() => restoreMutation.mutate()}
              disabled={restoreConfirmText !== "RESTORE" || !restoreFile || restoreMutation.isPending}
              className="bg-red-600 text-white text-sm px-4 py-2 rounded-md hover:bg-red-500 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {restoreMutation.isPending ? "Restoring..." : "Restore from Backup"}
            </button>
          </div>
          {restoreMutation.isError && (
            <p className="text-sm text-red-600 mt-2">{(restoreMutation.error as Error).message}</p>
          )}
          {restoreResult && (
            <div className="text-sm text-emerald-700 mt-3 bg-emerald-50 border border-emerald-200 rounded-md p-3">
              <p>
                Restored{restoreResult.backedUpAt ? ` a backup taken ${new Date(restoreResult.backedUpAt).toLocaleString()}` : ""}
                {restoreResult.backedUpFromDatabase ? ` (database "${restoreResult.backedUpFromDatabase}")` : ""}.
              </p>
              {restoreResult.secretKeysInBackup.length > 0 && (
                <p className="mt-1 text-emerald-800">
                  This backup also contains: {restoreResult.secretKeysInBackup.join(", ")} - copy those into this
                  machine's <code className="bg-emerald-100 px-1 rounded">.env</code> and restart if this system
                  needs them too (see above).
                </p>
              )}
            </div>
          )}
        </div>
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
              "Reset Stock & Purchase Orders" above first for the cleanest result.
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
