import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { useState } from "react";
import { useAuth } from "../auth/AuthContext";
import { APP_VERSION } from "../version";

const dashboardLink = { to: "/", label: "Dashboard", end: true };

const navGroups: { heading: string; items: { to: string; label: string; end?: boolean }[] }[] = [
  {
    heading: "Sales",
    items: [
      { to: "/sales-activity", label: "Sales Activity" },
      { to: "/delivery-history", label: "Delivery History" },
      { to: "/purchase-orders", label: "Purchase Orders" },
      { to: "/companies", label: "Companies" },
    ],
  },
  {
    heading: "CRM",
    items: [
      { to: "/tickets", label: "Support Tickets" },
      { to: "/contacts", label: "Contacts" },
      { to: "/rmas", label: "RMAs" },
    ],
  },
  {
    heading: "Warehouse",
    items: [
      { to: "/products", label: "Products" },
      { to: "/goods-in", label: "Goods In" },
      { to: "/despatch", label: "Despatch" },
      { to: "/stock-movement", label: "Stock Movement" },
      { to: "/stock-overview", label: "Stock Overview" },
      { to: "/trace", label: "Stock Trace" },
    ],
  },
  {
    heading: "Invoicing",
    items: [
      { to: "/invoicing/generate", label: "Generate Invoices" },
      { to: "/invoicing/payment-tracking", label: "Payment Tracking" },
      { to: "/invoicing/history", label: "Invoice History" },
    ],
  },
  {
    heading: "Reports",
    items: [
      { to: "/reports/orders", label: "Order Reports" },
      { to: "/reports/stock", label: "Stock Reports" },
      { to: "/reports/invoices", label: "Invoice Reports" },
    ],
  },
  {
    heading: "Admin",
    items: [
      { to: "/api-access", label: "API Access" },
      { to: "/shopify-sync", label: "Shopify Sync" },
      { to: "/bug-reports", label: "Bug Reports" },
      { to: "/settings", label: "Settings" },
    ],
  },
];

function isItemActive(item: { to: string; end?: boolean }, pathname: string) {
  return item.end ? pathname === item.to : pathname.startsWith(item.to);
}

export default function Layout() {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout } = useAuth();

  const isGroupActive = (group: (typeof navGroups)[number]) =>
    group.items.some((item) => isItemActive(item, location.pathname));

  // Collapsed by default - the sidebar was getting genuinely busy as
  // features piled up - but a group auto-opens if you're currently on one
  // of its pages, so you're never landed on a page with no visible
  // indication of where you are in the nav.
  const [openGroups, setOpenGroups] = useState<Record<string, boolean>>(() =>
    Object.fromEntries(navGroups.map((g) => [g.heading, isGroupActive(g)]))
  );

  const toggleGroup = (heading: string) => {
    setOpenGroups((prev) => ({ ...prev, [heading]: !prev[heading] }));
  };

  const handleLogout = async () => {
    await logout();
    navigate("/login");
  };

  return (
    <div className="h-screen flex overflow-hidden">
      <aside className="w-56 bg-slate-900 text-slate-100 flex flex-col shrink-0 h-full overflow-y-auto">
        <div className="px-4 py-5 border-b border-slate-700">
          <h1 className="text-lg font-semibold">BNS Warehouse</h1>
          <p className="text-xs text-slate-400">
            System - Version: <span className="text-slate-500">{APP_VERSION}</span>
          </p>
        </div>
        <nav className="flex-1 py-2">
          <NavLink
            to={dashboardLink.to}
            end={dashboardLink.end}
            className={({ isActive }) =>
              `block px-4 py-2.5 text-sm font-medium border-b border-slate-800 ${
                isActive ? "bg-slate-800 text-white" : "text-slate-200 hover:bg-slate-800 hover:text-white"
              }`
            }
          >
            {dashboardLink.label}
          </NavLink>

          {navGroups.map((group) => {
            const active = isGroupActive(group);
            const open = openGroups[group.heading];
            return (
              <div key={group.heading} className={open ? "bg-slate-900" : ""}>
                {/* No distinguishing background for the open state - confirmed
                    as the actual preference after ruling out a build/caching
                    issue as the cause of an earlier colour looking wrong. The
                    active-group heading colour and the active-row dot marker
                    below are what carry the "where am I" signal instead. */}
                <button
                  onClick={() => toggleGroup(group.heading)}
                  className={`w-full flex justify-between items-center px-4 pt-4 pb-1 text-xs font-semibold uppercase tracking-wide ${
                    active ? "text-emerald-400" : "text-slate-500 hover:text-slate-300"
                  }`}
                >
                  <span>{group.heading}</span>
                  <span className="text-[10px]">{open ? "▲" : "▼"}</span>
                </button>
                {open &&
                  group.items.map((item) => {
                    const itemActive = isItemActive(item, location.pathname);
                    return (
                      <NavLink
                        key={item.to}
                        to={item.to}
                        end={item.end}
                        className={`flex items-center gap-2 px-4 py-2 text-sm ${
                          itemActive ? "bg-slate-700 text-white font-medium" : "text-slate-300 hover:bg-slate-800 hover:text-white"
                        }`}
                      >
                        {/* Dot's space is always reserved (rendered either way, just
                            transparent when inactive) so the label never shifts
                            depending on which row is active. */}
                        <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${itemActive ? "bg-emerald-500" : "bg-transparent"}`} />
                        <span>{item.label}</span>
                      </NavLink>
                    );
                  })}
              </div>
            );
          })}
        </nav>
        {user && (
          <div className="border-t border-slate-700 px-4 py-3 flex items-center gap-2.5 shrink-0">
            <span className="w-8 h-8 rounded-full bg-slate-700 text-white flex items-center justify-center text-sm font-medium shrink-0">
              {user.name.charAt(0).toUpperCase()}
            </span>
            <div className="flex-1 min-w-0">
              <p className="text-sm text-slate-200 truncate">{user.name}</p>
              <button onClick={handleLogout} className="text-xs text-slate-500 hover:text-slate-300">
                Log out
              </button>
            </div>
          </div>
        )}
      </aside>
      <main className="flex-1 bg-slate-50 min-w-0 h-full overflow-y-auto">
        <div className="max-w-[1600px] mx-auto p-6">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
