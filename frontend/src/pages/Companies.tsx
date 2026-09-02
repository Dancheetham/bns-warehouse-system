import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../api/client";
import { CompanyRequest, CompanyView } from "../types";

function CompanyRow({ company }: { company: CompanyView }) {
  const [editing, setEditing] = useState(false);
  const queryClient = useQueryClient();
  const [name, setName] = useState(company.name);
  const [creditLimit, setCreditLimit] = useState(company.creditLimit != null ? String(company.creditLimit) : "");
  const [shopifyCompanyId, setShopifyCompanyId] = useState(company.shopifyCompanyId ?? "");
  const [notes, setNotes] = useState(company.notes ?? "");
  const [error, setError] = useState<string | null>(null);

  const updateMutation = useMutation({
    mutationFn: async () => {
      const body: CompanyRequest = {
        name,
        creditLimit: creditLimit ? Number(creditLimit) : undefined,
        shopifyCompanyId: shopifyCompanyId || undefined,
        notes: notes || undefined,
      };
      return api.put(`/companies/${company.id}`, body);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["companies"] });
      setEditing(false);
      setError(null);
    },
    onError: (err: Error) => setError(err.message),
  });

  return (
    <div className="px-4 py-3 hover:bg-slate-50">
      <div className="flex justify-between items-center">
        <div>
          <p className="font-medium text-slate-800">{company.name}</p>
          {company.creditLimit != null ? (
            <p className={`text-sm ${company.overLimit ? "text-red-600 font-medium" : "text-slate-500"}`}>
              {company.creditUsed?.toFixed(2)} used of {company.creditLimit.toFixed(2)} limit (
              {company.creditAvailable?.toFixed(2)} available)
              {company.overLimit && " - OVER LIMIT"}
            </p>
          ) : (
            <p className="text-sm text-slate-400">No credit account</p>
          )}
        </div>
        <button
          onClick={() => setEditing((v) => !v)}
          className="text-xs text-slate-500 hover:text-slate-800 border border-slate-300 rounded px-2 py-1"
        >
          {editing ? "Cancel" : "Edit"}
        </button>
      </div>

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
          <button
            type="submit"
            disabled={updateMutation.isPending}
            className="bg-emerald-600 text-white text-sm px-4 py-2 rounded-md hover:bg-emerald-500 disabled:opacity-50"
          >
            {updateMutation.isPending ? "Saving..." : "Save Changes"}
          </button>
          {error && <p className="col-span-full text-sm text-red-600">{error}</p>}
        </form>
      )}
    </div>
  );
}

export default function Companies() {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [name, setName] = useState("");
  const [creditLimit, setCreditLimit] = useState("");
  const [error, setError] = useState<string | null>(null);

  const { data: companies, isLoading } = useQuery({
    queryKey: ["companies"],
    queryFn: async () => (await api.get<CompanyView[]>("/companies")).data,
  });

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

      <div className="bg-white rounded-lg shadow-sm border border-slate-200 divide-y divide-slate-100 overflow-hidden">
        {isLoading && <p className="px-4 py-4 text-slate-400 text-sm">Loading...</p>}
        {!isLoading && companies?.length === 0 && (
          <p className="px-4 py-4 text-slate-400 text-sm">No companies yet.</p>
        )}
        {companies?.map((c) => (
          <CompanyRow key={c.id} company={c} />
        ))}
      </div>
    </div>
  );
}
