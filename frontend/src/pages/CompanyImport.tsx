import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { api } from "../api/client";
import { CompanyImportPreview, CompanyImportResult } from "../types";

export default function CompanyImport() {
  const navigate = useNavigate();
  const [companiesFile, setCompaniesFile] = useState<File | null>(null);
  const [contactsFile, setContactsFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<CompanyImportPreview | null>(null);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const [result, setResult] = useState<CompanyImportResult | null>(null);
  const [commitError, setCommitError] = useState<string | null>(null);

  const buildFormData = () => {
    const formData = new FormData();
    formData.append("companiesFile", companiesFile as File);
    formData.append("contactsFile", contactsFile as File);
    return formData;
  };

  const previewMutation = useMutation({
    mutationFn: async () =>
      (
        await api.post<CompanyImportPreview>("/admin/company-import/preview", buildFormData(), {
          headers: { "Content-Type": "multipart/form-data" },
        })
      ).data,
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
    mutationFn: async () =>
      (
        await api.post<CompanyImportResult>("/admin/company-import/commit", buildFormData(), {
          headers: { "Content-Type": "multipart/form-data" },
        })
      ).data,
    onSuccess: (data) => {
      setResult(data);
      setCommitError(null);
      setPreview(null);
    },
    onError: (err: Error) => setCommitError(err.message),
  });

  const canCommit = preview && preview.errors.length === 0;

  return (
    <div className="max-w-3xl">
      <button onClick={() => navigate("/settings")} className="text-sm text-slate-500 hover:text-slate-800 mb-4">
        ← Back to Settings
      </button>
      <h2 className="text-2xl font-semibold text-slate-800 mb-1">Company &amp; Contact Import</h2>
      <p className="text-slate-500 mb-6">
        Bulk create/update Companies and their Contacts from an OrderWise customer export, matched by Account
        number / CustomerCode. "Do not use" and "On hold" companies are imported too, not excluded - use the
        filters on the Companies page to find them afterwards.
      </p>

      {!result && (
        <div className="bg-white border border-slate-200 rounded-lg p-5 mb-6 space-y-4">
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-2">Customer list spreadsheet</label>
            <p className="text-xs text-slate-500 mb-2">
              Expects columns <code className="bg-slate-100 px-1 rounded">Account number</code>,{" "}
              <code className="bg-slate-100 px-1 rounded">Statement name</code>,{" "}
              <code className="bg-slate-100 px-1 rounded">On hold</code>,{" "}
              <code className="bg-slate-100 px-1 rounded">Do not use this account</code>,{" "}
              <code className="bg-slate-100 px-1 rounded">GAPS</code>,{" "}
              <code className="bg-slate-100 px-1 rounded">GDMS</code>,{" "}
              <code className="bg-slate-100 px-1 rounded">Credit limit</code>,{" "}
              <code className="bg-slate-100 px-1 rounded">EORI number</code>,{" "}
              <code className="bg-slate-100 px-1 rounded">VAT number</code>. Everything else is ignored.
            </p>
            <input
              type="file"
              accept=".xlsx,.xls,.csv"
              onChange={(e) => {
                setCompaniesFile(e.target.files?.[0] ?? null);
                setPreview(null);
                setPreviewError(null);
              }}
              className="text-sm"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-2">Customer contact details spreadsheet</label>
            <p className="text-xs text-slate-500 mb-2">
              Expects columns <code className="bg-slate-100 px-1 rounded">CustomerCode</code>,{" "}
              <code className="bg-slate-100 px-1 rounded">CustomerContact</code>,{" "}
              <code className="bg-slate-100 px-1 rounded">ContactEmail</code>,{" "}
              <code className="bg-slate-100 px-1 rounded">ContactTelephone</code>,{" "}
              <code className="bg-slate-100 px-1 rounded">ContactPosition</code>,{" "}
              <code className="bg-slate-100 px-1 rounded">MainContact</code>,{" "}
              <code className="bg-slate-100 px-1 rounded">ActiveContact</code>. A contact with no company match
              (CustomerCode not found in the companies file or the system) is skipped, never guessed at.
            </p>
            <input
              type="file"
              accept=".xlsx,.xls,.csv"
              onChange={(e) => {
                setContactsFile(e.target.files?.[0] ?? null);
                setPreview(null);
                setPreviewError(null);
              }}
              className="text-sm"
            />
          </div>
          <button
            disabled={!companiesFile || !contactsFile || previewMutation.isPending}
            onClick={() => previewMutation.mutate()}
            className="bg-slate-800 text-white text-sm px-4 py-2 rounded-md hover:bg-slate-700 disabled:opacity-50"
          >
            {previewMutation.isPending ? "Reading..." : "Preview Import"}
          </button>
          {previewError && <p className="text-sm text-red-600 mt-3">{previewError}</p>}
        </div>
      )}

      {preview && (
        <div className="bg-white border border-slate-200 rounded-lg p-5 mb-6 space-y-5">
          <h3 className="font-medium text-slate-700">Preview - nothing has been changed yet</h3>

          {preview.errors.length > 0 && (
            <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-sm text-red-700">
              <p className="font-medium mb-1">This can't be imported:</p>
              <ul className="list-disc pl-5">
                {preview.errors.map((e, i) => (
                  <li key={i}>{e}</li>
                ))}
              </ul>
            </div>
          )}

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-center">
            <div className="bg-slate-50 rounded-lg p-3">
              <p className="text-2xl font-semibold text-slate-800">{preview.totalCompanyRows}</p>
              <p className="text-xs text-slate-500">Company rows</p>
            </div>
            <div className="bg-emerald-50 rounded-lg p-3">
              <p className="text-2xl font-semibold text-emerald-700">{preview.companiesToCreate}</p>
              <p className="text-xs text-slate-500">Companies to create</p>
            </div>
            <div className="bg-slate-50 rounded-lg p-3">
              <p className="text-2xl font-semibold text-slate-800">{preview.companiesToUpdate}</p>
              <p className="text-xs text-slate-500">Companies to update</p>
            </div>
            <div className="bg-slate-50 rounded-lg p-3">
              <p className="text-2xl font-semibold text-slate-800">{preview.totalContactRows}</p>
              <p className="text-xs text-slate-500">Contact rows</p>
            </div>
          </div>

          {preview.unmatchedContactCodes.length > 0 && (
            <div>
              <p className="text-sm font-medium text-slate-700 mb-1">
                Contact rows with no matching company ({preview.unmatchedContactCodes.length}) - these will be skipped
              </p>
              <p className="text-xs text-slate-500">{preview.unmatchedContactCodes.slice(0, 50).join(", ")}</p>
            </div>
          )}

          {preview.edgeCaseNotes.length > 0 && (
            <div>
              <p className="text-sm font-medium text-slate-700 mb-1">Notes</p>
              <ul className="text-xs text-slate-500 list-disc pl-5">
                {preview.edgeCaseNotes.map((n, i) => (
                  <li key={i}>{n}</li>
                ))}
              </ul>
            </div>
          )}

          <div className="flex gap-3 pt-2 border-t border-slate-100">
            <button
              onClick={() => setPreview(null)}
              className="text-sm text-slate-500 hover:text-slate-800 border border-slate-300 rounded-md px-4 py-2"
            >
              Cancel
            </button>
            <button
              disabled={!canCommit || commitMutation.isPending}
              onClick={() => {
                if (confirm("Import these companies and contacts? This will create/update real records.")) {
                  commitMutation.mutate();
                }
              }}
              className="bg-emerald-600 text-white text-sm px-4 py-2 rounded-md hover:bg-emerald-500 disabled:opacity-50"
            >
              {commitMutation.isPending ? "Importing..." : "Confirm Import"}
            </button>
          </div>
          {commitError && <p className="text-sm text-red-600">{commitError}</p>}
        </div>
      )}

      {result && (
        <div className="bg-white border border-slate-200 rounded-lg p-5 space-y-4">
          <h3 className="font-medium text-emerald-700">Import complete</h3>
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 text-center">
            <div className="bg-slate-50 rounded-lg p-3">
              <p className="text-2xl font-semibold text-slate-800">{result.companiesCreated}</p>
              <p className="text-xs text-slate-500">Companies created</p>
            </div>
            <div className="bg-slate-50 rounded-lg p-3">
              <p className="text-2xl font-semibold text-slate-800">{result.companiesUpdated}</p>
              <p className="text-xs text-slate-500">Companies updated</p>
            </div>
            <div className="bg-slate-50 rounded-lg p-3">
              <p className="text-2xl font-semibold text-slate-800">{result.contactsCreated}</p>
              <p className="text-xs text-slate-500">Contacts created</p>
            </div>
            <div className="bg-slate-50 rounded-lg p-3">
              <p className="text-2xl font-semibold text-slate-800">{result.contactsUpdated}</p>
              <p className="text-xs text-slate-500">Contacts updated</p>
            </div>
            <div className="bg-amber-50 rounded-lg p-3">
              <p className="text-2xl font-semibold text-amber-700">{result.contactsSkipped}</p>
              <p className="text-xs text-slate-500">Contacts skipped (no company match)</p>
            </div>
          </div>
          <button
            onClick={() => navigate("/companies")}
            className="bg-slate-800 text-white text-sm px-4 py-2 rounded-md hover:bg-slate-700"
          >
            Go to Companies
          </button>
        </div>
      )}
    </div>
  );
}
