import axios from "axios";

export const api = axios.create({
  baseURL: "/api",
  // Without this, axios doesn't send the session cookie on any request -
  // unlike fetch(), which sends same-origin cookies by default. Missing this
  // meant login would succeed on the backend but every subsequent call
  // (starting with the very next "am I logged in?" check) would look
  // unauthenticated, silently undoing a login that had actually worked.
  withCredentials: true,
});

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    // A request made with responseType "blob" (PDF/label downloads) gets its
    // error body back as a Blob too, not parsed JSON - so error.response.data.message
    // is silently undefined even though the server sent a perfectly good
    // error message, and everyone just sees a generic "Request failed with
    // status code 400" instead. Read it back out as text and parse it here
    // once, rather than every caller having to remember to do this.
    if (error?.response?.data instanceof Blob && error.response.data.type?.includes("json")) {
      try {
        const text = await error.response.data.text();
        error.response.data = JSON.parse(text);
      } catch {
        // leave error.response.data as the Blob - fall through to the generic message below
      }
    } else if (typeof error?.response?.data === "string" && error.response.data.trim().startsWith("{")) {
      // Same problem, one step removed: a request made with responseType
      // "text" gets an error body back as a raw (unparsed) string instead of
      // an object, so .message is likewise silently undefined.
      try {
        error.response.data = JSON.parse(error.response.data);
      } catch {
        // leave error.response.data as the string - fall through to the generic message below
      }
    }

    const message =
      error?.response?.data?.message || error?.message || "Something went wrong";
    const status = error?.response?.status;
    const url: string = error?.config?.url ?? "";
    const method: string = (error?.config?.method ?? "").toUpperCase();

    // Auto-log to Bug Reports, but never for the bug-reports endpoint itself (a
    // failure there shouldn't try to log itself and loop), 401s (routine "not
    // logged in" responses - e.g. checking auth state on page load), or 409s
    // (an optimistic-lock conflict - two people editing the same thing is
    // expected, handled behaviour, not a bug).
    if (!url.includes("/bug-reports") && status !== 401 && status !== 409) {
      axios
        .post("/api/bug-reports", {
          description: message,
          errorCode: status ? String(status) : "NETWORK",
          context: `${method} ${url}`,
          source: "AUTO",
        }, { withCredentials: true })
        .catch(() => {
          // if logging itself fails (e.g. backend fully unreachable), there's
          // nothing more useful to do here - don't compound the error
        });
    }

    const enrichedError = new Error(message) as Error & { status?: number };
    enrichedError.status = status;
    return Promise.reject(enrichedError);
  }
);
