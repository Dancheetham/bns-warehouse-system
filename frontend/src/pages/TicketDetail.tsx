import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { api } from "../api/client";
import { CompanyView, Order, TicketRequest, TicketStatus, TicketView } from "../types";
import { useToast } from "../components/ToastContext";
import { talkTimeLabel, TICKET_STATUSES, TICKET_STATUS_LABEL } from "./Tickets";

// Simple type-to-filter link picker (matches the client-side filtering
// SalesActivity already uses for its order list) rather than a server search
// endpoint - fine at this app's data volume, and keeps this consistent with
// how order/company lookups work everywhere else.
function LinkPicker<T>({
  label,
  items,
  getLabel,
  getSubLabel,
  onSelect,
  placeholder,
}: {
  label: string;
  items: T[];
  getLabel: (item: T) => string;
  getSubLabel?: (item: T) => string;
  onSelect: (item: T) => void;
  placeholder: string;
}) {
  const [term, setTerm] = useState("");
  const [open, setOpen] = useState(false);

  const matches = useMemo(() => {
    if (!term.trim()) return [];
    const t = term.toLowerCase();
    return items
      .filter((item) => getLabel(item).toLowerCase().includes(t) || getSubLabel?.(item)?.toLowerCase().includes(t))
      .slice(0, 8);
  }, [term, items, getLabel, getSubLabel]);

  return (
    <div className="relative">
      <label className="block text-xs text-slate-400 mb-1">{label}</label>
      <input
        value={term}
        onChange={(e) => {
          setTerm(e.target.value);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
        onBlur={() => setTimeout(() => setOpen(false), 150)}
        placeholder={placeholder}
        className="input"
      />
      {open && matches.length > 0 && (
        <div className="absolute z-10 mt-1 w-full bg-white border border-slate-200 rounded-md shadow-lg max-h-56 overflow-y-auto">
          {matches.map((item, i) => (
            <button
              type="button"
              key={i}
              onMouseDown={() => {
                onSelect(item);
                setTerm("");
                setOpen(false);
              }}
              className="w-full text-left px-3 py-2 text-sm hover:bg-slate-50 border-b border-slate-50 last:border-0"
            >
              <span className="font-medium text-slate-700">{getLabel(item)}</span>
              {getSubLabel && <span className="text-slate-400 ml-2">{getSubLabel(item)}</span>}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

export default function TicketDetail() {
  const { id } = useParams();
  const isNew = id === "new";
  const navigate = useNavigate();
  // Set when arriving via "+ Open a support ticket for this order" on the
  // order screen (/tickets/new?orderId=123) - pre-links that order (and its
  // company) rather than making the person search for it again.
  const [searchParams] = useSearchParams();
  const prefillOrderId = isNew ? searchParams.get("orderId") : null;
  const { showToast } = useToast();
  const queryClient = useQueryClient();

  const [title, setTitle] = useState("");
  const [callerName, setCallerName] = useState("");
  const [phone, setPhone] = useState("");
  const [email, setEmail] = useState("");
  const [status, setStatus] = useState<TicketStatus>("IN_PROGRESS");
  const [talkHours, setTalkHours] = useState("0");
  const [talkMinutes, setTalkMinutes] = useState("0");
  const [companyId, setCompanyId] = useState<number | undefined>(undefined);
  const [companyName, setCompanyName] = useState<string | undefined>(undefined);
  const [orderId, setOrderId] = useState<number | undefined>(undefined);
  const [orderNumber, setOrderNumber] = useState<string | undefined>(undefined);
  const [newNote, setNewNote] = useState("");
  const [error, setError] = useState<string | null>(null);

  const { data: existingTicket } = useQuery({
    queryKey: ["ticket", id],
    queryFn: async () => (await api.get<TicketView>(`/tickets/${id}`)).data,
    enabled: !isNew,
  });

  const { data: companies } = useQuery({
    queryKey: ["companies"],
    queryFn: async () => (await api.get<CompanyView[]>("/companies")).data,
  });

  const { data: orders } = useQuery({
    queryKey: ["orders"],
    queryFn: async () => (await api.get<Order[]>("/orders")).data,
  });

  useEffect(() => {
    if (prefillOrderId && orders && !orderId) {
      const match = orders.find((o) => String(o.id) === prefillOrderId);
      if (match) {
        setOrderId(match.id);
        setOrderNumber(match.orderNumber);
        setCallerName(match.customerName);
        setPhone(match.deliveryPhone ?? "");
        setEmail(match.customerEmail ?? "");
        if (match.company) {
          setCompanyId(match.company.id);
          setCompanyName(match.company.name);
        }
      }
    }
  }, [prefillOrderId, orders, orderId]);

  useEffect(() => {
    if (existingTicket) {
      setTitle(existingTicket.title);
      setCallerName(existingTicket.callerName ?? "");
      setPhone(existingTicket.phone ?? "");
      setEmail(existingTicket.email ?? "");
      setStatus(existingTicket.status);
      setTalkHours(String(Math.floor(existingTicket.talkTimeMinutes / 60)));
      setTalkMinutes(String(existingTicket.talkTimeMinutes % 60));
      setCompanyId(existingTicket.companyId);
      setCompanyName(existingTicket.companyName);
      setOrderId(existingTicket.orderId);
      setOrderNumber(existingTicket.orderNumber);
    }
  }, [existingTicket]);

  const totalTalkMinutes = (Number(talkHours) || 0) * 60 + (Number(talkMinutes) || 0);

  const saveMutation = useMutation({
    mutationFn: async () => {
      const body: TicketRequest = {
        title,
        callerName: callerName || undefined,
        phone: phone || undefined,
        email: email || undefined,
        status,
        talkTimeMinutes: totalTalkMinutes,
        companyId,
        orderId,
      };
      if (isNew) {
        return api.post<TicketView>("/tickets", body);
      }
      return api.put<TicketView>(`/tickets/${id}`, body);
    },
    onSuccess: (response) => {
      setError(null);
      queryClient.invalidateQueries({ queryKey: ["tickets"] });
      if (isNew) {
        showToast("Ticket created.");
        navigate(`/tickets/${response.data.id}`);
      } else {
        queryClient.invalidateQueries({ queryKey: ["ticket", id] });
        showToast("Saved.");
      }
    },
    onError: (err: Error) => setError(err.message),
  });

  const addNoteMutation = useMutation({
    mutationFn: async () => api.post(`/tickets/${id}/entries`, { note: newNote }),
    onSuccess: () => {
      setNewNote("");
      queryClient.invalidateQueries({ queryKey: ["ticket", id] });
    },
    onError: (err: Error) => setError(err.message),
  });

  return (
    <div className="pb-24 max-w-3xl">
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-2xl font-semibold text-slate-800">
          {isNew ? "New Ticket" : `Ticket ${existingTicket?.ticketNumber ?? ""}`}
        </h2>
        <button onClick={() => navigate("/tickets")} className="text-sm text-slate-500 hover:text-slate-800">
          ← Back to Support Tickets
        </button>
      </div>

      <div className="bg-white border border-slate-200 rounded-lg p-5 mb-6 space-y-4">
        <div>
          <label className="block text-xs text-slate-400 mb-1">Ticket title</label>
          <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="e.g. Shipment hasn't arrived" className="input" />
        </div>

        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-xs text-slate-400 mb-1">Caller name</label>
            <input value={callerName} onChange={(e) => setCallerName(e.target.value)} className="input" />
          </div>
          <div>
            <label className="block text-xs text-slate-400 mb-1">Status</label>
            <select value={status} onChange={(e) => setStatus(e.target.value as TicketStatus)} className="input">
              {TICKET_STATUSES.map((s) => (
                <option key={s} value={s}>
                  {TICKET_STATUS_LABEL[s]}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-xs text-slate-400 mb-1">Phone number</label>
            <input value={phone} onChange={(e) => setPhone(e.target.value)} className="input" />
          </div>
          <div>
            <label className="block text-xs text-slate-400 mb-1">Email</label>
            <input value={email} onChange={(e) => setEmail(e.target.value)} className="input" />
          </div>
        </div>

        <div>
          <label className="block text-xs text-slate-400 mb-1">Talk time</label>
          <div className="flex items-center gap-2">
            <input type="number" min={0} value={talkHours} onChange={(e) => setTalkHours(e.target.value)} className="input w-20" />
            <span className="text-sm text-slate-500">hrs</span>
            <input type="number" min={0} max={59} value={talkMinutes} onChange={(e) => setTalkMinutes(e.target.value)} className="input w-20" />
            <span className="text-sm text-slate-500">mins</span>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-4">
          <div>
            {companyName ? (
              <div>
                <label className="block text-xs text-slate-400 mb-1">Company</label>
                <div className="flex items-center justify-between input bg-slate-50">
                  <span>{companyName}</span>
                  <button
                    type="button"
                    onClick={() => {
                      setCompanyId(undefined);
                      setCompanyName(undefined);
                    }}
                    className="text-xs text-slate-400 hover:text-slate-700"
                  >
                    Remove
                  </button>
                </div>
              </div>
            ) : (
              <LinkPicker
                label="Link a company"
                items={companies ?? []}
                getLabel={(c) => c.name}
                onSelect={(c: CompanyView) => {
                  setCompanyId(c.id);
                  setCompanyName(c.name);
                }}
                placeholder="Search companies..."
              />
            )}
          </div>
          <div>
            {orderNumber ? (
              <div>
                <label className="block text-xs text-slate-400 mb-1">Order</label>
                <div className="flex items-center justify-between input bg-slate-50">
                  <span>{orderNumber}</span>
                  <button
                    type="button"
                    onClick={() => {
                      setOrderId(undefined);
                      setOrderNumber(undefined);
                    }}
                    className="text-xs text-slate-400 hover:text-slate-700"
                  >
                    Remove
                  </button>
                </div>
              </div>
            ) : (
              <LinkPicker
                label="Link an order"
                items={orders ?? []}
                getLabel={(o) => o.orderNumber}
                getSubLabel={(o) => o.customerName}
                onSelect={(o: Order) => {
                  setOrderId(o.id);
                  setOrderNumber(o.orderNumber);
                  // Matches the backend default (TicketService.apply) - linking
                  // an order fills in its company too, unless one's already set.
                  if (!companyId && o.company) {
                    setCompanyId(o.company.id);
                    setCompanyName(o.company.name);
                  }
                }}
                placeholder="Search order number..."
              />
            )}
          </div>
        </div>

        {error && <p className="text-sm text-red-600">{error}</p>}

        <button
          onClick={() => saveMutation.mutate()}
          disabled={saveMutation.isPending || !title}
          className="bg-emerald-600 text-white text-sm font-medium px-5 py-2 rounded-md hover:bg-emerald-500 disabled:opacity-50"
        >
          {saveMutation.isPending ? "Saving..." : isNew ? "Create Ticket" : "Save Changes"}
        </button>
      </div>

      {!isNew && existingTicket && (
        <div className="bg-white border border-slate-200 rounded-lg p-5">
          <h3 className="font-medium text-slate-700 mb-3">Timeline</h3>
          {existingTicket.entries.length > 0 ? (
            <div className="space-y-3 mb-4">
              {existingTicket.entries.map((e) => (
                <div key={e.id} className="border-l-2 border-slate-200 pl-3">
                  <p className="text-xs text-slate-400">
                    <span className="font-medium text-slate-600">{e.author}</span> ·{" "}
                    {new Date(e.createdAt).toLocaleString("en-GB")}
                  </p>
                  <p className="text-sm text-slate-700 whitespace-pre-wrap">{e.note}</p>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-sm text-slate-400 mb-4">No updates logged yet.</p>
          )}

          <div className="flex gap-3 items-end">
            <div className="flex-1">
              <label className="block text-xs text-slate-400 mb-1">Add an update</label>
              <textarea value={newNote} onChange={(e) => setNewNote(e.target.value)} rows={2} className="input" />
            </div>
            <button
              onClick={() => addNoteMutation.mutate()}
              disabled={addNoteMutation.isPending || !newNote.trim()}
              className="bg-slate-800 text-white text-sm px-4 py-2 rounded-md hover:bg-slate-700 disabled:opacity-50"
            >
              {addNoteMutation.isPending ? "Adding..." : "Add Update"}
            </button>
          </div>
          {/* Author is set server-side from whoever's logged in (see
              TicketController) - not asked for here, matching how Payments
              on the order screen doesn't ask "recorded by" either. */}
        </div>
      )}
    </div>
  );
}
