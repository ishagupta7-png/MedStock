import { useEffect, useState } from "react";
import { useAuth } from "../../context/AuthContext";
import * as alertService from "../../services/alertService";

export default function AlertsPage() {
  const { branchId } = useAuth();
  const [alerts, setAlerts] = useState([]);
  const [error, setError] = useState("");
  const [isLoading, setIsLoading] = useState(false);

  const loadAlerts = async () => {
    setIsLoading(true);
    try {
      const data = await alertService.getAlertsByBranch(branchId);
      setAlerts(data);
    } catch (err) {
      setError(err.response?.data?.message || "Failed to load alerts.");
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadAlerts();
  }, [branchId]);

  const handleResolve = async (id) => {
    try {
      await alertService.resolveAlert(id);
      loadAlerts();
    } catch (err) {
      setError(err.response?.data?.message || "Failed to resolve alert.");
    }
  };

  return (
    <div className="page-container">
      <div className="page-header">
        <h1 className="page-title">Restock Alerts</h1>
      </div>

      {error && <div className="alert-error">{error}</div>}

      <div className="card">
        {isLoading ? (
          <div className="loading-text">Loading...</div>
        ) : (
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Medicine</th>
                <th>Days Remaining</th>
                <th>Status</th>
                <th>Created At</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {alerts.length === 0 ? (
                <tr>
                  <td colSpan="6">
                    <div className="empty-state">No alerts found.</div>
                  </td>
                </tr>
              ) : (
                alerts.map((a) => (
                  <tr key={a.id}>
                    <td>{a.id}</td>
                    <td>{a.medicineName}</td>
                    <td>
                      {typeof a.daysRemaining === "number"
                        ? a.daysRemaining.toFixed(1)
                        : a.daysRemaining}
                    </td>
                    <td>
                      <span className={`badge badge-${a.resolved ? "confirmed" : "pending"}`}>
                        {a.resolved ? "Resolved" : "Unresolved"}
                      </span>
                    </td>
                    <td>{a.createdAt}</td>
                    <td>
                      {!a.resolved && (
                        <button
                          type="button"
                          className="btn-primary"
                          onClick={() => handleResolve(a.id)}
                        >
                          Resolve
                        </button>
                      )}
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