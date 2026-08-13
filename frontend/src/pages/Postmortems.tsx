import React, { useEffect, useState } from 'react';
import { postmortemsApi } from '../api/postmortemsApi';
import type { CreateActionItemPayload } from '../api/postmortemsApi';
import { incidentsApi } from '../api/incidentsApi';
import type { IncidentSummary, Postmortem, PostmortemStatus } from '../types';
import { useAuth } from '../context/AuthContext';
import { Badge } from '../components/common/Badge';
import { Card } from '../components/common/Card';
import { Button } from '../components/common/Button';
import { Modal } from '../components/common/Modal';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorBanner } from '../components/common/ErrorBanner';
import {
  FileText,
  Download,
  Plus,
  ExternalLink,
  CheckCircle,
  Send,
  FileCheck,
} from 'lucide-react';

export const Postmortems: React.FC = () => {
  const [incidents, setIncidents] = useState<IncidentSummary[]>([]);
  const [selectedIncidentId, setSelectedIncidentId] = useState<string>('');
  const [postmortem, setPostmortem] = useState<Postmortem | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Generate & Update Loading
  const [actionLoading, setActionLoading] = useState(false);
  const [actionMessage, setActionMessage] = useState<string | null>(null);

  // Action Item Modal
  const [actionItemModalOpen, setActionItemModalOpen] = useState(false);
  const [actionItemForm, setActionItemForm] = useState<CreateActionItemPayload>({
    title: '',
    description: '',
    category: 'PREVENTION',
    priority: 'HIGH',
    assignedTo: 'sre-team@roottrace.com',
  });

  const { hasRole } = useAuth();

  const loadResolvedIncidents = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const response = await incidentsApi.listIncidents({ size: 50 });
      const list = response.content || [];
      setIncidents(list);
      if (list.length > 0) {
        setSelectedIncidentId(list[0].id);
      }
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to load incidents list.');
    } finally {
      setIsLoading(false);
    }
  };

  const loadPostmortem = async (incId: string) => {
    if (!incId) return;
    setIsLoading(true);
    setError(null);
    try {
      const data = await postmortemsApi.getPostmortem(incId);
      setPostmortem(data);
    } catch {
      // Postmortem might not exist yet for this incident
      setPostmortem(null);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadResolvedIncidents();
  }, []);

  useEffect(() => {
    if (selectedIncidentId) {
      loadPostmortem(selectedIncidentId);
    }
  }, [selectedIncidentId]);

  const handleGeneratePostmortem = async () => {
    if (!selectedIncidentId) return;
    setActionLoading(true);
    setActionMessage('Synthesizing postmortem timeline & root cause analysis with Google Gemini...');
    try {
      const generated = await postmortemsApi.generatePostmortem(selectedIncidentId);
      setPostmortem(generated);
      setActionMessage('Postmortem report generated successfully!');
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to generate postmortem.');
    } finally {
      setActionLoading(false);
    }
  };

  const handleStatusUpdate = async (newStatus: PostmortemStatus) => {
    if (!selectedIncidentId) return;
    setActionLoading(true);
    try {
      const updated = await postmortemsApi.updatePostmortem(selectedIncidentId, { status: newStatus });
      setPostmortem(updated);
      setActionMessage(`Postmortem lifecycle status moved to ${newStatus}.`);
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to update postmortem status.');
    } finally {
      setActionLoading(false);
    }
  };

  const handleExportMarkdown = async () => {
    if (!selectedIncidentId) return;
    try {
      const md = await postmortemsApi.exportMarkdown(selectedIncidentId);
      const blob = new Blob([md], { type: 'text/markdown;charset=utf-8' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `postmortem-${selectedIncidentId}.md`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to export postmortem markdown.');
    }
  };

  const handleCreateActionItem = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedIncidentId) return;
    setActionLoading(true);
    try {
      await postmortemsApi.createActionItem(selectedIncidentId, actionItemForm);
      setActionItemModalOpen(false);
      await loadPostmortem(selectedIncidentId);
      setActionMessage('Action item added to postmortem.');
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to create action item.');
    } finally {
      setActionLoading(false);
    }
  };

  const handleCreateJira = async (actionItemId: string, title: string, desc: string) => {
    if (!selectedIncidentId) return;
    setActionLoading(true);
    try {
      const res = await postmortemsApi.createJiraTicket(selectedIncidentId, actionItemId, title, desc);
      setActionMessage(`Jira issue ${res.externalTicketId} created successfully!`);
      await loadPostmortem(selectedIncidentId);
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to create Jira ticket.');
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
            <FileText size={22} color="var(--accent-primary)" />
            <span>Automated SRE Postmortems</span>
          </h1>
          <p className="page-subtitle">
            Retrospective incident reports, root cause analyses, preventative action items, and Jira synchronization
          </p>
        </div>
      </div>

      {/* Incident Selector */}
      <Card style={{ marginBottom: '20px', padding: '12px 16px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px', flexWrap: 'wrap' }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--text-secondary)' }}>
            Select Incident:
          </span>
          <select
            className="form-select"
            style={{ width: 'auto', minWidth: '320px' }}
            value={selectedIncidentId}
            onChange={(e) => setSelectedIncidentId(e.target.value)}
          >
            {incidents.map((inc) => (
              <option key={inc.id} value={inc.id}>
                [{inc.severity}] {inc.title} ({inc.service}) — {inc.status}
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

      {error && <ErrorBanner message={error} onRetry={() => loadPostmortem(selectedIncidentId)} />}

      {isLoading ? (
        <LoadingSpinner message="Retrieving postmortem report..." />
      ) : !postmortem ? (
        <Card>
          <div style={{ textAlign: 'center', padding: '48px 20px', color: 'var(--text-muted)' }}>
            <FileText size={36} color="var(--text-dim)" style={{ margin: '0 auto 12px auto' }} />
            <h3 style={{ fontSize: '16px', fontWeight: 600, color: 'var(--text-secondary)' }}>
              No Postmortem Generated
            </h3>
            <p style={{ fontSize: '13px', color: 'var(--text-muted)', marginTop: '4px', maxWidth: '440px', margin: '4px auto 16px auto' }}>
              RootTraceAI can automatically synthesize timeline evidence, diagnostics, and remediation lessons into an executive SRE postmortem.
            </p>
            {hasRole(['ADMIN', 'ENGINEER']) && (
              <Button
                variant="ai"
                icon={<FileCheck size={16} />}
                loading={actionLoading}
                onClick={handleGeneratePostmortem}
              >
                Generate Postmortem with AI
              </Button>
            )}
          </div>
        </Card>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '20px', marginBottom: '24px' }}>
          {/* Left Column: Postmortem Document Content */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
            <Card
              title={
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                  <span>{postmortem.title}</span>
                  <Badge value={postmortem.status} />
                </div>
              }
              subtitle={`Downtime: ${postmortem.downtimeMinutes} min • Author: ${postmortem.createdBy?.name}`}
              action={
                <Button
                  variant="secondary"
                  size="sm"
                  icon={<Download size={14} />}
                  onClick={handleExportMarkdown}
                >
                  Export Markdown
                </Button>
              }
            >
              <div style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
                {/* Executive Summary */}
                <div>
                  <h3 style={{ fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: '6px' }}>
                    Executive Summary
                  </h3>
                  <p style={{ fontSize: '13px', color: 'var(--text-secondary)', lineHeight: 1.6 }}>
                    {postmortem.summary}
                  </p>
                </div>

                {/* Impact Summary */}
                <div>
                  <h3 style={{ fontSize: '13px', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: '6px' }}>
                    Customer & Operational Impact
                  </h3>
                  <p style={{ fontSize: '13px', color: 'var(--text-secondary)', lineHeight: 1.6 }}>
                    {postmortem.impactSummary}
                  </p>
                </div>

                {/* Root Cause Analysis */}
                <div>
                  <h3 style={{ fontSize: '13px', fontWeight: 600, color: '#fca5a5', textTransform: 'uppercase', marginBottom: '6px' }}>
                    Root Cause Analysis (5 Whys)
                  </h3>
                  <div style={{ padding: '12px 14px', backgroundColor: 'var(--bg-card-elevated)', borderLeft: '3px solid var(--status-critical)', borderRadius: '0 var(--radius-sm) var(--radius-sm) 0', fontSize: '13px', color: 'var(--text-main)', lineHeight: 1.6 }}>
                    {postmortem.rootCauseAnalysis}
                  </div>
                </div>

                {/* Resolution Summary */}
                <div>
                  <h3 style={{ fontSize: '13px', fontWeight: 600, color: 'var(--status-healthy)', textTransform: 'uppercase', marginBottom: '6px' }}>
                    Resolution & Remediation
                  </h3>
                  <p style={{ fontSize: '13px', color: 'var(--text-secondary)', lineHeight: 1.6 }}>
                    {postmortem.resolutionSummary}
                  </p>
                </div>

                {/* Lessons Learned */}
                <div>
                  <h3 style={{ fontSize: '13px', fontWeight: 600, color: '#93c5fd', textTransform: 'uppercase', marginBottom: '6px' }}>
                    Lessons Learned & Retrospective Observations
                  </h3>
                  <p style={{ fontSize: '13px', color: 'var(--text-secondary)', lineHeight: 1.6, whiteSpace: 'pre-line' }}>
                    {postmortem.lessonsLearned}
                  </p>
                </div>
              </div>
            </Card>
          </div>

          {/* Right Column: Lifecycle Controls & Action Items Tracker */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
            {/* Lifecycle Status Management */}
            {hasRole(['ADMIN', 'ENGINEER']) && (
              <Card title="Postmortem Lifecycle Controls">
                <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                  <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                    Current Status: <strong style={{ color: 'var(--text-main)' }}>{postmortem.status}</strong>
                  </div>
                  <div style={{ display: 'flex', gap: '8px' }}>
                    {postmortem.status === 'DRAFT' && (
                      <Button
                        variant="secondary"
                        size="sm"
                        icon={<Send size={14} />}
                        loading={actionLoading}
                        onClick={() => handleStatusUpdate('IN_REVIEW')}
                      >
                        Submit For Review
                      </Button>
                    )}
                    {postmortem.status === 'IN_REVIEW' && (
                      <>
                        <Button
                          variant="secondary"
                          size="sm"
                          onClick={() => handleStatusUpdate('DRAFT')}
                        >
                          Back to Draft
                        </Button>
                        <Button
                          variant="primary"
                          size="sm"
                          icon={<CheckCircle size={14} />}
                          loading={actionLoading}
                          onClick={() => handleStatusUpdate('PUBLISHED')}
                        >
                          Publish Postmortem
                        </Button>
                      </>
                    )}
                    {postmortem.status === 'PUBLISHED' && (
                      <div style={{ fontSize: '12px', color: 'var(--status-healthy)', display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <CheckCircle size={15} /> Published & Archived
                      </div>
                    )}
                  </div>
                </div>
              </Card>
            )}

            {/* Action Items List & Jira Sync */}
            <Card
              title="Preventative Action Items"
              subtitle={`${postmortem.actionItems?.length || 0} tracked items`}
              action={
                hasRole(['ADMIN', 'ENGINEER']) && (
                  <Button
                    variant="secondary"
                    size="sm"
                    icon={<Plus size={12} />}
                    onClick={() => setActionItemModalOpen(true)}
                  >
                    Add Item
                  </Button>
                )
              }
            >
              {postmortem.actionItems?.length === 0 ? (
                <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                  No action items created yet.
                </div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                  {postmortem.actionItems?.map((item) => (
                    <div
                      key={item.id}
                      style={{
                        padding: '10px 12px',
                        backgroundColor: 'var(--bg-card-elevated)',
                        border: '1px solid var(--border-subtle)',
                        borderRadius: 'var(--radius-sm)',
                        fontSize: '12px',
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '4px' }}>
                        <span style={{ fontWeight: 600, color: 'var(--text-main)' }}>{item.title}</span>
                        <Badge value={item.priority} />
                      </div>
                      <p style={{ fontSize: '11px', color: 'var(--text-secondary)', marginBottom: '6px' }}>
                        {item.description}
                      </p>
                      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderTop: '1px solid var(--border-subtle)', paddingTop: '6px' }}>
                        <span style={{ fontSize: '10px', color: 'var(--text-muted)' }}>
                          Assignee: {item.assignedTo || 'Unassigned'}
                        </span>
                        {hasRole(['ADMIN', 'ENGINEER']) && (
                          <button
                            onClick={() => handleCreateJira(item.id, item.title, item.description)}
                            style={{
                              background: 'none',
                              border: 'none',
                              color: 'var(--accent-primary)',
                              fontSize: '11px',
                              cursor: 'pointer',
                              display: 'flex',
                              alignItems: 'center',
                              gap: '4px',
                            }}
                          >
                            <span>Sync Jira</span>
                            <ExternalLink size={10} />
                          </button>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </Card>
          </div>
        </div>
      )}

      {/* Add Action Item Modal */}
      <Modal
        isOpen={actionItemModalOpen}
        onClose={() => setActionItemModalOpen(false)}
        title="Add Postmortem Action Item"
      >
        <form onSubmit={handleCreateActionItem}>
          <div className="form-group">
            <label className="form-label" htmlFor="ai-title">Action Item Title *</label>
            <input
              id="ai-title"
              type="text"
              className="form-input"
              placeholder="e.g. Increase connection pool timeout in payment-service"
              value={actionItemForm.title}
              onChange={(e) => setActionItemForm({ ...actionItemForm, title: e.target.value })}
              required
            />
          </div>
          <div className="form-group">
            <label className="form-label" htmlFor="ai-desc">Description & Verification Criterion *</label>
            <textarea
              id="ai-desc"
              className="form-textarea"
              rows={3}
              placeholder="Detailed description of the architectural or configuration fix..."
              value={actionItemForm.description}
              onChange={(e) => setActionItemForm({ ...actionItemForm, description: e.target.value })}
              required
            />
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
            <div className="form-group">
              <label className="form-label" htmlFor="ai-priority">Priority</label>
              <select
                id="ai-priority"
                className="form-select"
                value={actionItemForm.priority}
                onChange={(e) => setActionItemForm({ ...actionItemForm, priority: e.target.value as any })}
              >
                <option value="CRITICAL">Critical</option>
                <option value="HIGH">High</option>
                <option value="MEDIUM">Medium</option>
                <option value="LOW">Low</option>
              </select>
            </div>
            <div className="form-group">
              <label className="form-label" htmlFor="ai-category">Category</label>
              <select
                id="ai-category"
                className="form-select"
                value={actionItemForm.category}
                onChange={(e) => setActionItemForm({ ...actionItemForm, category: e.target.value })}
              >
                <option value="PREVENTION">Prevention</option>
                <option value="DETECTION">Detection</option>
                <option value="MITIGATION">Mitigation</option>
                <option value="PROCESS">Process</option>
              </select>
            </div>
          </div>
          <div className="form-group">
            <label className="form-label" htmlFor="ai-assignee">Assignee Email</label>
            <input
              id="ai-assignee"
              type="email"
              className="form-input"
              value={actionItemForm.assignedTo}
              onChange={(e) => setActionItemForm({ ...actionItemForm, assignedTo: e.target.value })}
            />
          </div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '20px' }}>
            <Button type="button" variant="secondary" onClick={() => setActionItemModalOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={actionLoading}>
              Save Action Item
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
