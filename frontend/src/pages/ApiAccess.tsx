import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../api/client";
import { ApiKeyCreatedResponse, ApiKeySummary } from "../types";
import { formatDate, formatDateTime } from "../utils/format";

export default function ApiAccess() {
  const queryClient = useQueryClient();
  const [label, setLabel] = useState("");
  const [justCreated, setJustCreated] = useState<ApiKeyCreatedResponse | null>(null);
  const [copied, setCopied] = useState(false);

  const { data: keys, isLoading } = useQuery({
    queryKey: ["api-keys"],
    queryFn: async () => (await api.get<ApiKeySummary[]>("/api-keys")).data,
  });

  const createMutation = useMutation({
    mutationFn: async () => (await api.post<ApiKeyCreatedResponse>("/api-keys", { label })).data,
    onSuccess: (data) => {
      setJustCreated(data);
      setLabel("");
      setCopied(false);
      queryClient.invalidateQueries({ queryKey: ["api-keys"] });
    },
  });

  const deactivateMutation = useMutation({
    mutationFn: async (id: number) => api.post(`/api-keys/${id}/deactivate`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["api-keys"] }),
  });

  const copyKey = () => {
    if (!justCreated) return;
    navigator.clipboard.writeText(justCreated.apiKey);
    setCopied(true);
  };

  return (
    <div>
      <h2 className="text-2xl font-semibold text-slate-800 mb-2">API Access</h2>
      <p className="text-slate-500 mb-6">
        Generate keys for customers who want to check current available stock programmatically.
        See the API documentation for endpoint details.
      </p>

      {justCreated && (
        <div className="bg-amber-50 border border-amber-300 rounded-lg p-5 mb-6">
          <p className="text-sm font-medium text-amber-800 mb-2">
            Key created for "{justCreated.label}" - copy it now, it won't be shown again:
          </p>
          <div className="flex items-center gap-2">
            <code className="flex-1 bg-white border border-amber-200 rounded px-3 py-2 text-sm font-mono break-all">
              {justCreated.apiKey}
            </code>
            <button
              onClick={copyKey}
              className="bg-amber-600 text-white text-sm px-3 py-2 rounded-md hover:bg-amber-500 whitespace-nowrap"
            >
              {copied ? "Copied!" : "Copy"}
            </button>
          </div>
        </div>
      )}

      <form
        onSubmit={(e) => {
          e.preventDefault();
          createMutation.mutate();
        }}
        className="bg-white border border-slate-200 rounded-lg p-5 mb-6 flex gap-3 items-end"
      >
        <div className="flex-1">
          <label className="block text-xs font-medium text-slate-500 mb-1">
            Label (e.g. customer or integration name)
          </label>
          <input
            required
            value={label}
            onChange={(e) => setLabel(e.target.value)}
            placeholder="Acme Corp integration"
            className="w-full border border-slate-300 rounded px-3 py-2 text-sm"
          />
        </div>
        <button
          type="submit"
          disabled={createMutation.isPending}
          className="bg-slate-800 text-white text-sm px-4 py-2 rounded-md hover:bg-slate-700 disabled:opacity-50"
        >
          {createMutation.isPending ? "Generating..." : "Generate Key"}
        </button>
      </form>

      <div className="bg-white rounded-lg shadow-sm border border-slate-200 overflow-hidden">
        <table className="w-full text-sm">
          <thead className="text-left text-slate-500 border-b border-slate-200">
            <tr>
              <th className="px-4 py-2">Label</th>
              <th className="px-4 py-2">Created</th>
              <th className="px-4 py-2">Last used</th>
              <th className="px-4 py-2">Status</th>
              <th className="px-4 py-2"></th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {isLoading && (
              <tr>
                <td colSpan={5} className="px-4 py-4 text-slate-400">
                  Loading...
                </td>
              </tr>
            )}
            {keys?.length === 0 && (
              <tr>
                <td colSpan={5} className="px-4 py-4 text-slate-400">
                  No API keys yet.
                </td>
              </tr>
            )}
            {keys?.map((k) => (
              <tr key={k.id}>
                <td className="px-4 py-2 font-medium text-slate-800">{k.label}</td>
                <td className="px-4 py-2 text-slate-500">{formatDate(k.createdAt)}</td>
                <td className="px-4 py-2 text-slate-500">
                  {k.lastUsedAt ? formatDateTime(k.lastUsedAt) : "Never"}
                </td>
                <td className="px-4 py-2">
                  <span
                    className={`text-xs px-2 py-0.5 rounded font-medium ${
                      k.active ? "bg-emerald-100 text-emerald-700" : "bg-slate-200 text-slate-500"
                    }`}
                  >
                    {k.active ? "Active" : "Revoked"}
                  </span>
                </td>
                <td className="px-4 py-2 text-right">
                  {k.active && (
                    <button
                      onClick={() => deactivateMutation.mutate(k.id)}
                      className="text-xs text-red-600 hover:text-red-800"
                    >
                      Revoke
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
