import api, { ALERT_BASE } from "./api";

export const getAllAlerts = () =>
  api.get(`${ALERT_BASE}/alerts`).then((res) => res.data);

export const getAlertsByBranch = (branchId) =>
  api.get(`${ALERT_BASE}/alerts/branch/${branchId}`).then((res) => res.data);

export const resolveAlert = (id) =>
  api.put(`${ALERT_BASE}/alerts/${id}/resolve`).then((res) => res.data);