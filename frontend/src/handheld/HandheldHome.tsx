import { useNavigate } from "react-router-dom";

export default function HandheldHome() {
  const navigate = useNavigate();

  return (
    <div className="min-h-screen bg-slate-950 text-white flex flex-col">
      <header className="px-4 py-5 border-b border-slate-800">
        <h1 className="text-xl font-bold">BNS Warehouse</h1>
        <p className="text-sm text-slate-400">Handheld</p>
      </header>

      <div className="flex-1 flex flex-col gap-4 p-4 justify-center">
        <button
          onClick={() => navigate("/handheld/pick")}
          className="bg-slate-900 border border-slate-800 rounded-2xl p-6 text-left active:bg-slate-800"
        >
          <p className="text-2xl font-bold mb-1">Picking</p>
          <p className="text-slate-400 text-sm">Scan and pick orders ready for despatch</p>
        </button>

        <button
          onClick={() => navigate("/handheld/goods-in")}
          className="bg-slate-900 border border-slate-800 rounded-2xl p-6 text-left active:bg-slate-800"
        >
          <p className="text-2xl font-bold mb-1">Goods In</p>
          <p className="text-slate-400 text-sm">Scan cartons to book stock into a bin</p>
        </button>
      </div>
    </div>
  );
}
