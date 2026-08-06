import { useEffect, useState } from "react";
import { useAuth } from "../../context/AuthContext";
import * as inventoryService from "../../services/inventoryService";
import { LoadingState, ButtonBusy } from "../../components/Spinner";

export default function StockManagementPage() {
  const { branchId } = useAuth();
  const [medicines, setMedicines] = useState([]);
  const [medicineName, setMedicineName] = useState("");
  const [batchNumber, setBatchNumber] = useState("");
  const [quantity, setQuantity] = useState("");
  const [unitPrice, setUnitPrice] = useState("");
  const [expiryDate, setExpiryDate] = useState("");
  const [avgDailyConsumption, setAvgDailyConsumption] = useState("");
  const [error, setError] = useState("");
  // isLoading gates the table's own fetch; the add form needs its own flag, or submitting would
  // blank out the table underneath it.
  const [isLoading, setIsLoading] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [deletingId, setDeletingId] = useState(null);

  const loadMedicines = async () => {
    setIsLoading(true);
    try {
      const data = await inventoryService.getMedicinesByBranch(branchId);
      setMedicines(data);
    } catch (err) {
      setError(err.response?.data?.message || "Failed to load stock.");
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadMedicines();
  }, [branchId]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");
    setIsSubmitting(true);
    try {
      // No branchName: it is resolved from branch-service against branchId. This used to send the
      // logged-in username, which then showed up as the holding branch on transfer requests.
      await inventoryService.addMedicine({
        medicineName,
        batchNumber,
        branchId: Number(branchId),
        quantity: Number(quantity),
        unitPrice: Number(unitPrice),
        expiryDate,
        avgDailyConsumption: Number(avgDailyConsumption),
      });
      setMedicineName("");
      setBatchNumber("");
      setQuantity("");
      setUnitPrice("");
      setExpiryDate("");
      setAvgDailyConsumption("");
      await loadMedicines();
    } catch (err) {
      setError(err.response?.data?.message || "Failed to add medicine.");
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDelete = async (id) => {
    setError("");
    setDeletingId(id);
    try {
      await inventoryService.deleteMedicine(id);
      await loadMedicines();
    } catch (err) {
      setError(err.response?.data?.message || "Failed to delete medicine.");
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <div className="page-container">
      <div className="page-header">
        <h1 className="page-title">Stock Management</h1>
      </div>

      <div className="card">
        <form onSubmit={handleSubmit} className="form-grid">
          <div className="form-group">
            <label>Medicine Name</label>
            <input
              value={medicineName}
              onChange={(e) => setMedicineName(e.target.value)}
              required
            />
          </div>
          <div className="form-group">
            <label>Batch Number</label>
            <input
              value={batchNumber}
              onChange={(e) => setBatchNumber(e.target.value)}
              required
            />
          </div>
          <div className="form-group">
            <label>Quantity</label>
            {/* min="1" mirrors the server's @Positive: a zero-unit batch advertises nothing. */}
            <input
              type="number"
              min="1"
              value={quantity}
              onChange={(e) => setQuantity(e.target.value)}
              required
            />
          </div>
          <div className="form-group">
            <label>Unit Price</label>
            <input
              type="number"
              min="0"
              step="0.01"
              value={unitPrice}
              onChange={(e) => setUnitPrice(e.target.value)}
              required
            />
          </div>
          <div className="form-group">
            <label>Expiry Date</label>
            <input
              type="date"
              value={expiryDate}
              onChange={(e) => setExpiryDate(e.target.value)}
              required
            />
          </div>
          <div className="form-group">
            <label>Avg Daily Consumption</label>
            <input
              type="number"
              min="0"
              step="0.01"
              value={avgDailyConsumption}
              onChange={(e) => setAvgDailyConsumption(e.target.value)}
              required
            />
          </div>
          <button type="submit" className="btn-primary" disabled={isSubmitting}>
            {isSubmitting ? <ButtonBusy label="Adding..." /> : "Add Medicine"}
          </button>
        </form>

        {error && <div className="alert-error">{error}</div>}
      </div>

      <div className="card">
        {isLoading ? (
          <LoadingState label="Loading stock..." />
        ) : (
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Medicine</th>
                <th>Batch</th>
                <th>Quantity</th>
                <th>Unit Price</th>
                <th>Expiry Date</th>
                <th>Avg Daily Consumption</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {medicines.length === 0 ? (
                <tr>
                  <td colSpan="8">
                    <div className="empty-state">No stock recorded yet.</div>
                  </td>
                </tr>
              ) : (
                medicines.map((m) => (
                  <tr key={m.id}>
                    <td>{m.id}</td>
                    <td>{m.medicineName}</td>
                    <td>{m.batchNumber}</td>
                    <td>{m.quantity}</td>
                    <td>{m.unitPrice}</td>
                    <td>{m.expiryDate}</td>
                    <td>{m.avgDailyConsumption}</td>
                    <td>
                      <button
                        type="button"
                        className="btn-danger"
                        disabled={deletingId === m.id}
                        onClick={() => handleDelete(m.id)}
                      >
                        {deletingId === m.id ? (
                          <ButtonBusy label="Deleting..." tone="inherit" />
                        ) : (
                          "Delete"
                        )}
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}