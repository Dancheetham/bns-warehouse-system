import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "./AuthContext";

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
  const updated = [name, ...existing].slice(0, MAX_REMEMBERED);
  localStorage.setItem(REMEMBERED_ACCOUNTS_KEY, JSON.stringify(updated));
}

function forgetAccount(name: string) {
  const updated = getRememberedAccounts().filter((n) => n !== name);
  localStorage.setItem(REMEMBERED_ACCOUNTS_KEY, JSON.stringify(updated));
}

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [name, setName] = useState("");
  const [password, setPassword] = useState("");
  const [rememberAccountChecked, setRememberAccountChecked] = useState(true);
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
      if (rememberAccountChecked) {
        rememberAccount(name);
      } else {
        forgetAccount(name);
      }
      // login() only updates auth state - nothing about that state change on
      // its own moves the browser off /login, since this route isn't wrapped
      // in RequireAuth (it can't be, or a logged-out visitor could never see
      // it). Has to be done explicitly.
      navigate("/");
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
    <div className="min-h-screen bg-slate-900 flex items-center justify-center p-6">
      <div className="w-full max-w-sm">
        <div className="text-center mb-8">
          <h1 className="text-2xl font-semibold text-white">BNS Warehouse</h1>
          <p className="text-slate-400 text-sm mt-1">System</p>
        </div>

        <div className="bg-white rounded-xl shadow-xl p-6">
          {!pickedAccount && rememberedAccounts.length > 0 && (
            <div className="mb-5">
              <p className="text-xs font-medium text-slate-500 mb-2">Continue as</p>
              <div className="space-y-1.5">
                {rememberedAccounts.map((accountName) => (
                  <div key={accountName} className="flex items-center gap-2">
                    <button
                      onClick={() => pickAccount(accountName)}
                      className="flex-1 flex items-center gap-3 text-left px-3 py-2 rounded-lg border border-slate-200 hover:border-emerald-400 hover:bg-emerald-50 transition-colors"
                    >
                      <span className="w-8 h-8 rounded-full bg-slate-700 text-white flex items-center justify-center text-sm font-medium shrink-0">
                        {accountName.charAt(0).toUpperCase()}
                      </span>
                      <span className="text-sm font-medium text-slate-800">{accountName}</span>
                    </button>
                    <button
                      onClick={() => {
                        forgetAccount(accountName);
                        setRememberedAccounts(getRememberedAccounts());
                      }}
                      title="Forget this account on this device"
                      className="text-slate-300 hover:text-red-500 text-xs px-1"
                    >
                      ✕
                    </button>
                  </div>
                ))}
              </div>
              <div className="flex items-center gap-2 my-4">
                <div className="h-px bg-slate-200 flex-1" />
                <span className="text-xs text-slate-400">or</span>
                <div className="h-px bg-slate-200 flex-1" />
              </div>
              <button
                onClick={() => {
                  setPickedAccount("__other__");
                  setName("");
                }}
                className="text-sm text-slate-500 hover:text-slate-700"
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
                  className="text-xs text-slate-500 hover:text-slate-700 mb-4"
                >
                  ← Back
                </button>
              )}

              {error && <div className="bg-red-50 text-red-700 text-sm rounded-lg px-3 py-2 mb-4">{error}</div>}

              {(!pickedAccount || pickedAccount === "__other__") && (
                <div className="mb-4">
                  <label className="block text-xs font-medium text-slate-500 mb-1">Name</label>
                  <input
                    autoFocus
                    required
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    className="input"
                  />
                </div>
              )}

              <div className="mb-4">
                <label className="block text-xs font-medium text-slate-500 mb-1">Password</label>
                <input
                  autoFocus={!!pickedAccount && pickedAccount !== "__other__"}
                  required
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="input"
                />
              </div>

              <label className="flex items-center gap-2 text-sm text-slate-600 mb-5">
                <input
                  type="checkbox"
                  checked={rememberAccountChecked}
                  onChange={(e) => setRememberAccountChecked(e.target.checked)}
                />
                Remember this account on this device
              </label>

              <button
                type="submit"
                disabled={submitting}
                className="w-full bg-emerald-600 text-white py-2.5 rounded-lg font-medium hover:bg-emerald-500 disabled:opacity-50"
              >
                {submitting ? "Signing in..." : "Sign in"}
              </button>
            </form>
          )}
        </div>
      </div>
    </div>
  );
}
