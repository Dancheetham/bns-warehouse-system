import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useSearchParams } from "react-router-dom";
import { api } from "../api/client";
import { Product, ShopifyCompanySyncResult, ShopifyOrderSyncResult, ShopifyStatus, ShopifyStockPushResult, ShopifySyncResult } from "../types";

export default function ShopifySync() {
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const [connectMessage, setConnectMessage] = useState<{ ok: boolean; text: string } | null>(null);

  const { data: status, isLoading } = useQuery({
    queryKey: ["shopify-status"],
    queryFn: async () => (await api.get<ShopifyStatus>("/shopify/status")).data,
  });

  const { data: products } = useQuery({
    queryKey: ["products"],
    queryFn: async () => (await api.get<Product[]>("/products")).data,
  });

  const needsReview = products?.filter((p) => p.needsReview) ?? [];

  // Landed back here from the OAuth callback - the backend appends these params.
  useEffect(() => {
    const connected = searchParams.get("connected");
    if (connected === null) return;
    if (connected === "true") {
      setConnectMessage({ ok: true, text: "Connected to Shopify." });
      queryClient.invalidateQueries({ queryKey: ["shopify-status"] });
    } else {
      setConnectMessage({ ok: false, text: searchParams.get("error") ?? "Couldn't connect to Shopify." });
    }
    setSearchParams({}, { replace: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const syncMutation = useMutation({
    mutationFn: async () => (await api.post<ShopifySyncResult>("/shopify/sync")).data,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["shopify-status"] });
      queryClient.invalidateQueries({ queryKey: ["products"] });
    },
  });

  const companySyncMutation = useMutation({
    mutationFn: async () => (await api.post<ShopifyCompanySyncResult>("/shopify/sync-companies")).data,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["shopify-status"] });
      queryClient.invalidateQueries({ queryKey: ["companies"] });
    },
  });

  const orderSyncMutation = useMutation({
    mutationFn: async () => (await api.post<ShopifyOrderSyncResult>("/shopify/sync-orders")).data,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["shopify-status"] });
      queryClient.invalidateQueries({ queryKey: ["orders"] });
    },
  });

  const stockPushMutation = useMutation({
    mutationFn: async () => (await api.post<ShopifyStockPushResult>("/shopify/push-stock")).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["shopify-status"] }),
  });

  const disconnectMutation = useMutation({
    mutationFn: async () => api.post("/shopify/oauth/disconnect"),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["shopify-status"] }),
  });

  // The backend's own port (8080), not the nginx-proxied 8081 this page is
  // served from - keeps the OAuth redirect_uri unambiguous. See
  // ShopifyOAuthController for why.
  const connectUrl = `${window.location.protocol}//${window.location.hostname}:8080/api/shopify/oauth/start`;

  if (isLoading) return <p className="text-slate-500">Loading...</p>;

  if (!status?.appConfigured) {
    return (
      <div>
        <h2 className="text-2xl font-semibold text-slate-800 mb-2">Shopify Sync</h2>
        <div className="bg-amber-50 border border-amber-200 rounded-lg p-4 text-sm text-amber-800">
          Not configured - set <code className="bg-amber-100 px-1 rounded">SHOPIFY_SHOP_DOMAIN</code>,{" "}
          <code className="bg-amber-100 px-1 rounded">SHOPIFY_CLIENT_ID</code> and{" "}
          <code className="bg-amber-100 px-1 rounded">SHOPIFY_CLIENT_SECRET</code> in{" "}
          <code className="bg-amber-100 px-1 rounded">docker-compose.yml</code> and restart.
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-3xl">
      <h2 className="text-2xl font-semibold text-slate-800 mb-2">Shopify Sync</h2>
      <p className="text-slate-500 mb-4">
        Read-only - pulls products in from <span className="font-medium">{status.shopDomain}</span> and keeps them
        updated. Tracking type, default bin, and default password are never touched by a sync - Shopify has no
        concept of any of them.
      </p>

      {connectMessage && (
        <div
          className={`rounded-lg p-4 text-sm mb-4 ${
            connectMessage.ok ? "bg-emerald-50 text-emerald-800 border border-emerald-200" : "bg-red-50 text-red-700 border border-red-200"
          }`}
        >
          {connectMessage.text}
        </div>
      )}

      <div className="bg-white border border-slate-200 rounded-lg p-4 mb-6 flex items-center justify-between">
        <div>
          <p className="font-medium text-slate-800">
            {status.connected ? (
              <span className="text-emerald-600">● Connected</span>
            ) : (
              <span className="text-slate-400">○ Not connected</span>
            )}
          </p>
          <p className="text-sm text-slate-500">
            {status.connected
              ? "A real Admin API access token is on file, obtained via Shopify's OAuth flow."
              : "Connect to obtain an access token before syncing can run."}
          </p>
        </div>
        {status.connected ? (
          <button
            onClick={() => disconnectMutation.mutate()}
            disabled={disconnectMutation.isPending}
            className="text-sm text-red-600 border border-red-200 px-4 py-2 rounded-md hover:bg-red-50 disabled:opacity-50"
          >
            Disconnect
          </button>
        ) : (
          <a
            href={connectUrl}
            className="bg-emerald-600 text-white text-sm px-4 py-2 rounded-md hover:bg-emerald-500"
          >
            Connect to Shopify
          </a>
        )}
      </div>

      <div className="grid grid-cols-3 gap-4 mb-6">
        <div className="bg-white rounded-lg shadow-sm p-5 border border-slate-200">
          <p className="text-sm text-slate-500">Synced products</p>
          <p className="text-3xl font-semibold text-slate-800">{status.totalSyncedProducts}</p>
        </div>
        <div className="bg-white rounded-lg shadow-sm p-5 border border-slate-200">
          <p className="text-sm text-slate-500">Need review</p>
          <p className="text-3xl font-semibold text-amber-600">{status.needsReviewCount}</p>
        </div>
        <div className="bg-white rounded-lg shadow-sm p-5 border border-slate-200">
          <p className="text-sm text-slate-500">Last synced</p>
          <p className="text-lg font-medium text-slate-800 mt-1.5">
            {status.lastSyncedAt ? new Date(status.lastSyncedAt).toLocaleString("en-GB") : "Never"}
          </p>
        </div>
      </div>

      <button
        onClick={() => syncMutation.mutate()}
        disabled={syncMutation.isPending || !status.connected}
        className="bg-emerald-600 text-white text-sm px-5 py-2.5 rounded-md hover:bg-emerald-500 disabled:opacity-50 mb-6"
      >
        {syncMutation.isPending ? "Syncing..." : "Sync Now"}
      </button>

      {syncMutation.data && (
        <div className="bg-white border border-slate-200 rounded-lg p-4 mb-6 text-sm">
          <p>
            <span className="font-medium text-emerald-600">{syncMutation.data.created}</span> new,{" "}
            <span className="font-medium text-blue-600">{syncMutation.data.updated}</span> updated,{" "}
            <span className="font-medium text-slate-500">{syncMutation.data.skippedNoSku}</span> skipped (no SKU set
            in Shopify)
          </p>
          {syncMutation.data.errors.length > 0 && (
            <ul className="mt-2 text-red-600">
              {syncMutation.data.errors.map((e, i) => (
                <li key={i}>{e}</li>
              ))}
            </ul>
          )}
        </div>
      )}

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-6">
        <div className="bg-white border border-slate-200 rounded-lg p-5">
          <h3 className="font-medium text-slate-700 mb-1">Companies</h3>
          <p className="text-xs text-slate-500 mb-3">
            Pulls Shopify's own B2B companies in - name and existence only. Credit limits stay set here, never
            pulled from Shopify.
          </p>
          <p className="text-xs text-slate-400 mb-3">
            Last synced: {status.lastCompanySyncedAt ? new Date(status.lastCompanySyncedAt).toLocaleString("en-GB") : "Never"}
          </p>
          <button
            onClick={() => companySyncMutation.mutate()}
            disabled={companySyncMutation.isPending || !status.connected}
            className="bg-slate-800 text-white text-sm px-4 py-2 rounded-md hover:bg-slate-700 disabled:opacity-50"
          >
            {companySyncMutation.isPending ? "Syncing..." : "Sync Companies Now"}
          </button>
          {companySyncMutation.data && (
            <p className="text-xs text-slate-500 mt-2">
              {companySyncMutation.data.created} new, {companySyncMutation.data.updated} updated
              {companySyncMutation.data.errors.length > 0 && (
                <span className="text-red-600"> - {companySyncMutation.data.errors.join("; ")}</span>
              )}
            </p>
          )}
        </div>

        <div className="bg-white border border-slate-200 rounded-lg p-5">
          <h3 className="font-medium text-slate-700 mb-1">Orders</h3>
          <p className="text-xs text-slate-500 mb-3">
            Every order lands On Hold, linked to its company if it's a B2B order - runs automatically every 2
            minutes.
          </p>
          <p className="text-xs text-slate-400 mb-3">
            Last synced: {status.lastOrderSyncedAt ? new Date(status.lastOrderSyncedAt).toLocaleString("en-GB") : "Never"}
          </p>
          <button
            onClick={() => orderSyncMutation.mutate()}
            disabled={orderSyncMutation.isPending || !status.connected}
            className="bg-slate-800 text-white text-sm px-4 py-2 rounded-md hover:bg-slate-700 disabled:opacity-50"
          >
            {orderSyncMutation.isPending ? "Syncing..." : "Sync Orders Now"}
          </button>
          {orderSyncMutation.data && (
            <div className="text-xs text-slate-500 mt-2">
              <p>
                {orderSyncMutation.data.imported} imported, {orderSyncMutation.data.alreadyImported} already had
              </p>
              {orderSyncMutation.data.skipped.length > 0 && (
                <ul className="mt-1 text-amber-700">
                  {orderSyncMutation.data.skipped.map((s, i) => (
                    <li key={i}>{s}</li>
                  ))}
                </ul>
              )}
              {orderSyncMutation.data.errors.length > 0 && (
                <p className="text-red-600 mt-1">{orderSyncMutation.data.errors.join("; ")}</p>
              )}
            </div>
          )}
        </div>

        <div className="bg-white border border-slate-200 rounded-lg p-5">
          <h3 className="font-medium text-slate-700 mb-1">Stock Levels</h3>
          <p className="text-xs text-slate-500 mb-3">
            Pushes available stock out to Shopify - one-way, the warehouse system stays authoritative. Weight
            pushes automatically whenever it's edited here; this covers the stock quantity, on a timer.
          </p>
          <p className="text-xs text-slate-400 mb-3">
            Last pushed: {status.lastStockPushedAt ? new Date(status.lastStockPushedAt).toLocaleString("en-GB") : "Never"}
          </p>
          <button
            onClick={() => stockPushMutation.mutate()}
            disabled={stockPushMutation.isPending || !status.connected}
            className="bg-slate-800 text-white text-sm px-4 py-2 rounded-md hover:bg-slate-700 disabled:opacity-50"
          >
            {stockPushMutation.isPending ? "Pushing..." : "Push Stock Now"}
          </button>
          {stockPushMutation.data && (
            <div className="text-xs text-slate-500 mt-2">
              <p>{stockPushMutation.data.pushed} product(s) pushed</p>
              {stockPushMutation.data.errors.length > 0 && (
                <p className="text-red-600 mt-1">{stockPushMutation.data.errors.join("; ")}</p>
              )}
            </div>
          )}
        </div>
      </div>

      {needsReview.length > 0 && (
        <div className="bg-white border border-amber-200 rounded-lg overflow-hidden">
          <div className="px-5 py-3 border-b border-amber-100 bg-amber-50">
            <h3 className="font-medium text-amber-800">Needs review ({needsReview.length})</h3>
            <p className="text-xs text-amber-700 mt-0.5">
              New from Shopify - defaulted to no tracking. Open each and set the correct tracking type (and default
              bin, if it has one) to clear this.
            </p>
          </div>
          <div className="divide-y divide-slate-100">
            {needsReview.map((p) => (
              <Link
                key={p.id}
                to="/products"
                className="flex justify-between items-center px-5 py-3 hover:bg-slate-50 text-sm"
              >
                <div>
                  <p className="font-medium text-slate-800">{p.sku}</p>
                  <p className="text-slate-500">{p.name}</p>
                </div>
                <span className="text-xs px-2 py-1 rounded-full bg-amber-100 text-amber-700">Review needed</span>
              </Link>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
