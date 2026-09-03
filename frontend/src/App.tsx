import { Routes, Route, Navigate, useParams, Outlet } from "react-router-dom";
import { AuthProvider, useAuth } from "./auth/AuthContext";
import Login from "./auth/Login";
import Layout from "./components/Layout";
import Dashboard from "./pages/Dashboard";
import Products from "./pages/Products";
import Companies from "./pages/Companies";
import ProductDetail from "./pages/ProductDetail";
import PurchaseOrders from "./pages/PurchaseOrders";
import PurchaseOrderDetail from "./pages/PurchaseOrderDetail";
import GoodsIn from "./pages/GoodsIn";
import StockMovement from "./pages/StockMovement";
import StockOverview from "./pages/StockOverview";
import StockTrace from "./pages/StockTrace";
import ApiAccess from "./pages/ApiAccess";
import ReportsOrders from "./pages/ReportsOrders";
import ReportsStock from "./pages/ReportsStock";
import BugReports from "./pages/BugReports";
import SalesActivity from "./pages/SalesActivity";
import OrderEdit from "./pages/OrderEdit";
import Settings from "./pages/Settings";
import ShopifySync from "./pages/ShopifySync";
import Despatch from "./pages/Despatch";
import Packing from "./pages/Packing";
import HandheldHome from "./handheld/HandheldHome";
import HandheldStockMovement from "./handheld/HandheldStockMovement";
import HandheldLogin from "./handheld/HandheldLogin";
import PickOrderList from "./handheld/PickOrderList";
import PickOrder from "./handheld/PickOrder";
import GoodsInStart from "./handheld/GoodsInStart";
import GoodsInScan from "./handheld/GoodsInScan";
import RmaRequestForm from "./public/RmaRequestForm";
import RmaInbox from "./pages/RmaInbox";
import RmaDetail from "./pages/RmaDetail";

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        {/* Public, unauthenticated - the RMA request form customers use
            directly, and both login pages. */}
        <Route path="/rma" element={<RmaRequestForm />} />
        <Route path="/login" element={<Login />} />
        <Route path="/handheld/login" element={<HandheldLogin />} />

        <Route element={<RequireHandheldAuth />}>
          {/* Standalone handheld app - deliberately outside the main Layout (no
              sidebar, dark full-screen UI) since it runs on a Zebra scanner, not
              alongside the desk-bound screens below. Its own login page too,
              matching that same look - a device that's locked down to just this
              URL should never land on the desktop-styled login. */}
          <Route path="/handheld" element={<HandheldHome />} />
          <Route path="/handheld/pick" element={<PickOrderList />} />
          <Route path="/handheld/pick/:orderId" element={<PickOrder />} />
          <Route path="/handheld/goods-in" element={<GoodsInStart />} />
          <Route path="/handheld/goods-in/:sessionId" element={<GoodsInScan />} />
          <Route path="/handheld/stock-movement" element={<HandheldStockMovement />} />

          {/* The handheld app used to live at /pick before Goods In moved in and
              everything got grouped under /handheld - redirect anyone with the old
              URL bookmarked instead of silently showing a blank page. */}
          <Route path="/pick" element={<Navigate to="/handheld/pick" replace />} />
          <Route path="/pick/:orderId" element={<RedirectToPickOrder />} />
        </Route>

        <Route element={<RequireAuth />}>
          <Route element={<Layout />}>
            <Route path="/" element={<Dashboard />} />
            <Route path="/sales-activity" element={<SalesActivity />} />
            <Route path="/sales-activity/:id" element={<OrderEdit />} />
            <Route path="/products" element={<Products />} />
            <Route path="/products/:id" element={<ProductDetail />} />
            <Route path="/purchase-orders" element={<PurchaseOrders />} />
            <Route path="/purchase-orders/:id" element={<PurchaseOrderDetail />} />
            <Route path="/goods-in" element={<GoodsIn />} />
            <Route path="/despatch" element={<Despatch />} />
            <Route path="/despatch/:orderId" element={<Packing />} />
            <Route path="/rmas" element={<RmaInbox />} />
            <Route path="/companies" element={<Companies />} />
            <Route path="/rmas/:id" element={<RmaDetail />} />
            <Route path="/stock-movement" element={<StockMovement />} />
            <Route path="/stock-overview" element={<StockOverview />} />
            <Route path="/trace" element={<StockTrace />} />
            <Route path="/api-access" element={<ApiAccess />} />
            <Route path="/reports/orders" element={<ReportsOrders />} />
            <Route path="/reports/stock" element={<ReportsStock />} />
            <Route path="/bug-reports" element={<BugReports />} />
            <Route path="/settings" element={<Settings />} />
            <Route path="/shopify-sync" element={<ShopifySync />} />
          </Route>
        </Route>

        {/* Catch-all - an unmatched path used to render nothing at all (React
            Router renders null with no route match), which looks exactly like a
            broken page rather than a wrong URL. This makes that visible instead. */}
        <Route path="*" element={<NotFound />} />
      </Routes>
    </AuthProvider>
  );
}

function RequireAuth() {
  const { user, isLoading } = useAuth();
  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50 text-slate-400 text-sm">
        Loading...
      </div>
    );
  }
  if (!user) return <Navigate to="/login" replace />;
  return <Outlet />;
}

function RequireHandheldAuth() {
  const { user, isLoading } = useAuth();
  if (isLoading) {
    return <div className="min-h-screen flex items-center justify-center bg-slate-950 text-slate-500 text-sm">Loading...</div>;
  }
  if (!user) return <Navigate to="/handheld/login" replace />;
  return <Outlet />;
}

function RedirectToPickOrder() {
  const { orderId } = useParams();
  return <Navigate to={`/handheld/pick/${orderId}`} replace />;
}

function NotFound() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50 text-center p-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-800 mb-2">Page not found</h1>
        <p className="text-slate-500 mb-4">There's nothing at this URL.</p>
        <div className="flex gap-3 justify-center text-sm">
          <a href="/" className="text-emerald-600 hover:underline">
            Go to the main app
          </a>
          <a href="/handheld" className="text-emerald-600 hover:underline">
            Go to the handheld app
          </a>
        </div>
      </div>
    </div>
  );
}
