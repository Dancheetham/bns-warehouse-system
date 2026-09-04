import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { useState } from "react";
import { useAuth } from "../auth/AuthContext";

const navGroups: { heading: string; items: { to: string; label: string; end?: boolean }[] }[] = [
  {
    heading: "Overview",
    items: [{ to: "/", label: "Dashboard", end: true }],
  },
  {
    heading: "Sales",
    items: [
      { to: "/sales-activity", label: "Sales Activity" },
      { to: "/rmas", label: "RMAs" },
      { to: "/companies", label: "Companies" },
    ],
  },
  {
    heading: "Warehouse",
    items: [
      { to: "/products", label: "Products" },
      { to: "/purchase-orders", label: "Purchase Orders" },
      { to: "/goods-in", label: "Goods In" },
      { to: "/despatch", label: "Despatch" },
      { to: "/stock-movement", label: "Stock Movement" },
      { to: "/stock-overview", label: "Stock Overview" },
      { to: "/trace", label: "Stock Trace" },
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

const reportsChildren = [
  { to: "/reports/orders", label: "Order Reports" },
  { to: "/reports/stock", label: "Stock Reports" },
];

const linkClasses = ({ isActive }: { isActive: boolean }) =>
  `block px-4 py-2 text-sm ${
    isActive ? "bg-slate-700 text-white font-medium" : "text-slate-300 hover:bg-slate-800 hover:text-white"
  }`;

const headingClasses = "px-4 pt-4 pb-1 text-xs font-semibold uppercase tracking-wide text-slate-500";

export default function Layout() {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout } = useAuth();
  const reportsActive = location.pathname.startsWith("/reports");
  const [reportsOpen, setReportsOpen] = useState(reportsActive);

  const isGroupActive = (group: (typeof navGroups)[number]) =>
    group.items.some((item) => (item.end ? location.pathname === item.to : location.pathname.startsWith(item.to)));

  // Collapsed by default - the sidebar was getting genuinely busy as
  // features piled up - but a group auto-opens if you're currently on one
  // of its pages, same as Reports already did, so you're never landed on a
  // page with no visible indication of where you are in the nav.
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
          <p className="text-xs text-slate-400">System</p>
        </div>
        <nav className="flex-1 py-2">
          {navGroups.map((group) => (
            <div key={group.heading}>
              <button
                onClick={() => toggleGroup(group.heading)}
                className={`w-full flex justify-between items-center ${headingClasses} hover:text-slate-300`}
              >
                <span>{group.heading}</span>
                <span className="text-[10px]">{openGroups[group.heading] ? "▲" : "▼"}</span>
              </button>
              {openGroups[group.heading] &&
                group.items.map((item) => (
                  <NavLink key={item.to} to={item.to} end={item.end} className={linkClasses}>
                    {item.label}
                  </NavLink>
                ))}
            </div>
          ))}

          <p className={headingClasses}>Reports</p>
          <button
            onClick={() => setReportsOpen((v) => !v)}
            className={`w-full flex justify-between items-center px-4 py-2 text-sm ${
              reportsActive ? "bg-slate-700 text-white font-medium" : "text-slate-300 hover:bg-slate-800 hover:text-white"
            }`}
          >
            <span>Order &amp; Stock Reports</span>
            <span className="text-xs">{reportsOpen ? "▲" : "▼"}</span>
          </button>
          {reportsOpen && (
            <div className="bg-slate-950/40">
              {reportsChildren.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  className={({ isActive }) =>
                    `block pl-8 pr-4 py-2 text-sm ${
                      isActive ? "bg-slate-700 text-white font-medium" : "text-slate-400 hover:bg-slate-800 hover:text-white"
                    }`
                  }
                >
                  {item.label}
                </NavLink>
              ))}
            </div>
          )}
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
