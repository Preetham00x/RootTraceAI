import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { incidentsApi } from '../api/incidentsApi';
import type { CreateIncidentPayload } from '../api/incidentsApi';
import type { IncidentSeverity, IncidentStatus, IncidentSummary } from '../types';
import { useAuth } from '../context/AuthContext';
import { Badge } from '../components/common/Badge';
import { Card } from '../components/common/Card';
import { Button } from '../components/common/Button';
import { Modal } from '../components/common/Modal';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorBanner } from '../components/common/ErrorBanner';
import { AlertTriangle, Plus, Search, ArrowRight, ShieldAlert } from 'lucide-react';

export const Incidents: React.FC = () => {
  const [incidents, setIncidents] = useState<IncidentSummary[]>([]);
  const [page, setPage] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Filters
  const [search, setSearch] = useState('');
  const [severityFilter, setSeverityFilter] = useState<string>('ALL');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [serviceFilter, setServiceFilter] = useState<string>('');
  const [totalElements, setTotalElements] = useState(0);

  // Create Incident Modal State
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [createForm, setCreateForm] = useState<CreateIncidentPayload>({
    title: '',
    description: '',
    service: 'payment-service',
    severity: 'HIGH',
    environment: 'production',
  });
  const [createLoading, setCreateLoading] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  const { hasRole } = useAuth();
  const navigate = useNavigate();

  const loadIncidents = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const response = await incidentsApi.listIncidents({
        page,
        size: 20,
        severity: severityFilter !== 'ALL' ? (severityFilter as IncidentSeverity) : undefined,
        status: statusFilter !== 'ALL' ? (statusFilter as IncidentStatus) : undefined,
        service: serviceFilter || undefined,
        search: search || undefined,
      });
      setIncidents(response.content || []);
      setTotalElements(response.totalElements || 0);
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to retrieve incidents.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadIncidents();
  }, [page, severityFilter, statusFilter, serviceFilter]);

  const handleCreateSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setCreateLoading(true);
    setCreateError(null);
    try {
      const created = await incidentsApi.createIncident(createForm);
      setCreateModalOpen(false);
      navigate(`/incidents/${created.id}`);
    } catch (err: unknown) {
      setCreateError((err as Error).message || 'Failed to create incident.');
    } finally {
      setCreateLoading(false);
    }
  };

  return (
    <div>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px' }}>
        <div>
          <h1 className="page-title">
            <AlertTriangle size={22} color="var(--status-critical)" />
            <span>Incident Command & Triage</span>
          </h1>
          <p className="page-subtitle">
            Track active production outages, historical failure records, and automated AI diagnostic pipelines
          </p>
        </div>
        {hasRole(['ADMIN', 'ENGINEER']) && (
          <Button
            variant="primary"
            icon={<Plus size={16} />}
            onClick={() => setCreateModalOpen(true)}
          >
            Declare Incident
          </Button>
        )}
      </div>

      {/* Filter and Search Bar */}
      <Card style={{ marginBottom: '16px', padding: '12px 16px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px', flexWrap: 'wrap' }}>
          {/* Search */}
          <div style={{ flex: 1, minWidth: '240px', position: 'relative' }}>
            <Search size={16} style={{ position: 'absolute', left: '10px', top: '9px', color: 'var(--text-muted)' }} />
            <input
              type="text"
              className="form-input"
              style={{ paddingLeft: '32px' }}
              placeholder="Search incidents by title or description..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && loadIncidents()}
            />
          </div>

          {/* Service Filter */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>Service:</span>
            <input
              type="text"
              className="form-input"
              style={{ width: '150px' }}
              placeholder="e.g. payment"
              value={serviceFilter}
              onChange={(e) => {
                setServiceFilter(e.target.value);
                setPage(0);
              }}
              onKeyDown={(e) => e.key === 'Enter' && loadIncidents()}
            />
          </div>

          {/* Severity Filter */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>Severity:</span>
            <select
              className="form-select"
              style={{ width: 'auto' }}
              value={severityFilter}
              onChange={(e) => {
                setSeverityFilter(e.target.value);
                setPage(0);
              }}
            >
              <option value="ALL">All Severities</option>
              <option value="CRITICAL">Critical</option>
              <option value="HIGH">High</option>
              <option value="MEDIUM">Medium</option>
              <option value="LOW">Low</option>
            </select>
          </div>

          {/* Status Filter */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>Status:</span>
            <select
              className="form-select"
              style={{ width: 'auto' }}
              value={statusFilter}
              onChange={(e) => {
                setStatusFilter(e.target.value);
                setPage(0);
              }}
            >
              <option value="ALL">All Statuses</option>
              <option value="OPEN">Open</option>
              <option value="INVESTIGATING">Investigating</option>
              <option value="MITIGATED">Mitigated</option>
              <option value="RESOLVED">Resolved</option>
              <option value="CLOSED">Closed</option>
            </select>
          </div>

          <Button variant="secondary" size="sm" onClick={loadIncidents}>
            Filter
          </Button>
        </div>
      </Card>

      {error && <ErrorBanner message={error} onRetry={loadIncidents} />}

      {/* Incidents Table */}
      <Card>
        {isLoading ? (
          <LoadingSpinner message="Querying incidents telemetry..." />
        ) : (
          <div className="sre-table-container">
            <table className="sre-table">
              <thead>
                <tr>
                  <th>Severity</th>
                  <th>Title & Service</th>
                  <th>Status</th>
                  <th>Environment</th>
                  <th>Reported By</th>
                  <th>Created At</th>
                  <th>Resolved At</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {incidents.length === 0 ? (
                  <tr>
                    <td colSpan={8} style={{ textAlign: 'center', padding: '32px', color: 'var(--text-muted)' }}>
                      No incidents match the selected criteria.
                    </td>
                  </tr>
                ) : (
                  incidents.map((inc) => (
                    <tr
                      key={inc.id}
                      onClick={() => navigate(`/incidents/${inc.id}`)}
                      style={{ cursor: 'pointer' }}
                    >
                      <td>
                        <Badge value={inc.severity} />
                      </td>
                      <td>
                        <div style={{ fontWeight: 600, color: 'var(--text-main)' }}>{inc.title}</div>
                        <div style={{ fontSize: '11px', color: 'var(--accent-cyan)' }}>{inc.service}</div>
                      </td>
                      <td>
                        <Badge value={inc.status} />
                      </td>
                      <td>
                        <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{inc.environment}</span>
                      </td>
                      <td>
                        <span style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>
                          {typeof inc.createdBy === 'object' && inc.createdBy !== null
                            ? ((inc.createdBy as any).name || `${(inc.createdBy as any).firstName || ''} ${(inc.createdBy as any).lastName || ''}`.trim() || (inc.createdBy as any).email || 'System')
                            : String(inc.createdBy || 'System')}
                        </span>
                      </td>
                      <td>
                        <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                          {new Date(inc.createdAt).toLocaleString()}
                        </span>
                      </td>
                      <td>
                        <span style={{ fontSize: '11px', color: inc.resolvedAt ? 'var(--status-healthy)' : 'var(--text-dim)' }}>
                          {inc.resolvedAt ? new Date(inc.resolvedAt).toLocaleTimeString() : 'Unresolved'}
                        </span>
                      </td>
                      <td style={{ textAlign: 'right' }}>
                        <ArrowRight size={14} color="var(--text-muted)" />
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
            
            <div style={{ padding: '16px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderTop: '1px solid var(--border-color)' }}>
              <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                Showing {incidents.length} of {totalElements} incidents
              </span>
              <div style={{ display: 'flex', gap: '8px' }}>
                <Button variant="secondary" size="sm" onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0}>Previous</Button>
                <Button variant="secondary" size="sm" onClick={() => setPage(p => p + 1)} disabled={(page + 1) * 20 >= totalElements}>Next</Button>
              </div>
            </div>
          </div>
        )}
      </Card>

      {/* Declare Incident Modal */}
      <Modal
        isOpen={createModalOpen}
        onClose={() => setCreateModalOpen(false)}
        title={
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <ShieldAlert size={18} color="var(--status-critical)" />
            <span>Declare Production Incident</span>
          </div>
        }
      >
        <form onSubmit={handleCreateSubmit}>
          {createError && <ErrorBanner message={createError} />}

          <div className="form-group">
            <label className="form-label" htmlFor="inc-title">Incident Title *</label>
            <input
              id="inc-title"
              type="text"
              className="form-input"
              placeholder="e.g. Payment Gateway 504 Timeout Spike"
              value={createForm.title}
              onChange={(e) => setCreateForm({ ...createForm, title: e.target.value })}
              required
            />
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="inc-service">Target Monitored Service *</label>
            <input
              id="inc-service"
              type="text"
              className="form-input"
              placeholder="e.g. payment-service"
              value={createForm.service}
              onChange={(e) => setCreateForm({ ...createForm, service: e.target.value })}
              required
            />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
            <div className="form-group">
              <label className="form-label" htmlFor="inc-severity">Severity *</label>
              <select
                id="inc-severity"
                className="form-select"
                value={createForm.severity}
                onChange={(e) => setCreateForm({ ...createForm, severity: e.target.value as IncidentSeverity })}
              >
                <option value="CRITICAL">Critical (Sev 1 - Outage)</option>
                <option value="HIGH">High (Sev 2 - Degraded)</option>
                <option value="MEDIUM">Medium (Sev 3 - Impaired)</option>
                <option value="LOW">Low (Sev 4 - Minor)</option>
              </select>
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="inc-env">Environment *</label>
              <select
                id="inc-env"
                className="form-select"
                value={createForm.environment}
                onChange={(e) => setCreateForm({ ...createForm, environment: e.target.value })}
              >
                <option value="production">Production</option>
                <option value="staging">Staging</option>
                <option value="development">Development</option>
              </select>
            </div>
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="inc-desc">Initial Incident Description & Symptoms *</label>
            <textarea
              id="inc-desc"
              className="form-textarea"
              rows={4}
              placeholder="Provide symptoms, error codes, affected endpoints, or initial hypotheses..."
              value={createForm.description}
              onChange={(e) => setCreateForm({ ...createForm, description: e.target.value })}
              required
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '20px' }}>
            <Button type="button" variant="secondary" onClick={() => setCreateModalOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" variant="danger" loading={createLoading}>
              Declare & Open Command Center
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
