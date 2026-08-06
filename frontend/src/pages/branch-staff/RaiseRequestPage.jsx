import { useEffect, useState } from "react";
import { useLocation } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import * as transferService from "../../services/transferService";
import * as inventoryService from "../../services/inventoryService";
import * as branchService from "../../services/branchService";
import { LoadingState, ButtonBusy } from "../../components/Spinner";

const CRITICALITY_OPTIONS = ["CRITICAL", "URGENT", "ROUTINE"];

export default function RaiseRequestPage() {
  const { branchId } = useAuth();
  const location = useLocation();
  const prefill = location.state || {};
  const [requestingBranchName, setRequestingBranchName] = useState("");
  const [medicineName, setMedicineName] = useState(prefill.medicineName || "");
  const [quantity, setQuantity] = useState(prefill.quantity || "");
  const [criticality, setCriticality] = useState(CRITICALITY_OPTIONS[2]);
  const [remarks, setRemarks] = useState("");
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [availableBranches, setAvailableBranches] = useState([]);
  const [targetBranchId, setTargetBranchId] = useState(prefill.targetBranchId || "");
  const [availabilityChecked, setAvailabilityChecked] = useState(false);
  const [isCheckingAvailability, setIsCheckingAvailability] = useState(false);

  useEffect(() => {
    if (!branchId) return;
    branchService.getBranch(branchId).then((b) => setRequestingBranchName(b.branchName));
  }, [branchId]);

  const resetAvailability = () => {
    setAvailabilityChecked(false);
    setAvailableBranches([]);
    setTargetBranchId("");
  };

  const handleCheckAvailability = async () => {
    setError("");
    setIsCheckingAvailability(true);
    try {
      const data = await inventoryService.checkAvailability(medicineName, quantity);
      const others = data.filter((b) => String(b.branchId) !== String(branchId));
      setAvailableBranches(others);
      setAvailabilityChecked(true);
    } catch (err) {
      setError(err.response?.data?.message || "Failed to check availability.");
    } finally {
      setIsCheckingAvailability(false);
    }
  };

  useEffect(() => {
    if (prefill.medicineName && prefill.quantity) {
      handleCheckAvailability();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");
    setSuccess("");
    setIsLoading(true);
    try {
      const request = await transferService.createRequest({
        medicineName,
        quantity: Number(quantity),
        requestingBranchId: Number(branchId),
        requestingBranchName,
        criticality,
        remarks,
        targetBranchId: targetBranchId ? Number(targetBranchId) : null,
      });
      setSuccess(`Request #${request.id} created with status ${request.status}.`);
      setMedicineName("");
      setQuantity("");
      setRemarks("");
      resetAvailability();
    } catch (err) {
      setError(err.response?.data?.message || "Failed to create transfer request.");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="page-container">
      <div className="page-header">
        <h1 className="page-title">Raise Transfer Request</h1>
      </div>

      <div className="card">
        <form onSubmit={handleSubmit} className="form-grid">
          <div className="form-group">
            <label>Medicine Name</label>
            <input
              value={medicineName}
              onChange={(e) => {
                setMedicineName(e.target.value);
                resetAvailability();
              }}
              required
            />
          </div>
          <div className="form-group">
            <label>Quantity</label>
            <input
              type="number"
              min="1"
              value={quantity}
              onChange={(e) => {
                setQuantity(e.target.value);
                resetAvailability();
              }}
              required
            />
          </div>

          <button
            type="button"
            className="btn-secondary"
            onClick={handleCheckAvailability}
            disabled={!medicineName || !quantity || isCheckingAvailability}
          >
            {isCheckingAvailability ? (
              <ButtonBusy label="Checking..." tone="inherit" />
            ) : (
              "Check Availability"
            )}
          </button>

          {isCheckingAvailability && <LoadingState label="Checking branch stock..." />}

          {availabilityChecked && (
            <div className="form-group">
              <label>Source Branch</label>
              {availableBranches.length === 0 ? (
                <div className="empty-state">
                  No other branch has stock - this request will escalate to the central
                  warehouse.
                </div>
              ) : (
                <select value={targetBranchId} onChange={(e) => setTargetBranchId(e.target.value)}>
                  <option value="">Auto-select</option>
                  {availableBranches.map((b) => (
                    <option key={b.branchId} value={b.branchId}>
                      {b.branchName} - {b.quantity} available
                    </option>
                  ))}
                </select>
              )}
            </div>
          )}

          <div className="form-group">
            <label>Criticality</label>
            <select value={criticality} onChange={(e) => setCriticality(e.target.value)}>
              {CRITICALITY_OPTIONS.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
          </div>
          <div className="form-group">
            <label>Remarks</label>
            <input value={remarks} onChange={(e) => setRemarks(e.target.value)} />
          </div>

          {error && <div className="alert-error">{error}</div>}
          {success && <div className="alert-success">{success}</div>}
          <button
            type="submit"
            className="btn-primary"
            disabled={isLoading || isCheckingAvailability}
          >
            {isLoading ? <ButtonBusy label="Submitting..." /> : "Submit Request"}
          </button>
        </form>
      </div>
    </div>
  );
}