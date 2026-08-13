import React, { useEffect, useState } from 'react';
import { runbooksApi } from '../api/runbooksApi';
import { incidentsApi } from '../api/incidentsApi';
import type { IncidentSummary, RunbookExecution } from '../types';
import { useAuth } from '../context/AuthContext';
import { Badge } from '../components/common/Badge';
import { Card } from '../components/common/Card';
import { Button } from '../components/common/Button';
import { Modal } from '../components/common/Modal';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorBanner } from '../components/common/ErrorBanner';
import { Terminal, ShieldCheck, Eye } from 'lucide-react';

export const Runbooks: React.FC = () => {
  const [incidents, setIncidents] = useState<IncidentSummary[]>([]);
  const [selectedIncidentId, setSelectedIncidentId] = useState<string>('');
  const [executions, setExecutions] = useState<RunbookExecution[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Terminal Modal
  const [terminalModalOpen, setTerminalModalOpen] = useState(false);
  const [selectedExecution, setSelectedExecution] = useState<RunbookExecution | null>(null);

  // Action Loading
  const [actionLoading, setActionLoading] = useState(false);
  const [actionMessage, setActionMessage] = useState<string | null>(null);

  const { hasRole } = useAuth();

  const loadIncidents = async () => {
    try {
      const resp = await incidentsApi.listIncidents({ size: 50 });
      const list = resp.content || [];
      setIncidents(list);
      if (list.length > 0) {
        setSelectedIncidentId(list[0].id);
      }
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to load incidents list.');
    }
  };

  const loadExecutions = async (incId: string) => {
    if (!incId) return;
    setIsLoading(true);
    setError(null);
    try {
      const data = await runbooksApi.getExecutions(incId);
      setExecutions(data);
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to load runbook executions.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadIncidents();
  }, []);

  useEffect(() => {
    if (selectedIncidentId) {
      loadExecutions(selectedIncidentId);
    }
  }, [selectedIncidentId]);

  const handleApprove = async (stepId: string, executionId: string) => {
    if (!selectedIncidentId) return;
    setActionLoading(true);
    try {
      await runbooksApi.approveExecution(selectedIncidentId, stepId, executionId);
      setActionMessage('Runbook execution approved and dispatched!');
      await loadExecutions(selectedIncidentId);
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to approve runbook execution.');
    } finally {
      setActionLoading(false);
    }
  };

  return (
    <div>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px' }}>
        <div>
          <h1 className="page-title">
            <Terminal size={22} color="var(--accent-cyan)" />
            <span>Controlled Runbook Automation</span>
          </h1>
          <p className="page-subtitle">
            Secure, audited execution of diagnostic scripts, container restarts, and operational remediation commands
          </p>
        </div>
      </div>

      {/* Incident Selector */}
      <Card style={{ marginBottom: '20px', padding: '12px 16px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--text-secondary)' }}>
            Filter by Incident:
          </span>
          <select
            className="form-select"
            style={{ width: 'auto', minWidth: '320px' }}
            value={selectedIncidentId}
            onChange={(e) => setSelectedIncidentId(e.target.value)}
          >
            {incidents.map((inc) => (
              <option key={inc.id} value={inc.id}>
                [{inc.severity}] {inc.title} ({inc.service})
              </option>
            ))}
          </select>
        </div>
      </Card>

      {actionMessage && (
        <div style={{ padding: '10px 16px', backgroundColor: 'rgba(59, 130, 246, 0.1)', border: '1px solid var(--border-accent)', borderRadius: 'var(--radius-sm)', color: '#93c5fd', fontSize: '13px', marginBottom: '16px' }}>
          {actionMessage}
        </div>
      )}

      {error && <ErrorBanner message={error} onRetry={() => loadExecutions(selectedIncidentId)} />}

      <Card title="Runbook Execution History" subtitle="Audited command executions with container isolation">
        {isLoading ? (
          <LoadingSpinner message="Querying runbook execution audits..." />
        ) : executions.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '40px 20px', color: 'var(--text-muted)' }}>
            <Terminal size={32} color="var(--text-dim)" style={{ margin: '0 auto 12px auto' }} />
            <p>No runbooks executed for the selected incident.</p>
          </div>
        ) : (
          <div className="sre-table-container">
            <table className="sre-table">
              <thead>
                <tr>
                  <th>Command</th>
                  <th>Status</th>
                  <th>Requested By</th>
                  <th>Approved By</th>
                  <th>Started At</th>
                  <th>Duration</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {executions.map((rb) => (
                  <tr key={rb.id}>
                    <td>
                      <code style={{ fontSize: '12px', color: '#a5f3fc' }}>{rb.command}</code>
                    </td>
                    <td>
                      <Badge value={rb.executionStatus} />
                    </td>
                    <td>
                      <span style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>
                        {rb.requestedBy?.name || 'Automated'}
                      </span>
                    </td>
                    <td>
                      <span style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>
                        {rb.approvedBy?.name || 'Pending Approval'}
                      </span>
                    </td>
                    <td>
                      <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                        {rb.startedAt ? new Date(rb.startedAt).toLocaleTimeString() : 'Queued'}
                      </span>
                    </td>
                    <td>
                      <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                        {rb.completedAt && rb.startedAt
                          ? `${Math.round((new Date(rb.completedAt).getTime() - new Date(rb.startedAt).getTime()) / 1000)}s`
                          : '-'}
                      </span>
                    </td>
                    <td>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <Button
                          variant="secondary"
                          size="sm"
                          icon={<Eye size={12} />}
                          onClick={() => {
                            setSelectedExecution(rb);
                            setTerminalModalOpen(true);
                          }}
                        >
                          View Logs
                        </Button>
                        {rb.executionStatus === 'REQUESTED' && hasRole('ADMIN') && rb.investigationStepId && (
                          <Button
                            variant="primary"
                            size="sm"
                            icon={<ShieldCheck size={12} />}
                            loading={actionLoading}
                            onClick={() => handleApprove(rb.investigationStepId!, rb.id)}
                          >
                            Approve
                          </Button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {/* Terminal View Modal */}
      <Modal
        isOpen={terminalModalOpen}
        onClose={() => setTerminalModalOpen(false)}
        title={
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Terminal size={16} color="var(--accent-cyan)" />
            <span>Execution Terminal Console</span>
          </div>
        }
        maxWidth="750px"
      >
        {selectedExecution && (
          <div className="terminal-window">
            <div className="terminal-header">
              <span style={{ color: '#a5f3fc', fontSize: '12px' }}>$ {selectedExecution.command}</span>
              <Badge value={selectedExecution.executionStatus} />
            </div>
            <div className="terminal-body">
              {selectedExecution.output || selectedExecution.errorOutput || 'No stdout/stderr stream returned.'}
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
};
