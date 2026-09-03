import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { api } from "../api/client";
import { StockImportPreview, StockImportResult } from "../types";

export default function StockImport() {
  const navigate = useNavigate();
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<StockImportPreview | null>(null);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const [confirmText, setConfirmText] = useState("");
  const [result, setResult] = useState<StockImportResult | null>(null);
  const [commitError, setCommitError] = useState<string | null>(null);

  const previewMutation = useMutation({
    mutationFn: async () => {
      const formData = new FormData();
      formData.append("file", file as File);
      return (
        await api.post<StockImportPreview>("/admin/stock-import/preview", formData, {
          headers: { "Content-Type": "multipart/form-data" },
        })
      ).data;
    },
    onSuccess: (data) => {
      setPreview(data);
      setPreviewError(null);
      setResult(null);
    },
    onError: (err: Error) => {
      setPreviewError(err.message);
      setPreview(null);
    },
  });

  const commitMutation = useMutation({
    mutationFn: async () => {
      const formData = new FormData();
      formData.append("file", file as File);
      return (
        await api.post<StockImportResult>("/admin/stock-import/commit", formData, {
          headers: { "Content-Type": "multipart/form-data" },
        })
      ).data;
    },
    onSuccess: (data) => {
      setResult(data);
      setCommitError(null);
      setPreview(null);
      setConfirmText("");
    },
    onError: (err: Error) => setCommitError(err.message),
  });

  const canCommit = preview && preview.errors.length === 0;

  return (
    <div className="max-w-3xl">
      <button onClick={() => navigate("/settings")} className="text-sm text-slate-500 hover:text-slate-800 mb-4">
        ← Back to Settings
      </button>
      <h2 className="text-2xl font-semibold text-slate-800 mb-1">Bulk Stock Import</h2>
      <p className="text-slate-500 mb-6">
        One-off replacement of current on-hand stock from an OrderWise export. Only ever touches stock that's
        currently Available or Quarantined - despatch history and anything allocated to an open order are never
        affected.
      </p>

      {!result && (
        <div className="bg-white border border-slate-200 rounded-lg p-5 mb-6">
          <label className="block text-sm font-medium text-slate-700 mb-2">Stock export spreadsheet</label>
          <p className="text-xs text-slate-500 mb-3">
            Expects columns <code className="bg-slate-100 px-1 rounded">BinNumber</code>,{" "}
            <code className="bg-slate-100 px-1 rounded">VariantCode</code>,{" "}
            <code className="bg-slate-100 px-1 rounded">BatchNo</code>,{" "}
            <code className="bg-slate-100 px-1 rounded">SerialNo</code>,{" "}
            <code className="bg-slate-100 px-1 rounded">Qty</code>. Everything else is ignored.
          </p>
          <div className="flex gap-3 items-center">
            <input
              type="file"
              accept=".xlsx,.xls,.csv"
              onChange={(e) => {
                setFile(e.target.files?.[0] ?? null);
                setPreview(null);
                setPreviewError(null);
              }}
              className="text-sm"
            />
            <button
              disabled={!file || previewMutation.isPending}
              onClick={() => previewMutation.mutate()}
              className="bg-slate-800 text-white text-sm px-4 py-2 rounded-md hover:bg-slate-700 disabled:opacity-50"
            >
              {previewMutation.isPending ? "Reading..." : "Preview Import"}
            </button>
          </div>
          {previewError && <p className="text-sm text-red-600 mt-3">{previewError}</p>}
        </div>
      )}

      {preview && (
        <div className="bg-white border border-slate-200 rounded-lg p-5 mb-6 space-y-5">
          <h3 className="font-medium text-slate-700">Preview - nothing has been changed yet</h3>

          {preview.errors.length > 0 && (
            <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-sm text-red-700">
              <p className="font-medium mb-1">This file can't be imported:</p>
              <ul className="list-disc pl-5">
                {preview.errors.map((e, i) => (
                  <li key={i}>{e}</li>
                ))}
              </ul>
            </div>
          )}

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-center">
            <div className="bg-slate-50 rounded-lg p-3">
              <p className="text-2xl font-semibold text-slate-800">{preview.totalRows}</p>
              <p className="text-xs text-slate-500">Rows in file</p>
            </div>
            <div className="bg-slate-50 rounded-lg p-3">
              <p className="text-2xl font-semibold text-slate-800">{preview.matchedProductCount}</p>
              <p className="text-xs text-slate-500">Products matched</p>
            </div>
            <div className="bg-emerald-50 rounded-lg p-3">
              <p className="text-2xl font-semibold text-emerald-700">{preview.itemsToCreate}</p>
              <p className="text-xs text-slate-500">Stock items to create</p>
            </div>
            <div className="bg-red-50 rounded-lg p-3">
              <p className="text-2xl font-semibold text-red-700">{preview.currentOnHandItemsToRemove}</p>
              <p className="text-xs text-slate-500">Current items to be removed</p>
            </div>
          </div>

          {preview.binsToCreate.length > 0 && (
            <div>
              <p className="text-sm font-medium text-slate-700 mb-1">
                New bins to be created ({preview.binsToCreate.length})
              </p>
              <p className="text-xs text-slate-500">{preview.binsToCreate.join(", ")}</p>
            </div>
          )}

          {preview.trackingTypeChanges.length > 0 && (
            <div>
              <p className="text-sm font-medium text-slate-700 mb-1">
                Tracking type changes ({preview.trackingTypeChanges.length})
              </p>
              <div className="text-xs text-slate-600 space-y-0.5">
                {preview.trackingTypeChanges.map((c) => (
                  <p key={c.sku}>
                    {c.sku}: {c.from} to {c.to}
                  </p>
                ))}
              </div>
            </div>
          )}

          {preview.edgeCaseNotes.length > 0 && (
            <div>
              <p className="text-sm font-medium text-slate-700 mb-1">Worth a look ({preview.edgeCaseNotes.length})</p>
              <div className="text-xs text-amber-700 space-y-0.5 max-h-32 overflow-y-auto">
                {preview.edgeCaseNotes.map((n, i) => (
                  <p key={i}>{n}</p>
                ))}
              </div>
            </div>
          )}

          {preview.unmatchedSkus.length > 0 && (
            <div>
              <p className="text-sm font-medium text-slate-700 mb-1">
                Not matched to an existing product ({preview.unmatchedSkus.length}) - skipped, nothing created for
                these
              </p>
              <div className="max-h-48 overflow-y-auto border border-slate-100 rounded">
                <table className="w-full text-xs">
                  <thead className="bg-slate-50 text-left text-slate-500">
                    <tr>
                      <th className="px-3 py-1.5">SKU</th>
                      <th className="px-3 py-1.5">Rows</th>
                      <th className="px-3 py-1.5">Total Qty</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {preview.unmatchedSkus.map((u) => (
                      <tr key={u.sku}>
                        <td className="px-3 py-1.5 font-mono">{u.sku}</td>
                        <td className="px-3 py-1.5">{u.rowCount}</td>
                        <td className="px-3 py-1.5">{u.totalQty}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {canCommit && (
            <div className="border-t border-slate-100 pt-4">
              <p className="text-sm text-red-700 mb-2">
                This will permanently remove {preview.currentOnHandItemsToRemove} current stock item(s) and create{" "}
                {preview.itemsToCreate} new one(s). This can't be undone.
              </p>
              <label className="block text-xs font-medium text-slate-500 mb-1">Type REPLACE to confirm</label>
              <div className="flex gap-3 items-center">
                <input
                  value={confirmText}
                  onChange={(e) => setConfirmText(e.target.value)}
                  placeholder="REPLACE"
                  className="input w-40"
                />
                <button
                  disabled={confirmText !== "REPLACE" || commitMutation.isPending}
                  onClick={() => commitMutation.mutate()}
                  className="bg-red-600 text-white text-sm px-4 py-2 rounded-md hover:bg-red-500 disabled:opacity-50"
                >
                  {commitMutation.isPending ? "Importing..." : "Confirm & Import"}
                </button>
              </div>
              {commitError && <p className="text-sm text-red-600 mt-3">{commitError}</p>}
            </div>
          )}
        </div>
      )}

      {result && (
        <div className="bg-white border border-slate-200 rounded-lg p-5">
          <h3 className="font-medium text-slate-700 mb-3">{result.success ? "Import complete" : "Import failed"}</h3>
          {result.success ? (
            <ul className="text-sm text-slate-600 space-y-1">
              <li>{result.binsCreated} new bin(s) created</li>
              <li>{result.itemsRemoved} previous stock item(s) removed</li>
              <li>{result.itemsCreated} new stock item(s) created</li>
              <li>{result.productsSkipped} SKU(s) skipped (not matched to a product)</li>
            </ul>
          ) : (
            <ul className="text-sm text-red-600 list-disc pl-5">
              {result.errors.map((e, i) => (
                <li key={i}>{e}</li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
