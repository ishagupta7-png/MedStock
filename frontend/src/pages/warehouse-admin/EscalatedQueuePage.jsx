import { useEffect, useState } from "react";
import * as transferService from "../../services/transferService";
import { LoadingState } from "../../components/Spinner";

export default function EscalatedQueuePage() {
  const [requests, setRequests] = useState([]);
  const [error, setError] = useState("");
  const [isLoading, setIsLoading] = useState(false);

  const loadRequests = async () => {
    setIsLoading(true);
    try {
      const data = await transferService.getRequestsByStatus("ESCALATED_TO_WAREHOUSE");
      setRequests(data);
    } catch (err) {
      setError(err.response?.data?.message || "Failed to load escalated requests.");
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadRequests();
  }, []);

  return (
    <div className="page-container">
      <div className="page-header">
        <h1 className="page-title">Escalated to Central Warehouse</h1>
      </div>

      {error && <div className="alert-error">{error}</div>}

      <div className="card">
        {isLoading ? (
          <LoadingState label="Loading escalated requests..." />
        ) : (
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Medicine</th>
                <th>Quantity</th>
                <th>Requesting Branch</th>
                <th>Criticality</th>
                <th>Requested At</th>
              </tr>
            </thead>
            <tbody>
              {requests.length === 0 ? (
                <tr>
                  <td colSpan="6">
                    <div className="empty-state">No escalated requests found.</div>
                  </td>
                </tr>
              ) : (
                requests.map((r) => (
                  <tr key={r.id}>
                    <td>{r.id}</td>
                    <td>{r.medicineName}</td>
                    <td>{r.quantity}</td>
                    <td>{r.requestingBranchName}</td>
                    <td>
                      <span className={`badge badge-${r.criticality.toLowerCase()}`}>
                        {r.criticality}
                      </span>
                    </td>
                    <td>{r.requestedAt}</td>
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