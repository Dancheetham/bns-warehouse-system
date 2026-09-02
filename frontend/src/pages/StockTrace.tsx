import { useRef, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { api } from "../api/client";
import { StockTraceResult } from "../types";
import { formatDateTime } from "../utils/format";

const EVENT_LABELS: Record<string, string> = {
  RECEIPT: "Received",
  MOVE: "Moved",
  ALLOCATE: "Allocated",
  DEALLOCATE: "Deallocated",
  DESPATCH: "Despatched",
  QUARANTINE: "Quarantined",
  RELEASE_QUARANTINE: "Released from quarantine",
  RETURN: "Returned",
  ADJUSTMENT: "Adjusted",
};

type SearchMode = "item" | "batch";

function looksLikeMac(value: string) {
  return /^[0-9a-fA-F]{2}([:\-]?[0-9a-fA-F]{2}){5}$/.test(value.trim());
}

function Timeline({ result }: { result: StockTraceResult }) {
  if (result.timeline.length === 0) {
    return <p className="text-sm text-slate-400">No movement history yet.</p>;
  }
  return (
    <ol className="relative border-l-2 border-slate-200 ml-2 space-y-6">
      {result.timeline.map((event, i) => (
        <li key={i} className="ml-4">
          <div className="absolute w-2.5 h-2.5 bg-emerald-500 rounded-full -left-[5px] mt-1.5 border border-white" />
          <p className="text-xs text-slate-400">{formatDateTime(event.timestamp)}</p>
          <p className="font-medium text-slate-800">
            {EVENT_LABELS[event.eventType] ?? event.eventType}
            {event.fromLocation && event.toLocation
              ? ` - ${event.fromLocation} → ${event.toLocation}`
              : event.toLocation
              ? ` - ${event.toLocation}`
              : ""}
          </p>
          {(event.reference || event.notes) && (
            <p className="text-sm text-slate-500">
              {[event.reference, event.notes].filter(Boolean).join(" · ")}
            </p>
          )}
          {event.performedBy && <p className="text-xs text-slate-400">by {event.performedBy}</p>}
        </li>
      ))}
    </ol>
  );
}

function SummaryRow({ result }: { result: StockTraceResult }) {
  return (
    <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
      <div>
        <p className="text-xs text-slate-400">Product</p>
        <p className="font-medium text-slate-800">{result.productSku}</p>
        <p className="text-sm text-slate-500">{result.productName}</p>
      </div>
      <div>
        <p className="text-xs text-slate-400">{result.identifierType}</p>
        <p className="font-medium text-slate-800 font-mono">{result.identifier}</p>
      </div>
      <div>
        <p className="text-xs text-slate-400">Status</p>
        <p className="font-medium text-slate-800">{result.currentStatus}</p>
      </div>
      <div>
        <p className="text-xs text-slate-400">Current Location</p>
        <p className="font-medium text-slate-800">{result.currentLocation ?? "-"}</p>
      </div>
    </div>
  );
}

export default function StockTrace() {
  const [mode, setMode] = useState<SearchMode>("item");
  const [query, setQuery] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<StockTraceResult | null>(null);
  const [batchResults, setBatchResults] = useState<StockTraceResult[] | null>(null);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const inputRef = useRef<HTMLInputElement>(null);

  const itemTraceMutation = useMutation({
    mutationFn: async (value: string) => {
      const primary = looksLikeMac(value) ? "mac" : "serial";
      const fallback = primary === "mac" ? "serial" : "mac";
      try {
        return (await api.get<StockTraceResult>(`/trace/${primary}/${encodeURIComponent(value)}`)).data;
      } catch (primaryErr) {
        // The MAC-format heuristic is just a best guess (real-world test data
        // like "MAC123" won't match it) - try the other identifier type before
        // giving up, rather than surfacing a false "not found".
        try {
          return (await api.get<StockTraceResult>(`/trace/${fallback}/${encodeURIComponent(value)}`)).data;
        } catch {
          throw primaryErr;
        }
      }
    },
    onSuccess: (data) => {
      setResult(data);
      setBatchResults(null);
      setError(null);
    },
    onError: (err: Error) => {
      setResult(null);
      setBatchResults(null);
      setError(err.message);
    },
  });

  const batchTraceMutation = useMutation({
    mutationFn: async (value: string) =>
      (await api.get<StockTraceResult[]>(`/trace/batch/${encodeURIComponent(value)}`)).data,
    onSuccess: (data) => {
      setBatchResults(data);
      setResult(null);
      setError(null);
      setExpanded(new Set());
    },
    onError: (err: Error) => {
      setResult(null);
      setBatchResults(null);
      setError(err.message);
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!query.trim()) return;
    if (mode === "item") {
      itemTraceMutation.mutate(query.trim());
    } else {
      batchTraceMutation.mutate(query.trim());
    }
  };

  const toggleExpanded = (identifier: string) => {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(identifier)) next.delete(identifier);
      else next.add(identifier);
      return next;
    });
  };

  return (
    <div>
      <h2 className="text-2xl font-semibold text-slate-800 mb-2">Stock Trace</h2>
      <p className="text-slate-500 mb-4">
        Search is not case-sensitive - a MAC or serial will match however it was typed or scanned.
      </p>

      <div className="flex gap-2 mb-4">
        <button
          onClick={() => setMode("item")}
          className={`text-sm px-3 py-1.5 rounded-md font-medium ${
            mode === "item" ? "bg-slate-800 text-white" : "bg-slate-100 text-slate-600 hover:bg-slate-200"
          }`}
        >
          Single item (MAC / Serial)
        </button>
        <button
          onClick={() => setMode("batch")}
          className={`text-sm px-3 py-1.5 rounded-md font-medium ${
            mode === "batch" ? "bg-slate-800 text-white" : "bg-slate-100 text-slate-600 hover:bg-slate-200"
          }`}
        >
          Batch / Carton
        </button>
      </div>

      <form onSubmit={handleSubmit} className="mb-6">
        <input
          ref={inputRef}
          autoFocus
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder={
            mode === "item"
              ? "Scan or type a MAC address / serial number..."
              : "Scan or type a batch / carton code..."
          }
          className="w-full text-lg border-2 border-slate-300 focus:border-emerald-500 rounded-lg px-4 py-4 outline-none"
        />
      </form>

      {error && (
        <p className="text-sm text-red-600 bg-red-50 border border-red-200 rounded px-4 py-3 mb-6">
          {error}
        </p>
      )}

      {result && (
        <div className="space-y-6">
          <div className="bg-white rounded-lg shadow-sm border border-slate-200 p-5">
            <SummaryRow result={result} />
          </div>
          <div className="bg-white rounded-lg shadow-sm border border-slate-200 p-5">
            <h3 className="font-medium text-slate-700 mb-4">Timeline</h3>
            <Timeline result={result} />
          </div>
        </div>
      )}

      {batchResults && (
        <div>
          <p className="text-sm text-slate-500 mb-3">
            {batchResults.length} item{batchResults.length === 1 ? "" : "s"} in this batch/carton
          </p>
          <div className="bg-white rounded-lg shadow-sm border border-slate-200 divide-y divide-slate-100 overflow-hidden">
            {batchResults.map((item) => {
              const isOpen = expanded.has(item.identifier);
              return (
                <div key={item.identifier}>
                  <button
                    onClick={() => toggleExpanded(item.identifier)}
                    className="w-full flex justify-between items-center px-5 py-3 hover:bg-slate-50 text-left"
                  >
                    <div>
                      <p className="font-medium text-slate-800 font-mono">{item.identifier}</p>
                      <p className="text-sm text-slate-500">
                        {item.productSku} - {item.productName}
                      </p>
                    </div>
                    <div className="flex items-center gap-3">
                      <span className="text-xs px-2 py-1 rounded bg-slate-100 text-slate-600">
                        {item.currentStatus}
                      </span>
                      <span className="text-slate-400 text-sm">{isOpen ? "▲" : "▼"}</span>
                    </div>
                  </button>
                  {isOpen && (
                    <div className="px-5 pb-5 pt-1 bg-slate-50 border-t border-slate-100">
                      <Timeline result={item} />
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
