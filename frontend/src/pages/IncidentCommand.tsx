import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { commandCenterApi } from '../api/commandCenterApi';
import { incidentsApi } from '../api/incidentsApi';
import { runbooksApi } from '../api/runbooksApi';
import { postmortemsApi } from '../api/postmortemsApi';
import type { IncidentCommand } from '../types';
import { useAuth } from '../context/AuthContext';
import { Badge } from '../components/common/Badge';
import { Card } from '../components/common/Card';
import { Button } from '../components/common/Button';
import { Modal } from '../components/common/Modal';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorBanner } from '../components/common/ErrorBanner';
import {
  ArrowLeft,
  Sparkles,
  CheckCircle,
  FileText,
  ExternalLink,
  Layers,
  ThumbsUp,
  ThumbsDown,
  Play,
  Check,
} from 'lucide-react';

export const IncidentCommandView: React.FC = () => {
  const { incidentId } = useParams<{ incidentId: string }>();
  const [commandData, setCommandData] = useState<IncidentCommand | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Action states
  const [actionLoading, setActionLoading] = useState(false);
  const [actionMessage, setActionMessage] = useState<string | null>(null);

  // Resolve Modal State
  const [resolveModalOpen, setResolveModalOpen] = useState(false);
  const [resolutionText, setResolutionText] = useState('');

  // Runbook Output Modal
  const [runbookOutputModalOpen, setRunbookOutputModalOpen] = useState(false);
  const [selectedRunbookOutput, setSelectedRunbookOutput] = useState<{ command: string; output: string | null; error: string | null } | null>(null);

  const { hasRole } = useAuth();
  const navigate = useNavigate();

  const loadIncidentCommand = async () => {
    if (!incidentId) return;
    setIsLoading(true);
    setError(null);
    try {
      const data = await commandCenterApi.getIncidentCommand(incidentId);
      setCommandData(data);
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to load incident command view.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadIncidentCommand();
  }, [incidentId]);

  const handleRunDiagnosis = async () => {
    if (!incidentId) return;
    setActionLoading(true);
    setActionMessage('Executing AI diagnosis pipeline with hybrid RAG retrieval...');
    try {
      await incidentsApi.diagnoseIncident(incidentId);
      await loadIncidentCommand();
      setActionMessage('AI Diagnosis generated successfully!');
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to generate AI diagnosis.');
    } finally {
      setActionLoading(false);
    }
  };

  const handleGeneratePlan = async () => {
    if (!incidentId) return;
    setActionLoading(true);
    setActionMessage('Generating structured investigation plan with SRE steps...');
    try {
      await incidentsApi.generateInvestigationPlan(incidentId);
      await loadIncidentCommand();
      setActionMessage('Investigation plan created!');
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to generate investigation plan.');
    } finally {
      setActionLoading(false);
    }
  };

  const handleGeneratePostmortem = async () => {
    if (!incidentId) return;
    setActionLoading(true);
    setActionMessage('Generating SRE retrospective postmortem...');
    try {
      await postmortemsApi.generatePostmortem(incidentId);
      await loadIncidentCommand();
      setActionMessage('Postmortem generated!');
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to generate postmortem.');
    } finally {
      setActionLoading(false);
    }
  };

  const handleResolveSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!incidentId) return;
    setActionLoading(true);
    try {
      await incidentsApi.resolveIncident(incidentId, resolutionText);
      setResolveModalOpen(false);
      await loadIncidentCommand();
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to resolve incident.');
    } finally {
      setActionLoading(false);
    }
  };

  const handleStepStatusChange = async (planId: string, stepId: string, newStatus: string) => {
    if (!incidentId) return;
    try {
      await incidentsApi.updateInvestigationStep(incidentId, planId, stepId, { status: newStatus });
      await loadIncidentCommand();
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to update step status.');
    }
  };

  const handleExecuteRunbook = async (stepId: string) => {
    if (!incidentId) return;
    setActionLoading(true);
    setActionMessage('Requesting runbook execution...');
    try {
      await runbooksApi.requestExecution(incidentId, stepId);
      await loadIncidentCommand();
      setActionMessage('Runbook execution requested.');
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to execute runbook.');
    } finally {
      setActionLoading(false);
    }
  };

  const handleDiagnosisFeedback = async (diagnosisId: string, rating: number) => {
    if (!incidentId) return;
    try {
      await incidentsApi.submitDiagnosisFeedback(incidentId, diagnosisId, rating);
      await loadIncidentCommand();
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to submit feedback.');
    }
  };

  if (isLoading) {
    return <LoadingSpinner message="Loading 360° incident command pane..." />;
  }

  if (error || !commandData) {
    return <ErrorBanner message={error || 'Incident command data unavailable.'} onRetry={loadIncidentCommand} />;
  }

  const { incident, latestDiagnosis, investigationPlan, relatedIncidents, postmortem, sloImpact, runbookExecutions, jiraTickets, timeline, recommendations } = commandData;

  return (
    <div>
      {/* Back Button */}
      <div style={{ marginBottom: '16px' }}>
        <button
          onClick={() => navigate('/incidents')}
          style={{
            background: 'none',
            border: 'none',
            color: 'var(--text-muted)',
            display: 'flex',
            alignItems: 'center',
            gap: '6px',
            cursor: 'pointer',
            fontSize: '13px',
          }}
        >
          <ArrowLeft size={16} />
          <span>Back to Incidents</span>
        </button>
      </div>

      {actionMessage && (
        <div style={{ padding: '10px 16px', backgroundColor: 'rgba(59, 130, 246, 0.1)', border: '1px solid var(--border-accent)', borderRadius: 'var(--radius-sm)', color: '#93c5fd', fontSize: '13px', marginBottom: '16px' }}>
          {actionMessage}
        </div>
      )}

      {/* Incident Command Header */}
      <div
        className="sre-card"
        style={{
          padding: '24px',
          marginBottom: '24px',
          background: 'linear-gradient(180deg, var(--bg-card) 0%, rgba(15, 23, 42, 0.85) 100%)',
          border: '1px solid var(--border-default)',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', flexWrap: 'wrap', gap: '16px' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '8px' }}>
              <Badge value={incident.severity} />
              <Badge value={incident.status} />
              <span style={{ fontSize: '12px', fontWeight: 600, color: 'var(--accent-cyan)' }}>
                {incident.service}
              </span>
              <span style={{ fontSize: '11px', color: 'var(--text-dim)' }}>
                • {incident.environment.toUpperCase()}
              </span>
            </div>
            <h1 style={{ fontSize: '22px', fontWeight: 700, color: 'var(--text-main)', marginBottom: '8px' }}>
              {incident.title}
            </h1>
            <p style={{ fontSize: '13px', color: 'var(--text-secondary)', maxWidth: '800px', lineHeight: 1.5 }}>
              {incident.description}
            </p>
            <div style={{ display: 'flex', gap: '16px', marginTop: '12px', fontSize: '12px', color: 'var(--text-muted)' }}>
              <span>Created by: <strong style={{ color: 'var(--text-secondary)' }}>{incident.createdBy?.name}</strong></span>
              <span>Opened: {new Date(incident.createdAt).toLocaleString()}</span>
              {incident.resolvedAt && (
                <span style={{ color: 'var(--status-healthy)' }}>
                  Resolved: {new Date(incident.resolvedAt).toLocaleString()}
                </span>
              )}
            </div>
          </div>

          {/* Action Button Bar */}
          {hasRole(['ADMIN', 'ENGINEER']) && (
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
              <Button
                variant="ai"
                size="sm"
                icon={<Sparkles size={14} />}
                loading={actionLoading}
                onClick={handleRunDiagnosis}
              >
                {latestDiagnosis ? 'Re-Diagnose AI' : 'Run AI Diagnosis'}
              </Button>

              <Button
                variant="secondary"
                size="sm"
                icon={<Layers size={14} />}
                loading={actionLoading}
                onClick={handleGeneratePlan}
              >
                {investigationPlan ? 'Regenerate Plan' : 'Generate Plan'}
              </Button>

              {incident.status === 'RESOLVED' && !postmortem && (
                <Button
                  variant="secondary"
                  size="sm"
                  icon={<FileText size={14} />}
                  loading={actionLoading}
                  onClick={handleGeneratePostmortem}
                >
                  Generate Postmortem
                </Button>
              )}

              {incident.status !== 'RESOLVED' && incident.status !== 'CLOSED' && (
                <Button
                  variant="primary"
                  size="sm"
                  icon={<CheckCircle size={14} />}
                  onClick={() => setResolveModalOpen(true)}
                >
                  Resolve Incident
                </Button>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Main 2-Column Command Content */}
      <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '20px', marginBottom: '24px' }}>
        {/* Left Column: AI Diagnosis, Investigation Steps, Recommendations */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          {/* AI Diagnosis Card */}
          <Card
            title={
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Sparkles size={18} color="#c084fc" />
                <span>AI Automated Incident Diagnosis</span>
                <span className="badge badge-ai">RAG Powered</span>
              </div>
            }
            subtitle={latestDiagnosis ? `Evaluated on ${new Date(latestDiagnosis.createdAt).toLocaleTimeString()}` : 'No AI diagnosis generated yet'}
          >
            {!latestDiagnosis ? (
              <div style={{ textAlign: 'center', padding: '24px', color: 'var(--text-muted)' }}>
                Click "Run AI Diagnosis" to analyze error logs, historical incidents, and documentation with Google Gemini.
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                    Confidence Score: <strong style={{ color: 'var(--accent-cyan)' }}>{(latestDiagnosis.confidenceScore * 100).toFixed(0)}%</strong>
                  </div>
                  {/* Feedback rating */}
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Helpful?</span>
                    <button
                      onClick={() => handleDiagnosisFeedback(latestDiagnosis.id, 5)}
                      style={{ background: 'none', border: 'none', color: latestDiagnosis.feedbackRating === 5 ? 'var(--status-healthy)' : 'var(--text-muted)', cursor: 'pointer' }}
                    >
                      <ThumbsUp size={14} />
                    </button>
                    <button
                      onClick={() => handleDiagnosisFeedback(latestDiagnosis.id, 1)}
                      style={{ background: 'none', border: 'none', color: latestDiagnosis.feedbackRating === 1 ? 'var(--status-critical)' : 'var(--text-muted)', cursor: 'pointer' }}
                    >
                      <ThumbsDown size={14} />
                    </button>
                  </div>
                </div>

                <div>
                  <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: '4px' }}>
                    Probable Root Cause
                  </div>
                  <div style={{ fontSize: '14px', fontWeight: 600, color: '#fca5a5', lineHeight: 1.5 }}>
                    {latestDiagnosis.probableRootCause}
                  </div>
                </div>

                <div>
                  <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: '4px' }}>
                    Reasoning
                  </div>
                  <p style={{ fontSize: '13px', color: 'var(--text-secondary)', lineHeight: 1.6 }}>
                    {latestDiagnosis.reasoning}
                  </p>
                </div>

                <div>
                  <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: '4px' }}>
                    Recommended Mitigation
                  </div>
                  <div style={{ padding: '10px 14px', backgroundColor: 'var(--bg-card-elevated)', borderLeft: '3px solid var(--accent-primary)', borderRadius: '0 var(--radius-sm) var(--radius-sm) 0', fontSize: '13px', color: '#93c5fd' }}>
                    {latestDiagnosis.recommendedMitigation}
                  </div>
                </div>

                {latestDiagnosis.contributingFactors?.length > 0 && (
                  <div>
                    <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: '4px' }}>
                      Contributing Factors
                    </div>
                    <ul style={{ paddingLeft: '18px', fontSize: '13px', color: 'var(--text-secondary)' }}>
                      {latestDiagnosis.contributingFactors.map((cf, i) => (
                        <li key={i}>{cf}</li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            )}
          </Card>

          {/* Investigation Plan & Steps */}
          <Card
            title={
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Layers size={18} color="var(--accent-primary)" />
                <span>Structured Investigation Plan</span>
              </div>
            }
            subtitle={investigationPlan ? `${investigationPlan.steps.length} sequential diagnostic & remediation steps` : 'No plan active'}
          >
            {!investigationPlan ? (
              <div style={{ textAlign: 'center', padding: '24px', color: 'var(--text-muted)' }}>
                No investigation plan generated for this incident.
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                {investigationPlan.steps.map((step) => (
                  <div
                    key={step.id}
                    style={{
                      padding: '14px 16px',
                      backgroundColor: 'var(--bg-card-elevated)',
                      border: '1px solid var(--border-subtle)',
                      borderRadius: 'var(--radius-sm)',
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: '12px' }}>
                      <div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
                          <span style={{ fontSize: '11px', fontWeight: 700, padding: '2px 6px', backgroundColor: 'var(--bg-input)', borderRadius: '2px', color: 'var(--accent-cyan)' }}>
                            STEP {step.stepOrder}
                          </span>
                          <span style={{ fontSize: '14px', fontWeight: 600, color: 'var(--text-main)' }}>
                            {step.title}
                          </span>
                          <Badge value={step.status} />
                        </div>
                        <p style={{ fontSize: '13px', color: 'var(--text-secondary)', marginBottom: '8px' }}>
                          {step.description}
                        </p>
                      </div>

                      {/* Step controls */}
                      {hasRole(['ADMIN', 'ENGINEER']) && (
                        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                          {step.status !== 'COMPLETED' && (
                            <button
                              className="btn btn-secondary btn-sm"
                              title="Mark Completed"
                              onClick={() => handleStepStatusChange(investigationPlan.id, step.id, 'COMPLETED')}
                            >
                              <Check size={12} />
                            </button>
                          )}
                          <Button
                            variant="secondary"
                            size="sm"
                            icon={<Play size={12} />}
                            onClick={() => handleExecuteRunbook(step.id)}
                          >
                            Runbook
                          </Button>
                        </div>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </Card>

          {/* SRE Triage Recommendations */}
          {recommendations?.length > 0 && (
            <Card title="SRE Action Recommendations">
              <ul style={{ paddingLeft: '18px', fontSize: '13px', color: 'var(--text-secondary)', display: 'flex', flexDirection: 'column', gap: '6px' }}>
                {recommendations.map((r, i) => (
                  <li key={i}>{r}</li>
                ))}
              </ul>
            </Card>
          )}
        </div>

        {/* Right Column: SLO Impact, Related Incidents, Postmortem, Runbooks, Jira */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          {/* SLO & Error Budget Impact */}
          <Card title="SLO & Error Budget Impact" subtitle="Target degradation during incident window">
            {sloImpact.length === 0 ? (
              <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                No direct SLO breach recorded for this service.
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                {sloImpact.map((s) => (
                  <div
                    key={s.sloId}
                    style={{
                      padding: '10px 12px',
                      backgroundColor: 'var(--bg-card-elevated)',
                      borderRadius: 'var(--radius-sm)',
                      borderLeft: `3px solid ${s.status === 'BREACHED' ? 'var(--status-critical)' : 'var(--status-warning)'}`,
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                      <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--text-main)' }}>{s.sloName}</span>
                      <Badge value={s.status} />
                    </div>
                    <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '4px' }}>
                      Compliance: <strong>{s.currentCompliance.toFixed(2)}%</strong> (Target: {s.targetPercentage}%)
                    </div>
                    <div style={{ fontSize: '11px', color: '#fca5a5', marginTop: '2px' }}>
                      Error budget consumed: {s.budgetConsumedPercentage.toFixed(1)}% • Burn rate: {s.burnRateMultiplier.toFixed(1)}x
                    </div>
                  </div>
                ))}
              </div>
            )}
          </Card>

          {/* Related Historical Incidents */}
          <Card title="Correlated Incidents" subtitle="Cosine similarity on vectors & shared root cause">
            {relatedIncidents.length === 0 ? (
              <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                No correlated historical incidents found.
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                {relatedIncidents.map((rel) => (
                  <div
                    key={rel.incidentId}
                    onClick={() => navigate(`/incidents/${rel.incidentId}`)}
                    style={{
                      padding: '10px 12px',
                      backgroundColor: 'var(--bg-card-elevated)',
                      borderRadius: 'var(--radius-sm)',
                      cursor: 'pointer',
                      border: '1px solid var(--border-subtle)',
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                      <span style={{ fontSize: '12px', fontWeight: 600, color: 'var(--accent-cyan)' }}>
                        {rel.title}
                      </span>
                      <span style={{ fontSize: '11px', fontWeight: 700, color: 'var(--accent-primary)' }}>
                        {(rel.similarityScore * 100).toFixed(0)}% Match
                      </span>
                    </div>
                    <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '2px' }}>
                      Shared Cause: {rel.sharedRootCause}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </Card>

          {/* Postmortem Banner */}
          {postmortem && (
            <Card title="SRE Postmortem" subtitle={`Status: ${postmortem.status}`}>
              <div style={{ fontSize: '13px', color: 'var(--text-secondary)', marginBottom: '12px' }}>
                {postmortem.summary}
              </div>
              <Button
                variant="secondary"
                size="sm"
                icon={<FileText size={14} />}
                onClick={() => navigate(`/postmortems`)}
              >
                Open Full Postmortem
              </Button>
            </Card>
          )}

          {/* Runbook Executions */}
          <Card title="Runbook Executions" subtitle={`${runbookExecutions.length} commands triggered`}>
            {runbookExecutions.length === 0 ? (
              <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                No runbooks executed for this incident.
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                {runbookExecutions.map((rb) => (
                  <div
                    key={rb.id}
                    onClick={() => {
                      setSelectedRunbookOutput({ command: rb.command, output: rb.output, error: rb.errorOutput });
                      setRunbookOutputModalOpen(true);
                    }}
                    style={{
                      padding: '8px 10px',
                      backgroundColor: 'var(--bg-card-elevated)',
                      borderRadius: 'var(--radius-sm)',
                      cursor: 'pointer',
                      border: '1px solid var(--border-subtle)',
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                      <span style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: '11px', color: '#a5f3fc' }}>
                        {rb.command}
                      </span>
                      <Badge value={rb.executionStatus} />
                    </div>
                  </div>
                ))}
              </div>
            )}
          </Card>

          {/* External Jira Tickets */}
          {jiraTickets?.length > 0 && (
            <Card title="External Tickets">
              {jiraTickets.map((t) => (
                <div key={t.id} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '6px 0' }}>
                  <a href={t.externalUrl} target="_blank" rel="noreferrer" style={{ color: 'var(--accent-primary)', fontSize: '12px', display: 'flex', alignItems: 'center', gap: '4px' }}>
                    <span>{t.externalTicketId}</span>
                    <ExternalLink size={12} />
                  </a>
                  <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{t.status}</span>
                </div>
              ))}
            </Card>
          )}

          {/* Timeline */}
          <Card title="Chronological Incident Timeline">
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', maxHeight: '200px', overflowY: 'auto' }}>
              {timeline?.map((ev, i) => (
                <div key={i} style={{ fontSize: '11px', color: 'var(--text-secondary)', borderLeft: '2px solid var(--border-default)', paddingLeft: '8px' }}>
                  <div style={{ color: 'var(--text-muted)' }}>{new Date(ev.occurredAt).toLocaleTimeString()}</div>
                  <div>{ev.description}</div>
                </div>
              ))}
            </div>
          </Card>
        </div>
      </div>

      {/* Resolve Incident Modal */}
      <Modal
        isOpen={resolveModalOpen}
        onClose={() => setResolveModalOpen(false)}
        title="Resolve Production Incident"
      >
        <form onSubmit={handleResolveSubmit}>
          <div className="form-group">
            <label className="form-label" htmlFor="resolution-text">Resolution Summary & Fix *</label>
            <textarea
              id="resolution-text"
              className="form-textarea"
              rows={4}
              placeholder="Describe the remediation steps taken, config rolled back, or hotfix applied..."
              value={resolutionText}
              onChange={(e) => setResolutionText(e.target.value)}
              required
            />
          </div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
            <Button type="button" variant="secondary" onClick={() => setResolveModalOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={actionLoading}>
              Confirm Resolution
            </Button>
          </div>
        </form>
      </Modal>

      {/* Runbook Output Terminal Modal */}
      <Modal
        isOpen={runbookOutputModalOpen}
        onClose={() => setRunbookOutputModalOpen(false)}
        title="Runbook Execution Output"
        maxWidth="700px"
      >
        {selectedRunbookOutput && (
          <div className="terminal-window">
            <div className="terminal-header">
              <span style={{ fontSize: '12px', color: '#a5f3fc' }}>$ {selectedRunbookOutput.command}</span>
            </div>
            <div className="terminal-body">
              {selectedRunbookOutput.output || selectedRunbookOutput.error || 'No stdout/stderr returned.'}
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
};
