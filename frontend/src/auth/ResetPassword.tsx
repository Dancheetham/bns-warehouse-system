import { useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { api } from "../api/client";

/**
 * Reached from the link in a password-reset email (PasswordResetService) -
 * ?token=... identifies the reset request. Deliberately doesn't try to
 * validate the token up front with a separate call; POSTing it straight to
 * /auth/reset-password on submit is simpler and there's no meaningful
 * "check it's valid, then use it" gap to close since it's single-use either way.
 */
export default function ResetPassword() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token") ?? "";
  const navigate = useNavigate();
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (newPassword !== confirmPassword) {
      setError("Those two passwords don't match.");
      return;
    }
    setSubmitting(true);
    try {
      await api.post("/auth/reset-password", { token, newPassword });
      setDone(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Couldn't reset your password");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-900 flex items-center justify-center p-6">
      <div className="w-full max-w-sm">
        <div className="text-center mb-8">
          <h1 className="text-2xl font-semibold text-white">BNS Warehouse</h1>
          <p className="text-slate-400 text-sm mt-1">Reset your password</p>
        </div>

        <div className="bg-white rounded-xl shadow-xl p-6">
          {!token ? (
            <p className="text-sm text-slate-600">
              This link is missing its reset code - copy the full link from the email again, or request a new one
              from the sign-in page.
            </p>
          ) : done ? (
            <div className="text-center py-2">
              <p className="text-sm text-slate-600 mb-5">Your password has been reset - you can sign in with it now.</p>
              <button
                type="button"
                onClick={() => navigate("/login")}
                className="w-full bg-emerald-600 text-white py-2.5 rounded-lg font-medium hover:bg-emerald-500"
              >
                Go to sign in
              </button>
            </div>
          ) : (
            <form onSubmit={submit}>
              {error && <div className="bg-red-50 text-red-700 text-sm rounded-lg px-3 py-2 mb-4">{error}</div>}

              <div className="mb-4">
                <label className="block text-xs font-medium text-slate-500 mb-1">New password</label>
                <input
                  autoFocus
                  required
                  type="password"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  className="input"
                />
              </div>

              <div className="mb-5">
                <label className="block text-xs font-medium text-slate-500 mb-1">Confirm new password</label>
                <input
                  required
                  type="password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  className="input"
                />
              </div>

              <button
                type="submit"
                disabled={submitting}
                className="w-full bg-emerald-600 text-white py-2.5 rounded-lg font-medium hover:bg-emerald-500 disabled:opacity-50"
              >
                {submitting ? "Resetting..." : "Reset password"}
              </button>
            </form>
          )}
        </div>
      </div>
    </div>
  );
}
