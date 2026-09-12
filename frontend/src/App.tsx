import { Navigate, Route, Routes } from "react-router";
import { AdminLayout } from "./layouts/AdminLayout";
import { ProtectedRoute } from "./lib/auth";
import { DispatchPage } from "./pages/dispatch/DispatchPage";
import { LoginPage } from "./pages/LoginPage";
import { MapVersionDetailPage } from "./pages/maps/MapVersionDetailPage";
import { MapVersionsPage } from "./pages/maps/MapVersionsPage";
import { MapsPage } from "./pages/maps/MapsPage";
import { RobotDetailPage } from "./pages/robots/RobotDetailPage";
import { RobotsPage } from "./pages/robots/RobotsPage";
import { SiteDetailPage } from "./pages/sites/SiteDetailPage";
import { SitesPage } from "./pages/sites/SitesPage";
import { TaskDetailPage } from "./pages/tasks/TaskDetailPage";
import { TasksPage } from "./pages/tasks/TasksPage";
import { UserDetailPage } from "./pages/users/UserDetailPage";
import { UsersPage } from "./pages/users/UsersPage";

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/"
        element={
          <ProtectedRoute>
            <AdminLayout />
          </ProtectedRoute>
        }
      >
        <Route index element={<Navigate to="/sites" replace />} />
        <Route path="sites" element={<SitesPage />} />
        <Route path="sites/:siteId" element={<SiteDetailPage />} />
        <Route path="robots" element={<RobotsPage />} />
        <Route path="robots/:robotId" element={<RobotDetailPage />} />
        <Route path="maps" element={<MapsPage />} />
        <Route path="maps/:mapId" element={<MapVersionsPage />} />
        <Route path="maps/:mapId/versions/:versionId" element={<MapVersionDetailPage />} />
        <Route path="tasks" element={<TasksPage />} />
        <Route path="tasks/:taskId" element={<TaskDetailPage />} />
        <Route path="dispatch" element={<DispatchPage />} />
        <Route path="users" element={<UsersPage />} />
        <Route path="users/:userId" element={<UserDetailPage />} />
      </Route>
    </Routes>
  );
}

export default App;
