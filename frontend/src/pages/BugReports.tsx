import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import axios from "axios";
import { BugReport } from "../types";
import { formatDateTime } from "../utils/format";

// Uses plain axios rather than the shared `api` client - this page must not
// trigger the auto-bug-report interceptor if its own requests fail.
const rawApi = axios.create({ baseURL: "/api" });

export default function BugReports() {
  const queryClient = useQueryClient();
  const [description, setDescription] = useState("");
  const [errorCode, setErrorCode] = useState("");

  const { data: reports, isLoading } = useQuery({
    queryKey: ["bug-reports"],
    queryFn: async () => (await rawApi.get<BugReport[]>("/bug-reports")).data,
  });

  const createMutation = useMutation({
    mutationFn: async () =>
      rawApi.post("/bug-reports", {
        description,
        errorCode: errorCode || undefined,
        source: "MANUAL",
      }),
    onSuccess: () => {
      setDescription("");
      setErrorCode("");
      queryClient.invalidateQueries({ queryKey: ["bug-reports"] });
    },
  });

  return (
    <div>
      <h2 className="text-2xl font-semibold text-slate-800 mb-2">Bug Reports</h2>
      <p className="text-slate-500 mb-6">
        Failed requests anywhere in the app are logged here automatically with a
        timestamp, status code, and what was being attempted. You can also add one by
        hand below.
      </p>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          if (!description.trim()) return;
          createMutation.mutate();
        }}
        className="bg-white border border-slate-200 rounded-lg p-5 mb-6 flex gap-3 items-end flex-wrap"
      >
        <div className="flex-1 min-w-[240px]">
          <label className="block text-xs font-medium text-slate-500 mb-1">
            Description
          </label>
          <input
            required
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="What happened, and how did you get there?"
            className="w-full border border-slate-300 rounded px-3 py-2 text-sm"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-slate-500 mb-1">
            Error code (optional)
          </label>
          <input
            value={errorCode}
            onChange={(e) => setErrorCode(e.target.value)}
            placeholder="e.g. 500"
            className="w-full border border-slate-300 rounded px-3 py-2 text-sm w-28"
          />
        </div>
        <button
          type="submit"
          disabled={createMutation.isPending}
          className="bg-slate-800 text-white text-sm px-4 py-2 rounded-md hover:bg-slate-700 disabled:opacity-50"
        >
          {createMutation.isPending ? "Adding..." : "Add Report"}
        </button>
      </form>

      <div className="bg-white rounded-lg shadow-sm border border-slate-200 overflow-hidden">
        <table className="w-full text-sm">
          <thead className="text-left text-slate-500 border-b border-slate-200">
            <tr>
              <th className="px-4 py-2">When</th>
              <th className="px-4 py-2">Source</th>
              <th className="px-4 py-2">Code</th>
              <th className="px-4 py-2">Description</th>
              <th className="px-4 py-2">Context</th>
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
            {reports?.length === 0 && (
              <tr>
                <td colSpan={5} className="px-4 py-4 text-slate-400">
                  No bug reports yet - hopefully it stays that way.
                </td>
              </tr>
            )}
            {reports?.map((r) => (
              <tr key={r.id}>
                <td className="px-4 py-2 text-slate-500 whitespace-nowrap">
                  {formatDateTime(r.occurredAt)}
                </td>
                <td className="px-4 py-2">
                  <span
                    className={`text-xs px-2 py-0.5 rounded font-medium ${
                      r.source === "AUTO"
                        ? "bg-amber-100 text-amber-700"
                        : "bg-blue-100 text-blue-700"
                    }`}
                  >
                    {r.source}
                  </span>
                </td>
                <td className="px-4 py-2 font-mono text-slate-600">{r.errorCode ?? "-"}</td>
                <td className="px-4 py-2 text-slate-800">{r.description}</td>
                <td className="px-4 py-2 text-slate-500 font-mono text-xs">{r.context ?? "-"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
