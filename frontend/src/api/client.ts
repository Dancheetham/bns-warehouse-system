import axios from "axios";

export const api = axios.create({
  baseURL: "/api",
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const message =
      error?.response?.data?.message || error?.message || "Something went wrong";
    const status = error?.response?.status;
    const url: string = error?.config?.url ?? "";
    const method: string = (error?.config?.method ?? "").toUpperCase();

    // Auto-log to Bug Reports, but never for the bug-reports endpoint itself -
    // a failure there shouldn't try to log itself and loop.
    if (!url.includes("/bug-reports")) {
      axios
        .post("/api/bug-reports", {
          description: message,
          errorCode: status ? String(status) : "NETWORK",
          context: `${method} ${url}`,
          source: "AUTO",
        })
        .catch(() => {
          // if logging itself fails (e.g. backend fully unreachable), there's
          // nothing more useful to do here - don't compound the error
        });
    }

    return Promise.reject(new Error(message));
  }
);
