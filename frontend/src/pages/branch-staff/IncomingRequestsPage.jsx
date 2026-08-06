import { useEffect, useState } from "react";
import { useAuth } from "../../context/AuthContext";
import * as transferService from "../../services/transferService";
import { LoadingState, ButtonBusy } from "../../components/Spinner";

export default function IncomingRequestsPage() {
  const { branchId } = useAuth();
  const [requests, setRequests] = useState([]);
  const [error, setError] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  // Which row is mid-request, and which of its actions - approving and declining need different
  // labels, and both buttons in that row have to lock so a second click cannot fire the other one.
  const [busy, setBusy] = useState(null);

  const loadRequests = async () => {
    setIsLoading(true);
    setError("");
    try {
      // The backend decides what this branch may see and act on: pending requests from other
      // branches, minus any this branch already declined, each flagged with whether escalation
      // currently points here and whether this branch actually holds the stock.
      const data = await transferService.getOpenRequestsForBranch(branchId);
      setRequests(data);
    } catch (err) {
      setError(err.response?.data?.message || "Failed to load incoming requests.");
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadRequests();
  }, [branchId]);

  const act = async (fn, id, action, fallbackMessage) => {
    setError("");
    setBusy({ id, action });
    try {
      await fn(id);
    } catch (err) {
      setError(err.response?.data?.message || fallbackMessage);
    } finally {
      setBusy(null);
    }
    // Reload either way: on conflict the row has already changed and the list must catch up.
    loadRequests();
  };

  const handleApprove = (id) =>
    act(transferService.approveRequest, id, "approve", "Failed to approve request.");

  const handleReject = (id) =>
    act(transferService.rejectRequest, id, "reject", "Failed to decline request.");

  return (
    <div className="page-container">
      <div className="page-header">
        <h1 className="page-title">Incoming Requests</h1>
      </div>

      {error && <div className="alert-error">{error}</div>}

      <div className="card">
        {isLoading ? (
          <LoadingState />
        ) : (
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Medicine</th>
                <th>Quantity</th>
                <th>Requesting Branch</th>
                <th>Contact</th>
                <th>Criticality</th>
                <th>Priority</th>
                <th>Your Stock</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {requests.length === 0 ? (
                <tr>
                  <td colSpan="9">
                    <div className="empty-state">No open requests for your branch right now.</div>
                  </td>
                </tr>
              ) : (
                requests.map((r) => (
                  <tr key={r.id}>
                    <td>{r.id}</td>
                    <td>{r.medicineName}</td>
                    <td>{r.quantity}</td>
                    <td>{r.requestingBranchName}</td>
                    <td>{r.requestingBranchContact || "-"}</td>
                    <td>
                      <span className={`badge badge-${(r.criticality || "routine").toLowerCase()}`}>
                        {r.criticality || "-"}
                      </span>
                    </td>
                    <td>
                      {r.assignedToYou ? (
                        <span className="badge badge-pending">Assigned to you</span>
                      ) : (
                        <span className="badge badge-routine">
                          Open{r.currentTargetBranchName ? ` - with ${r.currentTargetBranchName}` : ""}
                        </span>
                      )}
                    </td>
                    <td>
                      {r.canFulfil ? (
                        <span className="badge badge-confirmed">Enough stock</span>
                      ) : (
                        <span className="badge badge-cancelled">Not enough</span>
                      )}
                    </td>
                    <td>
                      <div className="btn-row">
                        <button
                          type="button"
                          className="btn-primary"
                          disabled={!r.canFulfil || busy?.id === r.id}
                          title={
                            r.canFulfil
                              ? "Supply this request from your branch's stock"
                              : "Your branch does not have enough unexpired stock in a single batch"
                          }
                          onClick={() => handleApprove(r.id)}
                        >
                          {busy?.id === r.id && busy.action === "approve" ? (
                            <ButtonBusy label="Approving..." />
                          ) : (
                            "Approve"
                          )}
                        </button>
                        <button
                          type="button"
                          className="btn-danger"
                          disabled={busy?.id === r.id}
                          title="Decline - your branch will not be asked for this request again"
                          onClick={() => handleReject(r.id)}
                        >
                          {busy?.id === r.id && busy.action === "reject" ? (
                            <ButtonBusy label="Declining..." tone="inherit" />
                          ) : (
                            "Decline"
                          )}
                        </button>
                      </div>
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
