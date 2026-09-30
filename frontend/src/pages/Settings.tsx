import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { api } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { useToast } from "../components/ToastContext";
import SettingsSection from "../components/SettingsSection";
import SavedBadge from "../components/SavedBadge";
import { CollectionCourierOption, GdmsRunResult } from "../types";

interface UserView {
  id: number;
  name: string;
  email: string | null;
}

interface RestoreResult {
  success: boolean;
  backedUpAt: string | null;
  backedUpFromDatabase: string | null;
  secretKeysInBackup: string[];
}

export default function Settings() {
  const [printerName, setPrinterName] = useState("");
  const [labelPrinterName, setLabelPrinterName] = useState("");
  const [labelPrinterDpi, setLabelPrinterDpi] = useState("203");
  const [apcLabelPrinterName, setApcLabelPrinterName] = useState("");
  const [printAgentUrl, setPrintAgentUrl] = useState("");
  const [smtpHost, setSmtpHost] = useState("");
  const [smtpPort, setSmtpPort] = useState("587");
  const [smtpUsername, setSmtpUsername] = useState("");
  const [smtpPassword, setSmtpPassword] = useState("");
  const [mailFromAddress, setMailFromAddress] = useState("");
  const [appPublicUrl, setAppPublicUrl] = useState("");
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
  const [apcEnvironment, setApcEnvironment] = useState<"training" | "live">("training");
  const [apcEmail, setApcEmail] = useState("");
  const [apcPassword, setApcPassword] = useState("");
  const [apcAccountNumber, setApcAccountNumber] = useState("");
  const [apcDefaultProductCode, setApcDefaultProductCode] = useState("");
  const [apcGoodsDescription, setApcGoodsDescription] = useState("");
  // Optional override collection address - blank (the default) means "use
  // APC's own account default depot", which is what BNS wants day to day
  // since BNS is the collection point, not the customer. See
  // ApcShippingService.buildRequestBody.
  const [apcCollectionOrganisation, setApcCollectionOrganisation] = useState("");
  const [apcCollectionStreet, setApcCollectionStreet] = useState("");
  const [apcCollectionPostcode, setApcCollectionPostcode] = useState("");
  const [apcCollectionCity, setApcCollectionCity] = useState("");
  const [apcCollectionCountryCode, setApcCollectionCountryCode] = useState("GB");
  const [apcCollectionContactName, setApcCollectionContactName] = useState("");
  const [apcCollectionContactPhone, setApcCollectionContactPhone] = useState("");
  const [gdmsRegion, setGdmsRegion] = useState<"eu" | "us">("eu");
  const [gdmsClientId, setGdmsClientId] = useState("");
  const [gdmsClientSecret, setGdmsClientSecret] = useState("");
  const [gdmsUsername, setGdmsUsername] = useState("");
  const [gdmsPassword, setGdmsPassword] = useState("");
  const [gdmsRunResult, setGdmsRunResult] = useState<GdmsRunResult | null>(null);
  const [gdmsRunError, setGdmsRunError] = useState<string | null>(null);
  // Defaults to today (yyyy-mm-dd, matching a plain <input type="date">) -
  // only changed when backfilling a day GDMS was down or a batch shipped
  // before Grandstream had assigned it to our channel yet.
  const [gdmsRunDate, setGdmsRunDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [printSampleLabels, setPrintSampleLabels] = useState(true);
  const [autoAcknowledge, setAutoAcknowledge] = useState(true);
  const [autoPrintPickingNote, setAutoPrintPickingNote] = useState(true);
  const [packingMode, setPackingMode] = useState<"SPLIT" | "SERIAL">("SPLIT");
  const [nonFaultyReturnDays, setNonFaultyReturnDays] = useState("28");
  const [faultyWarrantyDays, setFaultyWarrantyDays] = useState("365");
  const [vatRate, setVatRate] = useState("20");
  const [nextInvoiceNumber, setNextInvoiceNumber] = useState("");
  const [invoiceTerms, setInvoiceTerms] = useState("30 days from invoice date");
  const [invoiceCompanyRegNumber, setInvoiceCompanyRegNumber] = useState("");
  const [invoiceCompanyEmail, setInvoiceCompanyEmail] = useState("");
  const [invoiceBankAccountName, setInvoiceBankAccountName] = useState("");
  const [invoiceBankAccountNumber, setInvoiceBankAccountNumber] = useState("");
  const [invoiceBankSortCode, setInvoiceBankSortCode] = useState("");
  const [invoiceBankIban, setInvoiceBankIban] = useState("");
  const [invoiceBankSwiftBic, setInvoiceBankSwiftBic] = useState("");
  const [paymentTermsDays, setPaymentTermsDays] = useState("30");
  const [paymentTermsWarningDays, setPaymentTermsWarningDays] = useState("5");
  const [chaserWarningSubject, setChaserWarningSubject] = useState("");
  const [chaserWarningBody, setChaserWarningBody] = useState("");
  const [chaserOverdueSubject, setChaserOverdueSubject] = useState("");
  const [chaserOverdueBody, setChaserOverdueBody] = useState("");
  const [saved, setSaved] = useState(false);
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const { showToast } = useToast();

  const { data: settings } = useQuery({
    queryKey: ["settings"],
    queryFn: async () => (await api.get<Record<string, string>>("/settings")).data,
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
    setApcLabelPrinterName(settings["apc_label_printer"] ?? "");
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
    setAppPublicUrl(settings["app_public_url"] ?? "");
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
    setApcEnvironment((settings["apc_environment"] as "training" | "live") ?? "training");
    setApcEmail(settings["apc_email"] ?? "");
    // apc_password deliberately never populated back, same reasoning as
    // smtp_password/dpd_api_secret above.
    setApcAccountNumber(settings["apc_account_number"] ?? "");
    setApcDefaultProductCode(settings["apc_default_product_code"] ?? "");
    setApcGoodsDescription(settings["apc_goods_description"] ?? "Telecoms and networking equipment");
    setApcCollectionOrganisation(settings["apc_collection_organisation"] ?? "");
    setApcCollectionStreet(settings["apc_collection_street"] ?? "");
    setApcCollectionPostcode(settings["apc_collection_postcode"] ?? "");
    setApcCollectionCity(settings["apc_collection_city"] ?? "");
    setApcCollectionCountryCode(settings["apc_collection_country_code"] ?? "GB");
    setApcCollectionContactName(settings["apc_collection_contact_name"] ?? "");
    setApcCollectionContactPhone(settings["apc_collection_contact_phone"] ?? "");
    setGdmsRegion((settings["gdms_region"] as "eu" | "us") ?? "eu");
    setGdmsClientId(settings["gdms_client_id"] ?? "");
    // gdms_client_secret/gdms_password deliberately never populated back,
    // same reasoning as smtp_password/dpd_api_secret above.
    setGdmsUsername(settings["gdms_username"] ?? "");
    setPrintSampleLabels((settings["print_sample_labels"] ?? "true") === "true");
    setAutoAcknowledge((settings["auto_acknowledge_on_release"] ?? "true") === "true");
    setAutoPrintPickingNote((settings["auto_print_picking_note_on_release"] ?? "true") === "true");
    setPackingMode((settings["packing_mode"] as "SPLIT" | "SERIAL") ?? "SPLIT");
    setNonFaultyReturnDays(settings["rma_non_faulty_return_days"] ?? "28");
    setFaultyWarrantyDays(settings["rma_faulty_warranty_days"] ?? "365");
    setVatRate(settings["vat_rate"] ?? "20");
    setNextInvoiceNumber(settings["next_invoice_number"] ?? "");
    setInvoiceTerms(settings["invoice_terms"] ?? "30 days from invoice date");
    setInvoiceCompanyRegNumber(settings["invoice_company_reg_number"] ?? "");
    setInvoiceCompanyEmail(settings["invoice_company_email"] ?? "");
    setInvoiceBankAccountName(settings["invoice_bank_account_name"] ?? "");
    setInvoiceBankAccountNumber(settings["invoice_bank_account_number"] ?? "");
    setInvoiceBankSortCode(settings["invoice_bank_sort_code"] ?? "");
    setInvoiceBankIban(settings["invoice_bank_iban"] ?? "");
    setInvoiceBankSwiftBic(settings["invoice_bank_swift_bic"] ?? "");
    setPaymentTermsDays(settings["payment_terms_days"] ?? "30");
    setPaymentTermsWarningDays(settings["payment_terms_warning_days"] ?? "5");
    setChaserWarningSubject(settings["chaser_warning_subject"] ?? "");
    setChaserWarningBody(settings["chaser_warning_body"] ?? "");
    setChaserOverdueSubject(settings["chaser_overdue_subject"] ?? "");
    setChaserOverdueBody(settings["chaser_overdue_body"] ?? "");
  }, [settings]);

  const resetDpdConnectionMutation = useMutation({
    mutationFn: async () => api.post("/settings/dpd/reset-connection"),
    onSuccess: () =>
      showToast("DPD connection reset - the next DPD action will log in fresh and get a new bearer token."),
    onError: () => showToast("Failed to reset the DPD connection - see the console for details."),
  });

  const resetGdmsConnectionMutation = useMutation({
    mutationFn: async () => api.post("/settings/gdms/reset-connection"),
    onSuccess: () =>
      showToast("GDMS connection reset - the next GDMS action will log in fresh and get a new access token."),
    onError: () => showToast("Failed to reset the GDMS connection - see the console for details."),
  });

  // Collection courier list (Settings > Couriers > Collection Services) -
  // each row saves immediately on click (add/toggle/delete), rather than
  // waiting for this page's big Save button, since it's managing a separate
  // list resource (CollectionCourierOption rows), not settings-map
  // key/values like everything else on this page.
  const { data: collectionCouriers } = useQuery({
    queryKey: ["collection-couriers-admin"],
    queryFn: async () => (await api.get<CollectionCourierOption[]>("/settings/collection-couriers")).data,
  });
  const [newCollectionCourierName, setNewCollectionCourierName] = useState("");
  const addCollectionCourierMutation = useMutation({
    mutationFn: async () =>
      api.post("/settings/collection-couriers", {
        name: newCollectionCourierName.trim(),
        active: true,
        sortOrder: (collectionCouriers?.length ?? 0) * 10,
      }),
    onSuccess: () => {
      setNewCollectionCourierName("");
      queryClient.invalidateQueries({ queryKey: ["collection-couriers-admin"] });
      queryClient.invalidateQueries({ queryKey: ["collection-courier-options"] });
    },
    onError: (err: Error) => showToast(err.message),
  });
  const toggleCollectionCourierMutation = useMutation({
    mutationFn: async (option: CollectionCourierOption) =>
      api.put(`/settings/collection-couriers/${option.id}`, {
        name: option.name,
        active: !option.active,
        sortOrder: option.sortOrder,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["collection-couriers-admin"] });
      queryClient.invalidateQueries({ queryKey: ["collection-courier-options"] });
    },
    onError: (err: Error) => showToast(err.message),
  });
  const deleteCollectionCourierMutation = useMutation({
    mutationFn: async (id: number) => api.delete(`/settings/collection-couriers/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["collection-couriers-admin"] });
      queryClient.invalidateQueries({ queryKey: ["collection-courier-options"] });
    },
    onError: (err: Error) => showToast(err.message),
  });

  const runGdmsEndOfDayMutation = useMutation({
    mutationFn: async () =>
      (await api.post<GdmsRunResult>("/settings/gdms/run-end-of-day", null, { params: { date: gdmsRunDate } })).data,
    onMutate: () => {
      setGdmsRunResult(null);
      setGdmsRunError(null);
    },
    onSuccess: (result) => setGdmsRunResult(result),
    onError: (err: Error) => setGdmsRunError(err.message),
  });

  const saveMutation = useMutation({
    mutationFn: async () =>
      api.put("/settings", {
        picking_note_printer: printerName,
        label_printer: labelPrinterName,
        dpd_label_printer_dpi: labelPrinterDpi,
        apc_label_printer: apcLabelPrinterName,
        print_agent_url: printAgentUrl,
        smtp_host: smtpHost,
        smtp_port: smtpPort,
        smtp_username: smtpUsername,
        // Only included when actually typed - omitting it entirely (rather
        // than sending an empty string) means the existing stored password
        // is left untouched when saving any other setting on this page.
        ...(smtpPassword ? { smtp_password: smtpPassword } : {}),
        mail_from_address: mailFromAddress,
        app_public_url: appPublicUrl,
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
        apc_environment: apcEnvironment,
        apc_email: apcEmail,
        // Only included when actually typed - same reasoning as
        // smtp_password/dpd_api_secret above.
        ...(apcPassword ? { apc_password: apcPassword } : {}),
        apc_account_number: apcAccountNumber,
        apc_default_product_code: apcDefaultProductCode,
        apc_goods_description: apcGoodsDescription,
        apc_collection_organisation: apcCollectionOrganisation,
        apc_collection_street: apcCollectionStreet,
        apc_collection_postcode: apcCollectionPostcode,
        apc_collection_city: apcCollectionCity,
        apc_collection_country_code: apcCollectionCountryCode,
        apc_collection_contact_name: apcCollectionContactName,
        apc_collection_contact_phone: apcCollectionContactPhone,
        gdms_region: gdmsRegion,
        gdms_client_id: gdmsClientId,
        ...(gdmsClientSecret ? { gdms_client_secret: gdmsClientSecret } : {}),
        gdms_username: gdmsUsername,
        ...(gdmsPassword ? { gdms_password: gdmsPassword } : {}),
        print_sample_labels: String(printSampleLabels),
        auto_acknowledge_on_release: String(autoAcknowledge),
        auto_print_picking_note_on_release: String(autoPrintPickingNote),
        packing_mode: packingMode,
        rma_non_faulty_return_days: nonFaultyReturnDays,
        rma_faulty_warranty_days: faultyWarrantyDays,
        vat_rate: vatRate,
        next_invoice_number: nextInvoiceNumber,
        invoice_terms: invoiceTerms,
        invoice_company_reg_number: invoiceCompanyRegNumber,
        invoice_company_email: invoiceCompanyEmail,
        invoice_bank_account_name: invoiceBankAccountName,
        invoice_bank_account_number: invoiceBankAccountNumber,
        invoice_bank_sort_code: invoiceBankSortCode,
        invoice_bank_iban: invoiceBankIban,
        invoice_bank_swift_bic: invoiceBankSwiftBic,
        payment_terms_days: paymentTermsDays,
        payment_terms_warning_days: paymentTermsWarningDays,
        chaser_warning_subject: chaserWarningSubject,
        chaser_warning_body: chaserWarningBody,
        chaser_overdue_subject: chaserOverdueSubject,
        chaser_overdue_body: chaserOverdueBody,
      }),
    onSuccess: () => {
      setSaved(true);
      setTimeout(() => setSaved(false), 2500);
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
  const [newUserEmail, setNewUserEmail] = useState("");
  const [newUserError, setNewUserError] = useState<string | null>(null);
  const createUserMutation = useMutation({
    mutationFn: async () =>
      api.post("/users", { name: newUserName, password: newUserPassword, email: newUserEmail || null }),
    onSuccess: () => {
      setNewUserName("");
      setNewUserPassword("");
      setNewUserEmail("");
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

  // Editing a login's email - separate from password change so the two
  // don't fight over the same inline-edit slot on the same row; a login
  // needs an email on file for "Forgot password?" (Login.tsx) to work at
  // all, and this is how an existing login gets one added, or changed.
  const [emailChangeUserId, setEmailChangeUserId] = useState<number | null>(null);
  const [newEmail, setNewEmail] = useState("");
  const [emailChangeError, setEmailChangeError] = useState<string | null>(null);
  const changeEmailMutation = useMutation({
    mutationFn: async () => api.put(`/users/${emailChangeUserId}/email`, { email: newEmail || null }),
    onSuccess: () => {
      setEmailChangeUserId(null);
      setNewEmail("");
      setEmailChangeError(null);
      queryClient.invalidateQueries({ queryKey: ["users"] });
    },
    onError: (err: Error) => setEmailChangeError(err.message),
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
            Without it, printing falls back to opening the PDF in a new tab instead. The shipping label printer
            (and its DPI, for DPD) is set separately per courier under Couriers below, since DPD and APC labels
            often need to go to two physically different label printers - this Print Agent URL is shared by all
            of them.
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
        <div>
          <label className="block text-xs font-medium text-slate-500 mb-1">
            Public URL (leave blank for a plain LAN deployment)
          </label>
          <input
            value={appPublicUrl}
            onChange={(e) => setAppPublicUrl(e.target.value)}
            placeholder="e.g. https://wms.yourcompany.co.uk"
            className="input"
          />
          <p className="text-xs text-slate-400 mt-1">
            Used to build the link in password-reset emails, and (v0.115+) the redirect sent to Shopify when
            connecting Shopify Sync below. If this system is reachable through a reverse proxy or tunnel (like
            Cloudflare Tunnel), set this to the address people actually use in their browser - otherwise these
            links/redirects can come out as plain http:// even when the real site is https://, or point at a port
            that isn't actually reachable from outside.
          </p>
        </div>
      </SettingsSection>

      <SettingsSection
        title="Couriers"
        description="Which couriers are available on the order screen's Despatch panel (No Courier / DPD / APC / Collection), and the settings each one needs."
      >
        <SettingsSection
          title="Collection Services"
          description="The options offered on the order screen when Courier = Collection - external couriers (a customer's own courier, a local courier, etc.) BNS neither books nor labels itself, just records which one was used. Each change here saves immediately, rather than waiting for the page's Save button below."
        >
          <div className="space-y-1.5 mb-3">
            {collectionCouriers?.map((option) => (
              <div key={option.id} className="flex items-center gap-3 text-sm bg-slate-50 border border-slate-200 rounded px-3 py-1.5">
                <span className={`flex-1 ${option.active ? "text-slate-700" : "text-slate-400 line-through"}`}>
                  {option.name}
                </span>
                <button
                  type="button"
                  onClick={() => toggleCollectionCourierMutation.mutate(option)}
                  disabled={toggleCollectionCourierMutation.isPending}
                  className="text-xs text-slate-500 hover:text-slate-800 disabled:opacity-50"
                >
                  {option.active ? "Deactivate" : "Activate"}
                </button>
                <button
                  type="button"
                  onClick={() => {
                    if (confirm(`Delete "${option.name}" from the Collection courier list? Historical orders keep their own copy of the name either way.`)) {
                      deleteCollectionCourierMutation.mutate(option.id);
                    }
                  }}
                  disabled={deleteCollectionCourierMutation.isPending}
                  className="text-xs text-red-600 hover:text-red-800 disabled:opacity-50"
                >
                  Delete
                </button>
              </div>
            ))}
            {collectionCouriers?.length === 0 && <p className="text-xs text-slate-400">No Collection couriers set up yet.</p>}
          </div>
          <div className="flex gap-2 items-end">
            <input
              value={newCollectionCourierName}
              onChange={(e) => setNewCollectionCourierName(e.target.value)}
              placeholder="e.g. Local Courier Ltd"
              className="input flex-1"
            />
            <button
              type="button"
              onClick={() => addCollectionCourierMutation.mutate()}
              disabled={addCollectionCourierMutation.isPending || !newCollectionCourierName.trim()}
              className="btn-secondary text-sm"
            >
              {addCollectionCourierMutation.isPending ? "Adding…" : "Add"}
            </button>
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
          <button
            type="button"
            onClick={() => resetDpdConnectionMutation.mutate()}
            disabled={resetDpdConnectionMutation.isPending}
            className="btn-secondary text-sm"
          >
            {resetDpdConnectionMutation.isPending ? "Resetting…" : "Reset DPD connection"}
          </button>
          <p className="text-xs text-slate-400 mt-1">
            Forces the next DPD action (booking a shipment, printing a label, etc.) to log in from scratch and get a
            brand new bearer token, instead of reusing the one currently cached here (normally valid 24h, refreshed
            automatically). This is what DPD support mean by "reset the connection" or "get a new bearer code" - the
            bearer token is separate from the API key/secret above, which are untouched by this and don't need
            re-entering.
          </p>
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

        <h4 className="text-sm font-medium text-slate-700 pt-2">Label printer</h4>
        <p className="text-xs text-slate-400 -mt-3">
          Kept separate from APC's label printer below so DPD and APC labels - which can be different sizes - can
          be sent to two different physical printers.
        </p>
        <div className="grid grid-cols-2 gap-4">
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
              DPD shipping labels print as raw ZPL straight to this printer via the print agent (Print Agent URL,
              under Printing above) - needs a ZPL-compatible thermal printer (e.g. Zebra) and pywin32 installed
              alongside the agent. See the print-agent README for setup.
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
              Must match the shipping label printer's actual resolution (check the printer or its datasheet) - a
              mismatch here can throw off barcode scaling on the printed label.
            </p>
          </div>
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
        <label className="flex items-center gap-2 text-sm text-slate-700 pt-2 border-t border-slate-100">
          <input type="checkbox" checked={printSampleLabels} onChange={(e) => setPrintSampleLabels(e.target.checked)} />
          Print a placeholder sample label at despatch when no DPD shipment could be booked
        </label>
        <p className="text-xs text-slate-400 -mt-2 ml-6">
          Turn this off once DPD is fully set up, so a despatch never accidentally prints an old test label instead
          of failing loudly - "Confirm Despatch" will simply not offer a label to print if DPD wasn't booked.
        </p>
        </SettingsSection>

        <SettingsSection
          title="APC"
          description="Login and default settings for creating APC Overnight (Hypaship) shipments and printing labels directly from an order. Unlike DPD, there's no separate API key - just the email/password for your APC Hypaship login."
        >
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Email</label>
            <input value={apcEmail} onChange={(e) => setApcEmail(e.target.value)} className="input" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">
              Password (leave blank to keep the current one)
            </label>
            <input
              type="password"
              value={apcPassword}
              onChange={(e) => setApcPassword(e.target.value)}
              placeholder="••••••••"
              className="input"
            />
          </div>
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Environment</label>
            <select
              value={apcEnvironment}
              onChange={(e) => setApcEnvironment(e.target.value as "training" | "live")}
              className="input w-48"
            >
              <option value="training">Training (testing)</option>
              <option value="live">Live</option>
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Account number (optional)</label>
            <input value={apcAccountNumber} onChange={(e) => setApcAccountNumber(e.target.value)} className="input" />
          </div>
        </div>
        <div>
          <label className="block text-xs font-medium text-slate-500 mb-1">Default product code (optional)</label>
          <input
            value={apcDefaultProductCode}
            onChange={(e) => setApcDefaultProductCode(e.target.value)}
            placeholder="e.g. ND16"
            className="input w-48"
          />
          <p className="text-xs text-slate-400 mt-1">
            Used when an order has no service picked on the order screen. Leave blank to let APC fall back to your
            account's own default product.
          </p>
        </div>
        <div>
          <label className="block text-xs font-medium text-slate-500 mb-1">Goods description</label>
          <input
            value={apcGoodsDescription}
            onChange={(e) => setApcGoodsDescription(e.target.value)}
            placeholder="e.g. Telecoms and networking equipment"
            className="input"
          />
        </div>

        <h4 className="text-sm font-medium text-slate-700 pt-2">Label printer</h4>
        <p className="text-xs text-slate-400 -mt-3">
          Separate from DPD's label printer above, so APC labels can go to a different physical printer if its
          label size differs from DPD's. There's no DPI setting here - APC's integration guide doesn't expose a
          DPI option, unlike DPD.
        </p>
        <div>
          <label className="block text-xs font-medium text-slate-500 mb-1">
            Shipping label printer (leave blank to use the PC's default printer)
          </label>
          <input
            value={apcLabelPrinterName}
            onChange={(e) => setApcLabelPrinterName(e.target.value)}
            placeholder="e.g. Despatch Label Printer 2"
            className="input"
          />
          <p className="text-xs text-slate-400 mt-1">
            APC shipping labels print as raw ZPL straight to this printer via the print agent (Print Agent URL,
            under Printing above).
          </p>
        </div>

        <h5 className="text-sm font-medium text-slate-700 pt-2">Collection address override (optional)</h5>
        <p className="text-xs text-slate-400 -mt-3">
          Leave every field below blank to use your APC account's own default collection depot - this is what BNS
          wants day to day, since BNS is the collection point, not the customer. Only fill these in if you need to
          override that on every shipment.
        </p>
        <div className="grid grid-cols-2 lg:grid-cols-3 gap-4">
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Organisation</label>
            <input
              value={apcCollectionOrganisation}
              onChange={(e) => setApcCollectionOrganisation(e.target.value)}
              placeholder="BNS Distribution"
              className="input"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Contact name</label>
            <input value={apcCollectionContactName} onChange={(e) => setApcCollectionContactName(e.target.value)} className="input" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Contact phone</label>
            <input value={apcCollectionContactPhone} onChange={(e) => setApcCollectionContactPhone(e.target.value)} className="input" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Address line 1</label>
            <input value={apcCollectionStreet} onChange={(e) => setApcCollectionStreet(e.target.value)} className="input" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">City</label>
            <input value={apcCollectionCity} onChange={(e) => setApcCollectionCity(e.target.value)} className="input" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Postcode</label>
            <input value={apcCollectionPostcode} onChange={(e) => setApcCollectionPostcode(e.target.value)} className="input" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Country code</label>
            <input
              value={apcCollectionCountryCode}
              onChange={(e) => setApcCollectionCountryCode(e.target.value.toUpperCase())}
              maxLength={2}
              className="input uppercase w-24"
            />
          </div>
        </div>
        </SettingsSection>
      </SettingsSection>

      <SettingsSection
        title="GDMS"
        description="Grandstream's GDMS API credentials, and the end-of-day process that assigns every device despatched to a GDMS-enabled customer to their GDMS channel automatically - runs on its own every day at 16:30, or can be run on demand here. Each company's channel is set on the Companies page."
      >
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Region</label>
            <select value={gdmsRegion} onChange={(e) => setGdmsRegion(e.target.value as "eu" | "us")} className="input w-48">
              <option value="eu">EU (eu.gdms.cloud)</option>
              <option value="us">US (www.gdms.cloud)</option>
            </select>
          </div>
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Client ID</label>
            <input value={gdmsClientId} onChange={(e) => setGdmsClientId(e.target.value)} className="input" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">
              Client secret (leave blank to keep the current one)
            </label>
            <input
              type="password"
              value={gdmsClientSecret}
              onChange={(e) => setGdmsClientSecret(e.target.value)}
              placeholder="••••••••"
              className="input"
            />
          </div>
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">GDMS username</label>
            <input value={gdmsUsername} onChange={(e) => setGdmsUsername(e.target.value)} className="input" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">
              GDMS password (leave blank to keep the current one)
            </label>
            <input
              type="password"
              value={gdmsPassword}
              onChange={(e) => setGdmsPassword(e.target.value)}
              placeholder="••••••••"
              className="input"
            />
          </div>
        </div>
        <div>
          <button
            type="button"
            onClick={() => resetGdmsConnectionMutation.mutate()}
            disabled={resetGdmsConnectionMutation.isPending}
            className="btn-secondary text-sm"
          >
            {resetGdmsConnectionMutation.isPending ? "Resetting…" : "Reset GDMS connection"}
          </button>
          <p className="text-xs text-slate-400 mt-1">
            Forces the next GDMS action to log in from scratch and get a brand new access token, instead of reusing
            the one currently cached here. The client ID/secret and username/password above are untouched by this.
          </p>
        </div>
        <div className="pt-2 border-t border-slate-100">
          <div className="flex items-end gap-2">
            <button
              type="button"
              onClick={() => runGdmsEndOfDayMutation.mutate()}
              disabled={runGdmsEndOfDayMutation.isPending}
              className="btn-secondary text-sm"
            >
              {runGdmsEndOfDayMutation.isPending ? "Running…" : "Run GDMS end-of-day now"}
            </button>
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1">For date</label>
              <input
                type="date"
                value={gdmsRunDate}
                onChange={(e) => setGdmsRunDate(e.target.value)}
                className="input"
              />
            </div>
          </div>
          <p className="text-xs text-slate-400 mt-1">
            Assigns every device despatched on the selected date to a GDMS-enabled customer (that hasn't already been
            synced) to that company's GDMS channel, right now - the same thing the 16:30 scheduled run does for
            today. Defaults to today; pick an earlier date to backfill a day GDMS was down or a batch shipped before
            Grandstream had assigned it to our channel yet. Safe to run more than once for the same date: anything
            already synced is skipped.
          </p>
          {gdmsRunResult && (
            <div className="mt-2 text-sm bg-slate-50 border border-slate-200 rounded p-3">
              <p className="text-slate-700">
                {gdmsRunResult.devicesAssigned} device(s) assigned across {gdmsRunResult.companiesProcessed} compan
                {gdmsRunResult.companiesProcessed === 1 ? "y" : "ies"}.
                {gdmsRunResult.companiesSkippedNoChannel > 0 &&
                  ` ${gdmsRunResult.companiesSkippedNoChannel} compan${
                    gdmsRunResult.companiesSkippedNoChannel === 1 ? "y" : "ies"
                  } skipped - GDMS-enabled but no channel set.`}
              </p>
              {gdmsRunResult.errors.length > 0 && (
                <ul className="mt-1 list-disc list-inside text-red-600">
                  {gdmsRunResult.errors.map((e, i) => (
                    <li key={i}>{e}</li>
                  ))}
                </ul>
              )}
            </div>
          )}
          {gdmsRunError && <p className="mt-2 text-sm text-red-600">{gdmsRunError}</p>}
        </div>
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
        title="Invoicing"
        description="Drives Generate Invoices - VAT, invoice numbering, and what appears on the generated PDF. A company's own VAT rate (Companies) overrides the default rate below for that company only."
      >
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Default VAT rate (%)</label>
            <input
              type="number"
              step="0.01"
              min={0}
              value={vatRate}
              onChange={(e) => setVatRate(e.target.value)}
              className="input"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Next invoice/credit note number</label>
            <input
              type="number"
              min={1}
              value={nextInvoiceNumber}
              onChange={(e) => setNextInvoiceNumber(e.target.value)}
              className="input"
            />
            <p className="text-xs text-slate-400 mt-1">
              One shared sequence for both invoices and credit notes, matching OrderWise. Advances by itself every
              time Generate Invoices runs - only change this by hand to realign with OrderWise's own numbering.
            </p>
          </div>
        </div>
        <div>
          <label className="block text-xs font-medium text-slate-500 mb-1">Payment terms (shown on the PDF)</label>
          <input value={invoiceTerms} onChange={(e) => setInvoiceTerms(e.target.value)} className="input" />
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Company registration number</label>
            <input
              value={invoiceCompanyRegNumber}
              onChange={(e) => setInvoiceCompanyRegNumber(e.target.value)}
              className="input"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Company email (shown on the PDF)</label>
            <input
              type="email"
              value={invoiceCompanyEmail}
              onChange={(e) => setInvoiceCompanyEmail(e.target.value)}
              className="input"
            />
          </div>
        </div>
        <p className="text-xs text-slate-400">
          The rest of the letterhead (business name/address/phone/VAT number) is already set under Settings &rarr;
          DPD &rarr; sender details, and is reused here rather than duplicated.
        </p>

        <div className="pt-3 border-t border-slate-100">
          <h4 className="text-sm font-medium text-slate-700 mb-2">Bank details (optional)</h4>
          <p className="text-xs text-slate-400 mb-3">
            Only shown on the PDF if a bank name is set below - leave blank to omit this box entirely rather than
            print something incomplete or wrong.
          </p>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1">Bank / account name</label>
              <input
                value={invoiceBankAccountName}
                onChange={(e) => setInvoiceBankAccountName(e.target.value)}
                placeholder="e.g. Revolut Ltd"
                className="input"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1">Account number</label>
              <input
                value={invoiceBankAccountNumber}
                onChange={(e) => setInvoiceBankAccountNumber(e.target.value)}
                className="input"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1">Sort code</label>
              <input
                value={invoiceBankSortCode}
                onChange={(e) => setInvoiceBankSortCode(e.target.value)}
                className="input"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1">Swift/BIC</label>
              <input
                value={invoiceBankSwiftBic}
                onChange={(e) => setInvoiceBankSwiftBic(e.target.value)}
                className="input"
              />
            </div>
            <div className="col-span-2">
              <label className="block text-xs font-medium text-slate-500 mb-1">IBAN</label>
              <input value={invoiceBankIban} onChange={(e) => setInvoiceBankIban(e.target.value)} className="input" />
            </div>
          </div>
        </div>
      </SettingsSection>

      <SettingsSection
        title="Payment Tracking"
        description="Default payment terms and the two chaser email templates - see Payment Tracking. A company's own Payment Terms (days) override (Companies) and Auto-hold on overdue setting take priority over the defaults below."
      >
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Default payment terms (days)</label>
            <input
              type="number"
              min={1}
              value={paymentTermsDays}
              onChange={(e) => setPaymentTermsDays(e.target.value)}
              className="input"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Warning window (days before terms)</label>
            <input
              type="number"
              min={0}
              value={paymentTermsWarningDays}
              onChange={(e) => setPaymentTermsWarningDays(e.target.value)}
              className="input"
            />
            <p className="text-xs text-slate-400 mt-1">
              The "close to payment terms" chaser goes out this many days before an invoice hits its terms.
            </p>
          </div>
        </div>

        <div className="pt-3 border-t border-slate-100">
          <h4 className="text-sm font-medium text-slate-700 mb-2">Warning chaser (sent once, at the warning window)</h4>
          <div className="space-y-3">
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1">Subject</label>
              <input value={chaserWarningSubject} onChange={(e) => setChaserWarningSubject(e.target.value)} className="input" />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1">Body</label>
              <textarea
                value={chaserWarningBody}
                onChange={(e) => setChaserWarningBody(e.target.value)}
                rows={6}
                className="input"
              />
            </div>
          </div>
        </div>

        <div className="pt-3 border-t border-slate-100">
          <h4 className="text-sm font-medium text-slate-700 mb-2">Overdue chaser (sent once, the day it hits terms)</h4>
          <div className="space-y-3">
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1">Subject</label>
              <input value={chaserOverdueSubject} onChange={(e) => setChaserOverdueSubject(e.target.value)} className="input" />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1">Body</label>
              <textarea
                value={chaserOverdueBody}
                onChange={(e) => setChaserOverdueBody(e.target.value)}
                rows={6}
                className="input"
              />
            </div>
          </div>
        </div>

        <p className="text-xs text-slate-400">
          Both templates support {"{invoiceNumber}"}, {"{companyName}"}, {"{amount}"}, {"{invoiceDate}"},{" "}
          {"{daysRemaining}"} (warning chaser only) and {"{daysOverdue}"} (overdue chaser only) placeholders, filled
          in automatically. Always sent to the company's Invoice Email (Companies) - never anywhere else.
        </p>
      </SettingsSection>

      <SettingsSection
        title="Users"
        description="Anyone with a login can manage other logins for now - there's no admin/staff distinction yet, matching how the rest of this app works."
      >
        <div className="space-y-2">
          {users?.map((u) => (
            <div key={u.id} className="border border-slate-100 rounded px-3 py-2">
              <div className="flex items-center justify-between gap-2">
                <div>
                  <span className="text-sm text-slate-700">
                    {u.name} {u.name === user?.name && <span className="text-xs text-emerald-600 ml-1">(you)</span>}
                  </span>
                  {emailChangeUserId !== u.id && (
                    <div className="text-xs text-slate-400 mt-0.5">
                      {u.email ?? "No email on file - \"Forgot password?\" won't work for this login"}
                    </div>
                  )}
                </div>
                <div className="flex items-center gap-2 flex-wrap justify-end">
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
                  ) : emailChangeUserId === u.id ? (
                    <div className="flex items-center gap-2">
                      <input
                        type="email"
                        autoFocus
                        value={newEmail}
                        onChange={(e) => setNewEmail(e.target.value)}
                        placeholder="name@example.com"
                        className="input w-52 text-sm"
                      />
                      <button
                        onClick={() => changeEmailMutation.mutate()}
                        disabled={changeEmailMutation.isPending}
                        className="text-xs bg-slate-800 text-white px-3 py-1.5 rounded hover:bg-slate-700 disabled:opacity-50"
                      >
                        Save
                      </button>
                      <button
                        onClick={() => {
                          setEmailChangeUserId(null);
                          setNewEmail("");
                          setEmailChangeError(null);
                        }}
                        className="text-xs text-slate-400 hover:text-slate-600"
                      >
                        Cancel
                      </button>
                    </div>
                  ) : (
                    <>
                      <button
                        onClick={() => {
                          setEmailChangeUserId(u.id);
                          setNewEmail(u.email ?? "");
                        }}
                        className="text-xs text-slate-500 hover:text-slate-700 border border-slate-300 rounded px-2 py-1"
                      >
                        {u.email ? "Edit email" : "Add email"}
                      </button>
                      <button
                        onClick={() => setPasswordChangeUserId(u.id)}
                        className="text-xs text-slate-500 hover:text-slate-700 border border-slate-300 rounded px-2 py-1"
                      >
                        Change password
                      </button>
                    </>
                  )}
                </div>
              </div>
              {emailChangeUserId === u.id && emailChangeError && (
                <p className="text-xs text-red-600 mt-1">{emailChangeError}</p>
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
            <div>
              <label className="block text-xs text-slate-400 mb-1">Email (optional)</label>
              <input
                type="email"
                value={newUserEmail}
                onChange={(e) => setNewUserEmail(e.target.value)}
                placeholder="name@example.com"
                className="input w-52"
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
          <p className="text-xs text-slate-400 mt-2">
            An email lets that login use "Forgot password?" on the sign-in page - it's not required, and can be
            added or changed later from here.
          </p>
          {newUserError && <p className="text-xs text-red-600 mt-2">{newUserError}</p>}
        </div>
      </SettingsSection>

      {/*
        Floating rather than sitting at the bottom of the page, matching the
        order screen - with every section collapsible you can be anywhere in
        the page when you finish editing, and having to scroll to the end to
        find Save is exactly the annoyance this removes.

        "My Email" and "Customisation" used to live here as their own
        per-user sections with their own Save buttons - moved to a dedicated
        Account Settings page in v0.107 (reached via the settings icon next
        to your name in the sidebar) so they're not mixed in among shared/
        global configuration.
      */}
      <div className="fixed bottom-6 right-6 z-50 flex items-center gap-3">
        <SavedBadge show={saved} />
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
