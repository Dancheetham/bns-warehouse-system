import { downloadFile, ReportCard } from "../components/ReportCard";

export default function ReportsOrders() {
  return (
    <div>
      <h2 className="text-2xl font-semibold text-slate-800 mb-2">Order Reports</h2>
      <p className="text-slate-500 mb-6">
        Generated live from current data and downloaded as an Excel spreadsheet.
      </p>

      <div className="grid md:grid-cols-2 gap-4">
        <ReportCard
          title="Open Orders"
          description="Every order that isn't completed or cancelled - on hold, partially despatched, or a quote awaiting conversion - oldest first."
          onDownload={() => downloadFile("/reports/open-orders", "open-orders.xlsx")}
        />

        <ReportCard
          title="Order Line Detail"
          description="Every line from every order, one row each - product, quantities, unit price, and line total. Useful for sales analysis."
          onDownload={() => downloadFile("/reports/order-lines", "order-line-detail.xlsx")}
        />
      </div>
    </div>
  );
}
