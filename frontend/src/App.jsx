import { Routes, Route, Navigate } from "react-router-dom";
import Navbar from "./components/Navbar";
import ProtectedRoute from "./components/ProtectedRoute";
import LoginPage from "./pages/LoginPage";
import RegisterPage from "./pages/RegisterPage";
import BranchManagementPage from "./pages/admin/BranchManagementPage";
import WarehouseCodeManagementPage from "./pages/admin/WarehouseCodeManagementPage";
import AvailabilitySearchPage from "./pages/branch-staff/AvailabilitySearchPage";
import RaiseRequestPage from "./pages/branch-staff/RaiseRequestPage";
import IncomingRequestsPage from "./pages/branch-staff/IncomingRequestsPage";
import SentRequestsPage from "./pages/branch-staff/SentRequestsPage";
import StockManagementPage from "./pages/inventory-manager/StockManagementPage";
import AlertsPage from "./pages/inventory-manager/AlertsPage";
import EscalatedQueuePage from "./pages/warehouse-admin/EscalatedQueuePage";
import { useAuth } from "./context/AuthContext";

function AppLayout({ children }) {
  const { token } = useAuth();
  return (
    <>
      {token && <Navbar />}
      <main className="app-main">{children}</main>
    </>
  );
}

function App() {
  return (
    <AppLayout>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />

        <Route
          path="/admin/branches"
          element={
            <ProtectedRoute allowedRoles={["ADMIN"]}>
              <BranchManagementPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/warehouse-codes"
          element={
            <ProtectedRoute allowedRoles={["ADMIN"]}>
              <WarehouseCodeManagementPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/availability"
          element={
            <ProtectedRoute allowedRoles={["BRANCH_STAFF"]}>
              <AvailabilitySearchPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/raise-request"
          element={
            <ProtectedRoute allowedRoles={["BRANCH_STAFF"]}>
              <RaiseRequestPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/incoming-requests"
          element={
            <ProtectedRoute allowedRoles={["BRANCH_STAFF"]}>
              <IncomingRequestsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/sent-requests"
          element={
            <ProtectedRoute allowedRoles={["BRANCH_STAFF"]}>
              <SentRequestsPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/stock"
          element={
            <ProtectedRoute allowedRoles={["INVENTORY_MANAGER"]}>
              <StockManagementPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/alerts"
          element={
            <ProtectedRoute allowedRoles={["INVENTORY_MANAGER"]}>
              <AlertsPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/escalated"
          element={
            <ProtectedRoute allowedRoles={["WAREHOUSE_ADMIN"]}>
              <EscalatedQueuePage />
            </ProtectedRoute>
          }
        />

        <Route path="/" element={<Navigate to="/login" replace />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    </AppLayout>
  );
}

export default App;