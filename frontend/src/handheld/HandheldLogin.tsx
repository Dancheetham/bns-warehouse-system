import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

const REMEMBERED_ACCOUNTS_KEY = "bns_remembered_accounts";
const MAX_REMEMBERED = 6;

function getRememberedAccounts(): string[] {
  try {
    const raw = localStorage.getItem(REMEMBERED_ACCOUNTS_KEY);
    return raw ? (JSON.parse(raw) as string[]) : [];
  } catch {
    return [];
  }
}

function rememberAccount(name: string) {
  const existing = getRememberedAccounts().filter((n) => n !== name);
  localStorage.setItem(REMEMBERED_ACCOUNTS_KEY, JSON.stringify([name, ...existing].slice(0, MAX_REMEMBERED)));
}

// Same accounts list as the desktop login (shared localStorage key) - someone
// who's used both on the same device sees the same quick-pick names either way.
export default function HandheldLogin() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [name, setName] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [rememberedAccounts, setRememberedAccounts] = useState<string[]>([]);
  const [pickedAccount, setPickedAccount] = useState<string | null>(null);

  useEffect(() => {
    setRememberedAccounts(getRememberedAccounts());
  }, []);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(name, password);
      rememberAccount(name);
      navigate("/handheld");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Login failed");
    } finally {
      setSubmitting(false);
    }
  };

  const pickAccount = (accountName: string) => {
    setPickedAccount(accountName);
    setName(accountName);
    setPassword("");
    setError(null);
  };

  return (
    <div className="min-h-screen bg-slate-950 text-white flex flex-col">
      <header className="px-4 py-5 border-b border-slate-800">
        <h1 className="text-xl font-bold">BNS Warehouse</h1>
        <p className="text-sm text-slate-400">Handheld</p>
      </header>

      <div className="flex-1 flex flex-col justify-center p-4">
        {!pickedAccount && rememberedAccounts.length > 0 && (
          <div className="mb-2">
            <p className="text-sm text-slate-400 mb-3">Who's scanning?</p>
            <div className="space-y-2 mb-4">
              {rememberedAccounts.map((accountName) => (
                <button
                  key={accountName}
                  onClick={() => pickAccount(accountName)}
                  className="w-full flex items-center gap-3 bg-slate-900 border border-slate-800 rounded-2xl p-4 text-left active:bg-slate-800"
                >
                  <span className="w-10 h-10 rounded-full bg-slate-700 flex items-center justify-center text-lg font-medium shrink-0">
                    {accountName.charAt(0).toUpperCase()}
                  </span>
                  <span className="text-lg font-medium">{accountName}</span>
                </button>
              ))}
            </div>
            <button
              onClick={() => setPickedAccount("__other__")}
              className="text-sm text-slate-400 active:text-slate-200"
            >
              Use a different login
            </button>
          </div>
        )}

        {(pickedAccount || rememberedAccounts.length === 0) && (
          <form onSubmit={submit}>
            {pickedAccount && pickedAccount !== "__other__" && (
              <button
                type="button"
                onClick={() => {
                  setPickedAccount(null);
                  setName("");
                  setPassword("");
                }}
                className="text-sm text-slate-400 active:text-slate-200 mb-4"
              >
                ← Back
              </button>
            )}

            {error && <div className="bg-red-950 text-red-300 text-sm rounded-xl px-4 py-3 mb-4">{error}</div>}

            {(!pickedAccount || pickedAccount === "__other__") && (
              <div className="mb-4">
                <label className="block text-sm text-slate-400 mb-2">Name</label>
                <input
                  autoFocus
                  required
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  className="w-full bg-slate-900 border border-slate-800 rounded-xl px-4 py-3 text-lg text-white"
                />
              </div>
            )}

            <div className="mb-6">
              <label className="block text-sm text-slate-400 mb-2">Password</label>
              <input
                autoFocus={!!pickedAccount && pickedAccount !== "__other__"}
                required
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full bg-slate-900 border border-slate-800 rounded-xl px-4 py-3 text-lg text-white"
              />
            </div>

            <button
              type="submit"
              disabled={submitting}
              className="w-full bg-emerald-600 text-white py-4 rounded-xl text-lg font-medium active:bg-emerald-500 disabled:opacity-50"
            >
              {submitting ? "Signing in..." : "Sign in"}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
