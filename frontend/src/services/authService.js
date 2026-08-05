import api, { AUTH_BASE } from "./api";

export const login = (username, password) =>
  api.post(`${AUTH_BASE}/login`, { username, password }).then((res) => res.data);

export const register = (data) =>
  api.post(`${AUTH_BASE}/register`, data).then((res) => res.data);

export const changePassword = (data) =>
  api.put(`${AUTH_BASE}/change-password`, data).then((res) => res.data);

// Not explicitly listed in the services spec, but WarehouseCodeManagementPage
// needs these and they belong to auth-service's warehouse-codes endpoints.
export const generateWarehouseCode = (data) =>
  api.post(`${AUTH_BASE}/warehouse-codes`, data || {}).then((res) => res.data);

export const getAllWarehouseCodes = () =>
  api.get(`${AUTH_BASE}/warehouse-codes`).then((res) => res.data);