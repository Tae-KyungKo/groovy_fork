import { createBrowserRouter, Navigate } from "react-router-dom";
import App from "./App";
import { ProtectedRoute } from "./components/ProtectedRoute";
import { CalendarPage } from "./pages/CalendarPage";
import { LoginPage } from "./pages/LoginPage";
import { MyPage } from "./pages/MyPage";
import { NotFoundPage } from "./pages/NotFoundPage";
import { SignupPage } from "./pages/SignupPage";
import { StudyApplicationsPage } from "./pages/StudyApplicationsPage";
import { StudyDetailPage } from "./pages/StudyDetailPage";
import { StudyFormPage } from "./pages/StudyFormPage";
import { StudyListPage } from "./pages/StudyListPage";

export const router = createBrowserRouter([
  {
    path: "/",
    element: <App />,
    children: [
      { index: true, element: <Navigate to="/studies" replace /> },
      { path: "login", element: <LoginPage /> },
      { path: "signup", element: <SignupPage /> },
      { path: "studies", element: <StudyListPage /> },
      { path: "studies/:studyId", element: <StudyDetailPage /> },
      {
        path: "studies/new",
        element: (
          <ProtectedRoute>
            <StudyFormPage />
          </ProtectedRoute>
        ),
      },
      {
        path: "studies/:studyId/edit",
        element: (
          <ProtectedRoute>
            <StudyFormPage />
          </ProtectedRoute>
        ),
      },
      {
        path: "studies/:studyId/applications",
        element: (
          <ProtectedRoute>
            <StudyApplicationsPage />
          </ProtectedRoute>
        ),
      },
      {
        path: "calendar",
        element: (
          <ProtectedRoute>
            <CalendarPage />
          </ProtectedRoute>
        ),
      },
      {
        path: "me",
        element: (
          <ProtectedRoute>
            <MyPage />
          </ProtectedRoute>
        ),
      },
      { path: "*", element: <NotFoundPage /> },
    ],
  },
]);
