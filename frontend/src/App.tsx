import { Navigate, Route, Routes } from 'react-router-dom';

import { ProtectedRoute } from '@/auth/ProtectedRoute';
import { useAuth } from '@/auth/useAuth';
import { AppLayout } from '@/components/AppLayout';
import { PageSpinner } from '@/components/PageSpinner';
import { AuditClaimHistoryPage } from '@/pages/AuditClaimHistoryPage';
import { AuditJournalPage } from '@/pages/AuditJournalPage';
import { ClaimDetailPage } from '@/pages/ClaimDetailPage';
import { ClaimListPage } from '@/pages/ClaimListPage';
import { ClaimSubmitPage } from '@/pages/ClaimSubmitPage';
import { LoginPage } from '@/pages/LoginPage';
import { ReviewDetailPage } from '@/pages/ReviewDetailPage';
import { ReviewQueuePage } from '@/pages/ReviewQueuePage';
import { ForbiddenPage, NotFoundPage } from '@/pages/StatusPages';

/** Sends the signed-in user to the first landing screen their roles can actually reach. */
function RoleLanding() {
  const { user, initializing } = useAuth();

  if (initializing) {
    return <PageSpinner label="Restoring your session" />;
  }
  if (user === null) {
    return <Navigate to="/login" replace />;
  }
  if (user.roles.includes('CLAIMS_PROCESSOR')) {
    return <Navigate to="/claims" replace />;
  }
  if (user.roles.includes('MEDICAL_REVIEWER')) {
    return <Navigate to="/review" replace />;
  }
  if (user.roles.includes('AUDITOR')) {
    return <Navigate to="/audit/journals" replace />;
  }
  return <Navigate to="/403" replace />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/403" element={<ForbiddenPage />} />

      <Route
        element={
          <ProtectedRoute>
            <AppLayout />
          </ProtectedRoute>
        }
      >
        <Route path="/" element={<RoleLanding />} />

        <Route
          path="/claims"
          element={
            <ProtectedRoute requiredRole="CLAIMS_PROCESSOR">
              <ClaimListPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/claims/new"
          element={
            <ProtectedRoute requiredRole="CLAIMS_PROCESSOR">
              <ClaimSubmitPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/claims/:claimUuid"
          element={
            <ProtectedRoute requiredRole="CLAIMS_PROCESSOR">
              <ClaimDetailPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/review"
          element={
            <ProtectedRoute requiredRole="MEDICAL_REVIEWER">
              <ReviewQueuePage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/review/:claimUuid"
          element={
            <ProtectedRoute requiredRole="MEDICAL_REVIEWER">
              <ReviewDetailPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/audit/journals"
          element={
            <ProtectedRoute requiredRole="AUDITOR">
              <AuditJournalPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/audit/claims/:claimUuid"
          element={
            <ProtectedRoute requiredRole="AUDITOR">
              <AuditClaimHistoryPage />
            </ProtectedRoute>
          }
        />
      </Route>

      <Route path="/404" element={<NotFoundPage />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
