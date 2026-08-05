import api, { BRANCH_BASE } from "./api";

export const getAllBranches = () =>
  api.get(`${BRANCH_BASE}/branches`).then((res) => res.data);

export const getBranch = (id) =>
  api.get(`${BRANCH_BASE}/branches/${id}`).then((res) => res.data);

export const createBranch = (data) =>
  api.post(`${BRANCH_BASE}/branches`, data).then((res) => res.data);

export const deleteBranch = (id) =>
  api.delete(`${BRANCH_BASE}/branches/${id}`).then((res) => res.data);