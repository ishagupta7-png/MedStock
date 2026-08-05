import { createContext, useContext, useEffect, useState } from "react";
import * as authService from "../services/authService";
import * as branchService from "../services/branchService";

const AuthContext = createContext(null);

const readStoredAuth = () => ({
  token: localStorage.getItem("token"),
  username: localStorage.getItem("username"),
  role: localStorage.getItem("role"),
  branchId: localStorage.getItem("branchId"),
  branchName: localStorage.getItem("branchName"),
});

/**
 * The branch label is cosmetic, so a failure here must never block signing in or using the app -
 * it just falls back to showing the raw branch id.
 */
const resolveBranchName = async (branchId) => {
  if (branchId === null || branchId === undefined || branchId === "") {
    return "";
  }
  try {
    const branch = await branchService.getBranch(branchId);
    return branch?.branchName || "";
  } catch {
    return "";
  }
};

export function AuthProvider({ children }) {
  const [auth, setAuth] = useState(readStoredAuth);

  const login = async (username, password) => {
    const data = await authService.login(username, password);
    localStorage.setItem("token", data.token);
    localStorage.setItem("username", data.username);
    localStorage.setItem("role", data.role);
    localStorage.setItem("branchId", data.branchId ?? "");

    // Token is already stored, so the axios interceptor can authenticate this lookup.
    const branchName = await resolveBranchName(data.branchId);
    localStorage.setItem("branchName", branchName);

    setAuth({
      token: data.token,
      username: data.username,
      role: data.role,
      branchId: data.branchId ?? "",
      branchName,
    });
    return data.role;
  };

  // Backfills the branch name for sessions that predate it being stored (or where the lookup
  // failed at login), so the header still identifies the branch after a refresh.
  useEffect(() => {
    if (!auth.token || !auth.branchId || auth.branchName) {
      return;
    }
    let cancelled = false;
    resolveBranchName(auth.branchId).then((branchName) => {
      if (cancelled || !branchName) {
        return;
      }
      localStorage.setItem("branchName", branchName);
      setAuth((prev) => ({ ...prev, branchName }));
    });
    return () => {
      cancelled = true;
    };
  }, [auth.token, auth.branchId, auth.branchName]);

  const logout = () => {
    localStorage.removeItem("token");
    localStorage.removeItem("username");
    localStorage.removeItem("role");
    localStorage.removeItem("branchId");
    localStorage.removeItem("branchName");
    setAuth({ token: null, username: null, role: null, branchId: null, branchName: null });
  };

  return (
    <AuthContext.Provider value={{ ...auth, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
