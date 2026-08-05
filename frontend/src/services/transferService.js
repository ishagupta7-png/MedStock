import api, { TRANSFER_BASE } from "./api";

export const getAllRequests = () =>
  api.get(`${TRANSFER_BASE}/requests`).then((res) => res.data);

export const getRequestsByStatus = (status) =>
  api.get(`${TRANSFER_BASE}/requests`, { params: { status } }).then((res) => res.data);

export const getRequestsByBranch = (branchId) =>
  api.get(`${TRANSFER_BASE}/requests/branch/${branchId}`).then((res) => res.data);

export const getOpenRequestsForBranch = (branchId) =>
  api.get(`${TRANSFER_BASE}/requests/open/${branchId}`).then((res) => res.data);

export const createRequest = (data) =>
  api.post(`${TRANSFER_BASE}/requests`, data).then((res) => res.data);

export const approveRequest = (id) =>
  api.put(`${TRANSFER_BASE}/requests/${id}/approve`).then((res) => res.data);

export const rejectRequest = (id) =>
  api.put(`${TRANSFER_BASE}/requests/${id}/reject`).then((res) => res.data);

export const deleteRequest = (id) =>
  api.delete(`${TRANSFER_BASE}/requests/${id}`).then((res) => res.data);