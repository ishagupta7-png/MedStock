import axios from "axios";

const GATEWAY_BASE = "http://localhost:8080";

// NOTE: gateway routing currently has issues. Each service's base URL is
// exported below with the gateway version active and a commented-out
// direct-service-port fallback, so you can swap quickly during development.

// const AUTH_BASE = "http://localhost:8083/api/auth"; // direct fallback
export const AUTH_BASE = "http://localhost:8080/api/auth"; // via gateway (default)

// const BRANCH_BASE = "http://localhost:8084/api/branch"; // direct fallback
export const BRANCH_BASE = "http://localhost:8080/api/branch"; // via gateway (default)

// const INVENTORY_BASE = "http://localhost:8081/api/inventory"; // direct fallback
export const INVENTORY_BASE = "http://localhost:8080/api/inventory"; // via gateway (default)

// const TRANSFER_BASE = "http://localhost:8082/api/transfer"; // direct fallback
export const TRANSFER_BASE = "http://localhost:8080/api/transfer"; // via gateway (default)

// const ALERT_BASE = "http://localhost:8085/api/alert"; // direct fallback
export const ALERT_BASE = "http://localhost:8080/api/alert"; // via gateway (default)

const api = axios.create({
  baseURL: GATEWAY_BASE,
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem("token");
      localStorage.removeItem("username");
      localStorage.removeItem("role");
      localStorage.removeItem("branchId");
      localStorage.removeItem("branchName");
      window.location.href = "/login";
    }
    return Promise.reject(error);
  }
);

export default api;