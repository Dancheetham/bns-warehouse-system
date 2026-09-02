import { useMutation } from "@tanstack/react-query";
import { api } from "../api/client";

export async function downloadFile(url: string, fallbackName: string) {
  const response = await api.get(url, { responseType: "blob" });
  const disposition = response.headers["content-disposition"] as string | undefined;
  const match = disposition?.match(/filename="?([^"]+)"?/);
  const filename = match?.[1] ?? fallbackName;

  const blobUrl = window.URL.createObjectURL(response.data);
  const link = document.createElement("a");
  link.href = blobUrl;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(blobUrl);
}

export function ReportCard({
  title,
  description,
  onDownload,
  extra,
}: {
  title: string;
  description: string;
  onDownload: () => void;
  extra?: React.ReactNode;
}) {
  const mutation = useMutation({ mutationFn: async () => onDownload() });

  return (
    <div className="bg-white rounded-lg shadow-sm border border-slate-200 p-5">
      <h3 className="font-medium text-slate-800 mb-1">{title}</h3>
      <p className="text-sm text-slate-500 mb-4">{description}</p>
      {extra}
      <button
        onClick={() => mutation.mutate()}
        disabled={mutation.isPending}
        className="bg-emerald-600 text-white text-sm px-4 py-2 rounded-md hover:bg-emerald-500 disabled:opacity-50"
      >
        {mutation.isPending ? "Generating..." : "Download Excel"}
      </button>
      {mutation.isError && (
        <p className="text-sm text-red-600 mt-2">{(mutation.error as Error).message}</p>
      )}
    </div>
  );
}
