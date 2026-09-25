import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { api } from "../api/client";
import { CompanyRequest, CompanyView, InvoiceGrouping, TicketSummaryView } from "../types";
import { useToast } from "../components/ToastContext";
import { talkTimeLabel, TICKET_STATUS_LABEL, TICKET_STATUS_STYLE } from "./Tickets";

function CompanyRow({ company }: { company: CompanyView }) {
  const { showToast } = useToast();
  const navigate = useNavigate();
  const [editing, setEditing] = useState(false);
  const [showTickets, setShowTickets] = useState(false);
  const queryClient = useQueryClient();
  const [name, setName] = useState(company.name);
  const [creditLimit, setCreditLimit] = useState(company.creditLimit != null ? String(company.creditLimit) : "");
  const [shopifyCompanyId, setShopifyCompanyId] = useState(company.shopifyCompanyId ?? "");
  const [notes, setNotes] = useState(company.notes ?? "");
  const [eoriNumber, setEoriNumber] = useState(company.eoriNumber ?? "");
  const [vatNumber, setVatNumber] = useState(company.vatNumber ?? "");
  const [accountNumber, setAccountNumber] = useState(company.accountNumber ?? "");
  const [onHold, setOnHold] = useState(company.onHold);
  const [doNotUse, setDoNotUse] = useState(company.doNotUse);
  const [gaps, setGaps] = useState(company.gaps);
  const [gdms, setGdms] = useState(company.gdms);
  const [invoiceEmail, setInvoiceEmail] = useState(company.invoiceEmail ?? "");
  const [companyVatRate, setCompanyVatRate] = useState(company.vatRate != null ? String(company.vatRate) : "");
  const [invoiceGrouping, setInvoiceGrouping] = useState<InvoiceGrouping>(company.invoiceGrouping);
  const [paymentTermsDays, setPaymentTermsDays] = useState(
    company.paymentTermsDays != null ? String(company.paymentTermsDays) : ""
  );
  const [autoHoldOnOverdue, setAutoHoldOnOverdue] = useState(company.autoHoldOnOverdue);
  const [error, setError] = useState<string | null>(null);

  const updateMutation = useMutation({
    mutationFn: async () => {
      const body: CompanyRequest = {
        name,
        creditLimit: creditLimit ? Number(creditLimit) : undefined,
        shopifyCompanyId: shopifyCompanyId || undefined,
        notes: notes || undefined,
        eoriNumber: eoriNumber || undefined,
        vatNumber: vatNumber || undefined,
        accountNumber: accountNumber || undefined,
        onHold,
        doNotUse,
        gaps,
        gdms,
        invoiceEmail: invoiceEmail || undefined,
        vatRate: companyVatRate ? Number(companyVatRate) : undefined,
        invoiceGrouping,
        paymentTermsDays: paymentTermsDays ? Number(paymentTermsDays) : undefined,
        autoHoldOnOverdue,
      };
      return api.put(`/companies/${company.id}`, body);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["companies"] });
      setEditing(false);
      setError(null);
      showToast("Saved.");
    },
    onError: (err: Error) => setError(err.message),
  });

  const deleteMutation = useMutation({
    mutationFn: async () => api.delete(`/companies/${company.id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["companies"] });
      showToast("Deleted.");
    },
    // Shown as a page-level error rather than inline - the row itself is
    // about to disappear on success, so there's nowhere inline left to put it
    // once this fails and the row is still here.
    onError: (err: Error) => setError(err.message),
  });

  // Only fetched once expanded - a plain "how many tickets does this company
  // have" count for every row up front would mean one query per company on
  // every page load for no reason most of the time.
  const { data: tickets, isLoading: ticketsLoading } = useQuery({
    queryKey: ["tickets-by-company", company.id],
    queryFn: async () => (await api.get<TicketSummaryView[]>(`/tickets/by-company/${company.id}`)).data,
    enabled: showTickets,
  });

  return (
    <div className="px-4 py-3 hover:bg-slate-50">
      <div className="flex justify-between items-center">
        <div>
          <p className="font-medium text-slate-800">
            {company.name}
            {company.onHold && (
              <span className="ml-2 text-xs px-2 py-0.5 rounded-full border bg-amber-50 text-amber-700 border-amber-200">
                On Hold
              </span>
            )}
            {company.doNotUse && (
              <span className="ml-2 text-xs px-2 py-0.5 rounded-full border bg-red-50 text-red-700 border-red-200">
                Do Not Use
              </span>
            )}
          </p>
          {company.creditLimit != null ? (
            <p className={`text-sm ${company.overLimit ? "text-red-600 font-medium" : "text-slate-500"}`}>
              {company.creditUsed?.toFixed(2)} used of {company.creditLimit.toFixed(2)} limit (
              {company.creditAvailable?.toFixed(2)} available)
              {company.overLimit && " - OVER LIMIT"}
            </p>
          ) : (
            <p className="text-sm text-slate-400">No credit account</p>
          )}
          {company.eoriNumber && <p className="text-xs text-slate-400">EORI: {company.eoriNumber}</p>}
          <p className="text-xs text-slate-400">
            {company.invoiceEmail ? `Invoices to: ${company.invoiceEmail}` : "No invoice email set"}
            {company.invoiceGrouping === "CONSOLIDATED" && " · Consolidated invoicing"}
          </p>
          <p className="text-xs text-slate-400">
            Payment terms: {company.paymentTermsDays ?? "default"} days
            {company.autoHoldOnOverdue && " · Auto-hold on overdue"}
            {company.autoHeld && (
              <span className="ml-1 text-amber-600 font-medium">(currently auto-held)</span>
            )}
            {company.creditBalance > 0 && (
              <span className="ml-1 text-emerald-600 font-medium">
                · £{company.creditBalance.toFixed(2)} credit balance
              </span>
            )}
          </p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={() => navigate(`/contacts?companyId=${company.id}`)}
            className="text-xs text-slate-500 hover:text-slate-800 border border-slate-300 rounded px-2 py-1"
          >
            Contacts
          </button>
          <button
            onClick={() => setShowTickets((v) => !v)}
            className="text-xs text-slate-500 hover:text-slate-800 border border-slate-300 rounded px-2 py-1"
          >
            {showTickets ? "Hide Tickets" : "Tickets"}
          </button>
          <button
            onClick={() => setEditing((v) => !v)}
            className="text-xs text-slate-500 hover:text-slate-800 border border-slate-300 rounded px-2 py-1"
          >
            {editing ? "Cancel" : "Edit"}
          </button>
          <button
            onClick={() => {
              if (
                confirm(
                  `Delete ${company.name}? This can't be undone. Blocked if it still has orders or support tickets linked.`
                )
              ) {
                deleteMutation.mutate();
              }
            }}
            disabled={deleteMutation.isPending}
            className="text-xs text-red-500 hover:text-red-700 border border-red-200 rounded px-2 py-1 disabled:opacity-50"
          >
            {deleteMutation.isPending ? "Deleting..." : "Delete"}
          </button>
        </div>
      </div>

      {error && <p className="text-sm text-red-600 mt-2">{error}</p>}

      {showTickets && (
        <div className="mt-3 pt-3 border-t border-slate-100">
          {ticketsLoading && <p className="text-sm text-slate-400">Loading tickets...</p>}
          {!ticketsLoading && tickets?.length === 0 && <p className="text-sm text-slate-400">No tickets for this company.</p>}
          {!ticketsLoading && tickets && tickets.length > 0 && (
            <table className="w-full text-sm">
              <thead className="text-left text-slate-500">
                <tr>
                  <th className="py-1 pr-4">Ticket #</th>
                  <th className="py-1 pr-4">Title</th>
                  <th className="py-1 pr-4">Order</th>
                  <th className="py-1 pr-4">Status</th>
                  <th className="py-1 pr-4">Talk time</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {tickets.map((t) => (
                  <tr key={t.id} onClick={() => navigate(`/tickets/${t.id}`)} className="cursor-pointer hover:bg-slate-50">
                    <td className="py-1.5 pr-4 font-medium text-slate-800">{t.ticketNumber}</td>
                    <td className="py-1.5 pr-4">{t.title}</td>
                    <td className="py-1.5 pr-4">{t.orderNumber ?? "-"}</td>
                    <td className="py-1.5 pr-4">
                      <span className={`text-xs px-2 py-0.5 rounded-full border ${TICKET_STATUS_STYLE[t.status]}`}>
                        {TICKET_STATUS_LABEL[t.status]}
                      </span>
                    </td>
                    <td className="py-1.5 pr-4">{talkTimeLabel(t.talkTimeMinutes)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      {editing && (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            updateMutation.mutate();
          }}
          className="grid grid-cols-2 md:grid-cols-4 gap-3 items-end mt-3 pt-3 border-t border-slate-100"
        >
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Name</label>
            <input required value={name} onChange={(e) => setName(e.target.value)} className="input" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Credit Limit</label>
            <input
              type="number"
              step="0.01"
              min={0}
              value={creditLimit}
              onChange={(e) => setCreditLimit(e.target.value)}
              placeholder="No credit account"
              className="input"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Shopify Company ID</label>
            <input value={shopifyCompanyId} onChange={(e) => setShopifyCompanyId(e.target.value)} className="input" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Notes</label>
            <input value={notes} onChange={(e) => setNotes(e.target.value)} className="input" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">EORI Number</label>
            <input
              value={eoriNumber}
              onChange={(e) => setEoriNumber(e.target.value)}
              placeholder="e.g. IE1234567A"
              className="input"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">VAT Number</label>
            <input value={vatNumber} onChange={(e) => setVatNumber(e.target.value)} className="input" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Account Number</label>
            <input
              value={accountNumber}
              onChange={(e) => setAccountNumber(e.target.value)}
              placeholder="OrderWise account code"
              className="input"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Invoice Email</label>
            <input
              type="email"
              value={invoiceEmail}
              onChange={(e) => setInvoiceEmail(e.target.value)}
              placeholder="Where generated invoices are sent"
              className="input"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">VAT Rate Override (%)</label>
            <input
              type="number"
              step="0.01"
              min={0}
              value={companyVatRate}
              onChange={(e) => setCompanyVatRate(e.target.value)}
              placeholder="Uses global default if blank"
              className="input"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Invoice Grouping</label>
            <select
              value={invoiceGrouping}
              onChange={(e) => setInvoiceGrouping(e.target.value as InvoiceGrouping)}
              className="input"
            >
              <option value="PER_ORDER">One invoice per order</option>
              <option value="CONSOLIDATED">Consolidate same-day orders</option>
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Payment Terms (days)</label>
            <input
              type="number"
              min={0}
              value={paymentTermsDays}
              onChange={(e) => setPaymentTermsDays(e.target.value)}
              placeholder="Uses global default if blank"
              className="input"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Credit Balance</label>
            <input
              disabled
              value={`£${company.creditBalance.toFixed(2)}`}
              className="input bg-slate-50 text-slate-500"
            />
          </div>
          <div className="col-span-2 md:col-span-4 flex flex-wrap gap-4 pt-1">
            <label className="flex items-center gap-2 text-sm text-slate-600">
              <input type="checkbox" checked={onHold} onChange={(e) => setOnHold(e.target.checked)} />
              On Hold (credit hold - stop processing new orders)
            </label>
            <label className="flex items-center gap-2 text-sm text-slate-600">
              <input type="checkbox" checked={doNotUse} onChange={(e) => setDoNotUse(e.target.checked)} />
              Do Not Use
            </label>
            <label className="flex items-center gap-2 text-sm text-slate-600">
              <input type="checkbox" checked={gaps} onChange={(e) => setGaps(e.target.checked)} />
              GAPS
            </label>
            <label className="flex items-center gap-2 text-sm text-slate-600">
              <input type="checkbox" checked={gdms} onChange={(e) => setGdms(e.target.checked)} />
              GDMS
            </label>
            <label className="flex items-center gap-2 text-sm text-slate-600">
              <input
                type="checkbox"
                checked={autoHoldOnOverdue}
                onChange={(e) => setAutoHoldOnOverdue(e.target.checked)}
              />
              Auto-hold when overdue on payment terms
            </label>
          </div>
          <button
            type="submit"
            disabled={updateMutation.isPending}
            className="bg-emerald-600 text-white text-sm px-4 py-2 rounded-md hover:bg-emerald-500 disabled:opacity-50"
          >
            {updateMutation.isPending ? "Saving..." : "Save Changes"}
          </button>
        </form>
      )}
    </div>
  );
}

export default function Companies() {
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const [showForm, setShowForm] = useState(false);
  const [name, setName] = useState("");
  const [creditLimit, setCreditLimit] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [onHoldFilter, setOnHoldFilter] = useState(false);
  const [doNotUseFilter, setDoNotUseFilter] = useState(false);
  const [search, setSearch] = useState("");

  const { data: companies, isLoading } = useQuery({
    queryKey: ["companies"],
    queryFn: async () => (await api.get<CompanyView[]>("/companies")).data,
  });

  const filteredCompanies = useMemo(() => {
    if (!companies) return companies;
    const term = search.trim().toLowerCase();
    return companies.filter((c) => {
      if (onHoldFilter && !c.onHold) return false;
      if (doNotUseFilter && !c.doNotUse) return false;
      if (!term) return true;
      return [c.name, c.accountNumber, c.shopifyCompanyId, c.eoriNumber, c.vatNumber]
        .filter(Boolean)
        .some((v) => v!.toLowerCase().includes(term));
    });
  }, [companies, search, onHoldFilter, doNotUseFilter]);

  const createMutation = useMutation({
    mutationFn: async () => {
      const body: CompanyRequest = { name, creditLimit: creditLimit ? Number(creditLimit) : undefined };
      return api.post("/companies", body);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["companies"] });
      setName("");
      setCreditLimit("");
      setShowForm(false);
      setError(null);
      showToast("Created.");
    },
    onError: (err: Error) => setError(err.message),
  });

  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <div>
          <h2 className="text-2xl font-semibold text-slate-800">Companies</h2>
          <p className="text-slate-500">B2B accounts - link an order to one to enable credit checking on release.</p>
        </div>
        <button
          onClick={() => setShowForm((v) => !v)}
          className="bg-slate-800 text-white text-sm px-4 py-2 rounded-md hover:bg-slate-700"
        >
          {showForm ? "Cancel" : "New Company"}
        </button>
      </div>

      {showForm && (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            createMutation.mutate();
          }}
          className="bg-white border border-slate-200 rounded-lg p-5 mb-6 grid grid-cols-3 gap-3 items-end"
        >
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Name</label>
            <input required value={name} onChange={(e) => setName(e.target.value)} className="input" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Credit Limit (leave blank for none)</label>
            <input
              type="number"
              step="0.01"
              min={0}
              value={creditLimit}
              onChange={(e) => setCreditLimit(e.target.value)}
              className="input"
            />
          </div>
          <button
            type="submit"
            disabled={createMutation.isPending}
            className="bg-emerald-600 text-white text-sm px-4 py-2 rounded-md hover:bg-emerald-500 disabled:opacity-50"
          >
            {createMutation.isPending ? "Saving..." : "Save"}
          </button>
          {error && <p className="col-span-3 text-sm text-red-600">{error}</p>}
        </form>
      )}

      <div className="flex gap-4 mb-4 flex-wrap items-center">
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search name, account number, EORI/VAT..."
          className="input flex-1 min-w-[240px]"
        />
        <label className="flex items-center gap-2 text-sm text-slate-600">
          <input type="checkbox" checked={onHoldFilter} onChange={(e) => setOnHoldFilter(e.target.checked)} />
          On Hold only
        </label>
        <label className="flex items-center gap-2 text-sm text-slate-600">
          <input type="checkbox" checked={doNotUseFilter} onChange={(e) => setDoNotUseFilter(e.target.checked)} />
          Do Not Use only
        </label>
      </div>

      <div className="bg-white rounded-lg shadow-sm border border-slate-200 divide-y divide-slate-100 overflow-hidden">
        {isLoading && <p className="px-4 py-4 text-slate-400 text-sm">Loading...</p>}
        {!isLoading && filteredCompanies?.length === 0 && (
          <p className="px-4 py-4 text-slate-400 text-sm">No companies match.</p>
        )}
        {filteredCompanies?.map((c) => (
          <CompanyRow key={c.id} company={c} />
        ))}
      </div>
    </div>
  );
}
