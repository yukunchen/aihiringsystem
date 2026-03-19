import { Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import ProtectedRoute from './components/ProtectedRoute';
import AppLayout from './components/AppLayout';
import LoginPage from './pages/login/LoginPage';
import ResumeListPage from './pages/resumes/ResumeListPage';
import ResumeUploadPage from './pages/resumes/ResumeUploadPage';
import JobListPage from './pages/jobs/JobListPage';
import JobCreatePage from './pages/jobs/JobCreatePage';
import JobDetailPage from './pages/jobs/JobDetailPage';

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<ProtectedRoute />}>
          <Route element={<AppLayout />}>
            <Route path="/resumes" element={<ResumeListPage />} />
            <Route path="/resumes/upload" element={<ResumeUploadPage />} />
            <Route path="/jobs" element={<JobListPage />} />
            <Route path="/jobs/new" element={<JobCreatePage />} />
            <Route path="/jobs/:id" element={<JobDetailPage />} />
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/jobs" replace />} />
      </Routes>
    </AuthProvider>
  );
}
