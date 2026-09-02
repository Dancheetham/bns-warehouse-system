import { useQuery } from "@tanstack/react-query";
import { api } from "../api/client";
import SplitPacking from "./SplitPacking";
import SerialPacking from "./SerialPacking";

// Which screen renders is a Settings > Packing Mode toggle (packing_mode: SPLIT
// or SERIAL) - both talk to the same order/carton but through different endpoints
// (/packing for quantity-split, /serial-packing for per-unit assignment).
export default function Packing() {
  const { data: settings, isLoading } = useQuery({
    queryKey: ["settings"],
    queryFn: async () => (await api.get<Record<string, string>>("/settings")).data,
  });

  if (isLoading) {
    return <p className="text-slate-500">Loading...</p>;
  }

  const mode = settings?.["packing_mode"] ?? "SPLIT";
  return mode === "SERIAL" ? <SerialPacking /> : <SplitPacking />;
}
