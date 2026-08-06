import api, { INVENTORY_BASE } from "./api";

export const getAllMedicines = () =>
  api.get(`${INVENTORY_BASE}/medicines`).then((res) => res.data);

export const getMedicinesByBranch = (branchId) =>
  api.get(`${INVENTORY_BASE}/medicines/branch/${branchId}`).then((res) => res.data);

/** `city` is optional; when set, the backend narrows results to branches in that city. */
export const checkAvailability = (medicineName, requiredQuantity, city) =>
  api
    .get(`${INVENTORY_BASE}/medicines/availability`, {
      params: {
        medicineName,
        requiredQuantity,
        ...(city ? { city } : {}),
      },
    })
    .then((res) => res.data);

export const addMedicine = (data) =>
  api.post(`${INVENTORY_BASE}/medicines`, data).then((res) => res.data);

export const deleteMedicine = (id) =>
  api.delete(`${INVENTORY_BASE}/medicines/${id}`).then((res) => res.data);