import { useEffect, useState } from "react";
import * as authService from "../../services/authService";
import { LoadingState, ButtonBusy } from "../../components/Spinner";

export default function WarehouseCodeManagementPage() {
  const [codes, setCodes] = useState([]);
  const [error, setError] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  // Separate from isLoading: generating must not blank out the table of existing codes, and a
  // double click here would mint a second single-use code nobody asked for.
  const [isGenerating, setIsGenerating] = useState(false);

  const loadCodes = async () => {
    setIsLoading(true);
    try {
      const data = await authService.getAllWarehouseCodes();
      setCodes(data);
    } catch (err) {
      setError(err.response?.data?.message || "Failed to load warehouse codes.");
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadCodes();
  }, []);

  const handleGenerate = async (e) => {
    e.preventDefault();
    setError("");
    setIsGenerating(true);
    try {
      await authService.generateWarehouseCode();
      await loadCodes();
    } catch (err) {
      setError(err.response?.data?.message || "Failed to generate warehouse code.");
    } finally {
      setIsGenerating(false);
    }
  };

  return (
    <div className="page-container">
      <div className="page-header">
        <h1 className="page-title">Warehouse Access Codes</h1>
      </div>

      <div className="card">
        <form onSubmit={handleGenerate} className="form-grid">
          <button type="submit" className="btn-primary" disabled={isGenerating}>
            {isGenerating ? <ButtonBusy label="Generating..." /> : "Generate New Code"}
          </button>
        </form>

        {error && <div className="alert-error">{error}</div>}
      </div>

      <div className="card">
        {isLoading ? (
          <LoadingState label="Loading codes..." />
        ) : (
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Code</th>
                <th>Status</th>
                <th>Assigned To</th>
                <th>Created At</th>
              </tr>
            </thead>
            <tbody>
              {codes.length === 0 ? (
                <tr>
                  <td colSpan="5">
                    <div className="empty-state">No warehouse codes found.</div>
                  </td>
                </tr>
              ) : (
                codes.map((c) => (
                  <tr key={c.id}>
                    <td>{c.id}</td>
                    <td>{c.code}</td>
                    <td>
                      <span className={`badge badge-${c.used ? "confirmed" : "routine"}`}>
                        {c.used ? "Used" : "Unused"}
                      </span>
                    </td>
                    <td>{c.assignedToUsername || "-"}</td>
                    <td>{c.createdAt}</td>
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