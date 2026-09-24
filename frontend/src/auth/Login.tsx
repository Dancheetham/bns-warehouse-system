import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "./AuthContext";
import { api } from "../api/client";

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
  const [showForgotPassword, setShowForgotPassword] = useState(false);
  const [forgotValue, setForgotValue] = useState("");
  const [forgotSubmitting, setForgotSubmitting] = useState(false);
  const [forgotSent, setForgotSent] = useState(false);

  useEffect(() => {
    setRememberedAccounts(getRememberedAccounts());
  }, []);

  const submitForgotPassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setForgotSubmitting(true);
    try {
      await api.post("/auth/forgot-password", { usernameOrEmail: forgotValue });
    } catch {
      // Deliberately swallowed - the backend already responds identically
      // whether or not the name/email matched anything, so surfacing a
      // request error here specifically would itself leak information.
      // A genuinely broken deployment (no SMTP configured, say) is a "no
      // email arrives" problem to chase up with whoever administers the
      // system, not something this screen should hint at.
    } finally {
      setForgotSubmitting(false);
      setForgotSent(true);
    }
  };

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
          {showForgotPassword ? (
            forgotSent ? (
              <div className="text-center py-2">
                <p className="text-sm text-slate-600 mb-5">
                  If that account has an email address on file, a password reset link is on its way to it now -
                  it's valid for the next hour.
                </p>
                <button
                  type="button"
                  onClick={() => {
                    setShowForgotPassword(false);
                    setForgotSent(false);
                    setForgotValue("");
                  }}
                  className="text-sm text-emerald-600 hover:text-emerald-700 font-medium"
                >
                  ← Back to sign in
                </button>
              </div>
            ) : (
              <form onSubmit={submitForgotPassword}>
                <button
                  type="button"
                  onClick={() => setShowForgotPassword(false)}
                  className="text-xs text-slate-500 hover:text-slate-700 mb-4"
                >
                  ← Back
                </button>
                <p className="text-sm text-slate-500 mb-4">
                  Enter your login name or email address and we'll send a password reset link, if there's one on
                  file for that account.
                </p>
                <div className="mb-5">
                  <label className="block text-xs font-medium text-slate-500 mb-1">Name or email</label>
                  <input
                    autoFocus
                    required
                    value={forgotValue}
                    onChange={(e) => setForgotValue(e.target.value)}
                    className="input"
                  />
                </div>
                <button
                  type="submit"
                  disabled={forgotSubmitting}
                  className="w-full bg-emerald-600 text-white py-2.5 rounded-lg font-medium hover:bg-emerald-500 disabled:opacity-50"
                >
                  {forgotSubmitting ? "Sending..." : "Send reset link"}
                </button>
              </form>
            )
          ) : (
            <>
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

              <div className="flex items-center justify-between mb-5">
                <label className="flex items-center gap-2 text-sm text-slate-600">
                  <input
                    type="checkbox"
                    checked={rememberAccountChecked}
                    onChange={(e) => setRememberAccountChecked(e.target.checked)}
                  />
                  Remember this account on this device
                </label>
              </div>

              <button
                type="submit"
                disabled={submitting}
                className="w-full bg-emerald-600 text-white py-2.5 rounded-lg font-medium hover:bg-emerald-500 disabled:opacity-50"
              >
                {submitting ? "Signing in..." : "Sign in"}
              </button>

              <button
                type="button"
                onClick={() => {
                  setShowForgotPassword(true);
                  setForgotValue(name);
                  setError(null);
                }}
                className="w-full text-center text-sm text-slate-500 hover:text-slate-700 mt-4"
              >
                Forgot password?
              </button>
            </form>
          )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
