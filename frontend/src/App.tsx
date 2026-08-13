import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { ProtectedRoute } from './components/layout/ProtectedRoute';
import { AppLayout } from './components/layout/AppLayout';

import { Login } from './pages/Login';
import { CommandCenter } from './pages/CommandCenter';
import { Services } from './pages/Services';
import { ServiceDetail } from './pages/ServiceDetail';
import { Incidents } from './pages/Incidents';
import { IncidentCommandView } from './pages/IncidentCommand';
import { Slos } from './pages/Slos';
import { Intelligence } from './pages/Intelligence';
import { Postmortems } from './pages/Postmortems';
import { Runbooks } from './pages/Runbooks';
import { Integrations } from './pages/Integrations';
import { Settings } from './pages/Settings';

export const App: React.FC = () => {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          {/* Public Route */}
          <Route path="/login" element={<Login />} />

          {/* Protected Application Routes */}
          <Route element={<ProtectedRoute />}>
            <Route element={<AppLayout />}>
              <Route path="/" element={<CommandCenter />} />
              <Route path="/overview" element={<Navigate to="/" replace />} />
              <Route path="/services" element={<Services />} />
              <Route path="/services/:serviceName" element={<ServiceDetail />} />
              <Route path="/incidents" element={<Incidents />} />
              <Route path="/incidents/:incidentId" element={<IncidentCommandView />} />
              <Route path="/slos" element={<Slos />} />
              <Route path="/intelligence" element={<Intelligence />} />
              <Route path="/postmortems" element={<Postmortems />} />
              <Route path="/runbooks" element={<Runbooks />} />
              <Route path="/integrations" element={<Integrations />} />
              <Route path="/settings" element={<Settings />} />
            </Route>
          </Route>

          {/* Catch-all */}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
};

export default App;
