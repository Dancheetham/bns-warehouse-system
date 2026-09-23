import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useSearchParams } from "react-router-dom";
import { api } from "../api/client";
import { CompanyView, ContactRequest, ContactView, TicketSummaryView } from "../types";
import { useToast } from "../components/ToastContext";
import { talkTimeLabel, TICKET_STATUS_LABEL, TICKET_STATUS_STYLE } from "./Tickets";

function ContactRow({ contact, onCompanyClick }: { contact: ContactView; onCompanyClick?: () => void }) {
  const { showToast } = useToast();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState(false);
  const [showTickets, setShowTickets] = useState(false);
  const [name, setName] = useState(contact.name);
  const [email, setEmail] = useState(contact.email ?? "");
  const [phone, setPhone] = useState(contact.phone ?? "");
  const [position, setPosition] = useState(contact.position ?? "");
  const [mainContact, setMainContact] = useState(contact.mainContact);
  const [active, setActive] = useState(contact.active);
  const [error, setError] = useState<string | null>(null);

  const updateMutation = useMutation({
    mutationFn: async () => {
      const body: ContactRequest = {
        companyId: contact.companyId,
        name,
        email: email || undefined,
        phone: phone || undefined,
        position: position || undefined,
        mainContact,
        active,
      };
      return api.put(`/contacts/${contact.id}`, body);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["contacts"] });
      setEditing(false);
      setError(null);
      showToast("Saved.");
    },
    onError: (err: Error) => setError(err.message),
  });

  const deleteMutation = useMutation({
    mutationFn: async () => api.delete(`/contacts/${contact.id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["contacts"] });
      showToast("Deleted.");
    },
    onError: (err: Error) => setError(err.message),
  });

  const { data: tickets, isLoading: ticketsLoading } = useQuery({
    queryKey: ["tickets-by-contact", contact.id],
    queryFn: async () => (await api.get<TicketSummaryView[]>(`/tickets/by-contact/${contact.id}`)).data,
    enabled: showTickets,
  });

  return (
    <div className="px-4 py-3 hover:bg-slate-50">
      <div className="flex justify-between items-center">
        <div>
          <p className="font-medium text-slate-800">
            {contact.name}
            {contact.mainContact && (
              <span className="ml-2 text-xs px-2 py-0.5 rounded-full border bg-blue-50 text-blue-700 border-blue-200">
                Main contact
              </span>
            )}
            {!contact.active && (
              <span className="ml-2 text-xs px-2 py-0.5 rounded-full border bg-slate-100 text-slate-500 border-slate-200">
                Inactive
              </span>
            )}
          </p>
          <p className="text-sm text-slate-500">
            {onCompanyClick ? (
              <button onClick={onCompanyClick} className="hover:underline text-slate-500">
                {contact.companyName}
              </button>
            ) : (
              contact.companyName
            )}
            {contact.position && ` · ${contact.position}`}
          </p>
          <p className="text-xs text-slate-400">
            {contact.email ?? "No email"} {contact.phone && `· ${contact.phone}`}
          </p>
        </div>
        <div className="flex gap-2">
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
              if (confirm(`Delete ${contact.name}? This can't be undone.`)) {
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
          {!ticketsLoading && tickets?.length === 0 && <p className="text-sm text-slate-400">No tickets for this contact.</p>}
          {!ticketsLoading && tickets && tickets.length > 0 && (
            <table className="w-full text-sm">
              <thead className="text-left text-slate-500">
                <tr>
                  <th className="py-1 pr-4">Ticket #</th>
                  <th className="py-1 pr-4">Title</th>
                  <th className="py-1 pr-4">Status</th>
                  <th className="py-1 pr-4">Talk time</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {tickets.map((t) => (
                  <tr key={t.id} onClick={() => navigate(`/tickets/${t.id}`)} className="cursor-pointer hover:bg-slate-50">
                    <td className="py-1.5 pr-4 font-medium text-slate-800">{t.ticketNumber}</td>
                    <td className="py-1.5 pr-4">{t.title}</td>
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
          className="grid grid-cols-2 md:grid-cols-3 gap-3 items-end mt-3 pt-3 border-t border-slate-100"
        >
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Name</label>
            <input required value={name} onChange={(e) => setName(e.target.value)} className="input" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Email</label>
            <input value={email} onChange={(e) => setEmail(e.target.value)} className="input" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Phone</label>
            <input value={phone} onChange={(e) => setPhone(e.target.value)} className="input" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Position</label>
            <input value={position} onChange={(e) => setPosition(e.target.value)} className="input" />
          </div>
          <label className="flex items-center gap-2 text-sm text-slate-600">
            <input type="checkbox" checked={mainContact} onChange={(e) => setMainContact(e.target.checked)} />
            Main contact
          </label>
          <label className="flex items-center gap-2 text-sm text-slate-600">
            <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
            Active
          </label>
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

export default function Contacts() {
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const [searchParams, setSearchParams] = useSearchParams();
  const companyFilterId = searchParams.get("companyId") ? Number(searchParams.get("companyId")) : undefined;

  const [showForm, setShowForm] = useState(false);
  const [search, setSearch] = useState("");
  const [companyId, setCompanyId] = useState<number | undefined>(undefined);
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [position, setPosition] = useState("");
  const [error, setError] = useState<string | null>(null);

  const { data: contacts, isLoading } = useQuery({
    queryKey: ["contacts"],
    queryFn: async () => (await api.get<ContactView[]>("/contacts")).data,
  });

  const { data: companies } = useQuery({
    queryKey: ["companies"],
    queryFn: async () => (await api.get<CompanyView[]>("/companies")).data,
  });

  const companyFilterName = companyFilterId ? companies?.find((c) => c.id === companyFilterId)?.name : undefined;

  const createMutation = useMutation({
    mutationFn: async () => {
      const body: ContactRequest = {
        companyId: companyId ?? (companyFilterId as number),
        name,
        email: email || undefined,
        phone: phone || undefined,
        position: position || undefined,
      };
      return api.post("/contacts", body);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["contacts"] });
      setName("");
      setEmail("");
      setPhone("");
      setPosition("");
      setCompanyId(undefined);
      setShowForm(false);
      setError(null);
      showToast("Created.");
    },
    onError: (err: Error) => setError(err.message),
  });

  const filtered = useMemo(() => {
    if (!contacts) return [];
    const term = search.trim().toLowerCase();
    return contacts.filter((c) => {
      if (companyFilterId && c.companyId !== companyFilterId) return false;
      if (!term) return true;
      return [c.name, c.companyName, c.email, c.phone, c.position].filter(Boolean).some((v) => v!.toLowerCase().includes(term));
    });
  }, [contacts, search, companyFilterId]);

  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <div>
          <h2 className="text-2xl font-semibold text-slate-800">Contacts</h2>
          <p className="text-slate-500">
            People at a company - searchable and linkable when opening a support ticket.
          </p>
          {companyFilterName && (
            <p className="text-sm text-slate-600 mt-1">
              Filtered to <span className="font-medium">{companyFilterName}</span> ·{" "}
              <button
                onClick={() => setSearchParams({})}
                className="text-slate-400 hover:text-slate-700 underline"
              >
                clear
              </button>
            </p>
          )}
        </div>
        <button
          onClick={() => {
            setCompanyId(companyFilterId);
            setShowForm((v) => !v);
          }}
          className="bg-slate-800 text-white text-sm px-4 py-2 rounded-md hover:bg-slate-700"
        >
          {showForm ? "Cancel" : "New Contact"}
        </button>
      </div>

      {showForm && (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            createMutation.mutate();
          }}
          className="bg-white border border-slate-200 rounded-lg p-5 mb-6 grid grid-cols-2 md:grid-cols-3 gap-3 items-end"
        >
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Company</label>
            <select
              required
              value={companyId ?? ""}
              onChange={(e) => setCompanyId(e.target.value ? Number(e.target.value) : undefined)}
              className="input"
            >
              <option value="">Select a company...</option>
              {companies?.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Name</label>
            <input required value={name} onChange={(e) => setName(e.target.value)} className="input" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Email</label>
            <input value={email} onChange={(e) => setEmail(e.target.value)} className="input" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Phone</label>
            <input value={phone} onChange={(e) => setPhone(e.target.value)} className="input" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Position</label>
            <input value={position} onChange={(e) => setPosition(e.target.value)} className="input" />
          </div>
          <button
            type="submit"
            disabled={createMutation.isPending || !companyId}
            className="bg-emerald-600 text-white text-sm px-4 py-2 rounded-md hover:bg-emerald-500 disabled:opacity-50"
          >
            {createMutation.isPending ? "Saving..." : "Save"}
          </button>
          {error && <p className="col-span-3 text-sm text-red-600">{error}</p>}
        </form>
      )}

      {!showForm && (
        <div className="flex gap-3 mb-4 flex-wrap">
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search name, company, email, phone..."
            className="input flex-1 min-w-[240px]"
          />
        </div>
      )}

      <div className="bg-white rounded-lg shadow-sm border border-slate-200 divide-y divide-slate-100 overflow-hidden">
        {isLoading && <p className="px-4 py-4 text-slate-400 text-sm">Loading...</p>}
        {!isLoading && filtered.length === 0 && <p className="px-4 py-4 text-slate-400 text-sm">No contacts found.</p>}
        {filtered.map((c) => (
          <ContactRow
            key={c.id}
            contact={c}
            onCompanyClick={companyFilterId ? undefined : () => setSearchParams({ companyId: String(c.companyId) })}
          />
        ))}
      </div>
    </div>
  );
}
