import React, { useEffect, useState } from 'react';
import { slosApi } from '../api/slosApi';
import type { CreateSloPayload } from '../api/slosApi';
import { commandCenterApi } from '../api/commandCenterApi';
import type { ServiceHealthSummary, SloEvaluation } from '../types';
import { useAuth } from '../context/AuthContext';
import { Badge } from '../components/common/Badge';
import { Card } from '../components/common/Card';
import { Button } from '../components/common/Button';
import { Modal } from '../components/common/Modal';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorBanner } from '../components/common/ErrorBanner';
import { Target, Plus, Activity } from 'lucide-react';

export const Slos: React.FC = () => {
  const [services, setServices] = useState<ServiceHealthSummary[]>([]);
  const [selectedService, setSelectedService] = useState<string>('payment-service');
  const [evaluations, setEvaluations] = useState<SloEvaluation[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Create SLO modal
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [createForm, setCreateForm] = useState<CreateSloPayload>({
    name: '',
    description: '',
    targetPercentage: 99.9,
    sliType: 'LATENCY',
    windowDays: 30,
    warningThresholdPercentage: 99.0,
    metricQuery: 'http_request_duration_seconds{status="200"}',
  });
  const [createLoading, setCreateLoading] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  // Record Measurement modal
  const [measureModalOpen, setMeasureModalOpen] = useState(false);
  const [selectedSloId, setSelectedSloId] = useState<string>('');
  const [goodEvents, setGoodEvents] = useState<number>(9990);
  const [totalEvents, setTotalEvents] = useState<number>(10000);
  const [measureLoading, setMeasureLoading] = useState(false);

  const { hasRole } = useAuth();

  const loadServices = async () => {
    try {
      const data = await commandCenterApi.getServiceSummaries(30, 50, 'name');
      setServices(data);
      if (data.length > 0 && !data.some((s) => s.serviceName === selectedService)) {
        setSelectedService(data[0].serviceName);
      }
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to load services list.');
    }
  };

  const loadSloEvaluations = async (svc: string) => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await slosApi.getSloEvaluations(svc);
      setEvaluations(data);
    } catch (err: unknown) {
      setError((err as Error).message || `Failed to load SLO evaluations for ${svc}.`);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadServices();
  }, []);

  useEffect(() => {
    if (selectedService) {
      loadSloEvaluations(selectedService);
    }
  }, [selectedService]);

  const handleCreateSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setCreateLoading(true);
    setCreateError(null);
    try {
      await slosApi.createSlo(selectedService, createForm);
      setCreateModalOpen(false);
      await loadSloEvaluations(selectedService);
    } catch (err: unknown) {
      setCreateError((err as Error).message || 'Failed to create SLO.');
    } finally {
      setCreateLoading(false);
    }
  };

  const handleMeasureSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setMeasureLoading(true);
    try {
      await slosApi.recordSliMeasurement(selectedService, selectedSloId, goodEvents, totalEvents);
      setMeasureModalOpen(false);
      await loadSloEvaluations(selectedService);
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to record SLI measurement.');
    } finally {
      setMeasureLoading(false);
    }
  };

  return (
    <div>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px' }}>
        <div>
          <h1 className="page-title">
            <Target size={22} color="var(--accent-primary)" />
            <span>Service Level Objectives & Error Budgets</span>
          </h1>
          <p className="page-subtitle">
            Reliability governance, multi-window burn rate detection, and error budget exhaustion tracking
          </p>
        </div>
        {hasRole(['ADMIN', 'ENGINEER']) && (
          <Button
            variant="primary"
            icon={<Plus size={16} />}
            onClick={() => setCreateModalOpen(true)}
          >
            Create New SLO
          </Button>
        )}
      </div>

      {/* Service Selector Bar */}
      <Card style={{ marginBottom: '20px', padding: '12px 16px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--text-secondary)' }}>
            Select Service:
          </span>
          <select
            className="form-select"
            style={{ width: 'auto', minWidth: '220px' }}
            value={selectedService}
            onChange={(e) => setSelectedService(e.target.value)}
          >
            {services.map((s) => (
              <option key={s.serviceName} value={s.serviceName}>
                {s.serviceName} ({s.environment})
              </option>
            ))}
          </select>
        </div>
      </Card>

      {error && <ErrorBanner message={error} onRetry={() => loadSloEvaluations(selectedService)} />}

      {isLoading ? (
        <LoadingSpinner message={`Evaluating multi-window SLO compliance for ${selectedService}...`} />
      ) : evaluations.length === 0 ? (
        <Card>
          <div style={{ textAlign: 'center', padding: '48px 20px', color: 'var(--text-muted)' }}>
            <Target size={32} color="var(--text-dim)" style={{ margin: '0 auto 12px auto' }} />
            <h3 style={{ fontSize: '16px', fontWeight: 600, color: 'var(--text-secondary)' }}>
              No SLOs configured for {selectedService}
            </h3>
            <p style={{ fontSize: '13px', color: 'var(--text-muted)', marginTop: '4px' }}>
              Define target SLIs and error budgets to monitor reliability compliance.
            </p>
            {hasRole(['ADMIN', 'ENGINEER']) && (
              <Button
                variant="primary"
                size="sm"
                icon={<Plus size={14} />}
                style={{ marginTop: '16px' }}
                onClick={() => setCreateModalOpen(true)}
              >
                Configure First SLO
              </Button>
            )}
          </div>
        </Card>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))', gap: '20px' }}>
          {evaluations.map((slo) => (
            <Card
              key={slo.sloId}
              title={
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <Target size={16} color="var(--accent-cyan)" />
                  <span>{slo.sloName}</span>
                </div>
              }
              action={<Badge value={slo.status} />}
            >
              <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                {/* Target vs Actual */}
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                  <div style={{ padding: '10px 12px', backgroundColor: 'var(--bg-card-elevated)', borderRadius: 'var(--radius-sm)' }}>
                    <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Target SLO</div>
                    <div style={{ fontSize: '18px', fontWeight: 700, color: 'var(--text-main)', marginTop: '2px' }}>
                      {slo.targetPercentage.toFixed(3)}%
                    </div>
                  </div>
                  <div style={{ padding: '10px 12px', backgroundColor: 'var(--bg-card-elevated)', borderRadius: 'var(--radius-sm)' }}>
                    <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Current Compliance</div>
                    <div style={{ fontSize: '18px', fontWeight: 700, color: slo.compliancePercentage < slo.targetPercentage ? '#fca5a5' : 'var(--status-healthy)', marginTop: '2px' }}>
                      {slo.compliancePercentage.toFixed(3)}%
                    </div>
                  </div>
                </div>

                {/* Error Budget Remaining Bar */}
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', fontSize: '12px', marginBottom: '6px' }}>
                    <span style={{ color: 'var(--text-secondary)' }}>Error Budget Consumed:</span>
                    <strong style={{ color: slo.budgetConsumedPercentage > 75 ? '#fca5a5' : 'var(--text-main)' }}>
                      {slo.budgetConsumedPercentage.toFixed(1)}% ({slo.errorBudgetRemainingPercentage.toFixed(1)}% remaining)
                    </strong>
                  </div>
                  <div className="progress-bar-container" style={{ height: '8px' }}>
                    <div
                      className="progress-bar-fill"
                      style={{
                        width: `${Math.min(100, slo.budgetConsumedPercentage)}%`,
                        backgroundColor:
                          slo.budgetConsumedPercentage > 75
                            ? 'var(--status-critical)'
                            : slo.budgetConsumedPercentage > 50
                            ? 'var(--status-warning)'
                            : 'var(--status-healthy)',
                      }}
                    />
                  </div>
                </div>

                {/* Multi-Window Burn Rates */}
                <div>
                  <div style={{ fontSize: '11px', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: '6px' }}>
                    Multi-Window Burn Rates
                  </div>
                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '8px', textAlign: 'center' }}>
                    <div style={{ padding: '6px', backgroundColor: 'var(--bg-card-elevated)', borderRadius: 'var(--radius-sm)' }}>
                      <div style={{ fontSize: '10px', color: 'var(--text-muted)' }}>1h Window</div>
                      <div style={{ fontSize: '13px', fontWeight: 700, color: slo.burnRate1h > 2 ? '#fca5a5' : 'var(--text-main)' }}>
                        {slo.burnRate1h.toFixed(1)}x
                      </div>
                    </div>
                    <div style={{ padding: '6px', backgroundColor: 'var(--bg-card-elevated)', borderRadius: 'var(--radius-sm)' }}>
                      <div style={{ fontSize: '10px', color: 'var(--text-muted)' }}>6h Window</div>
                      <div style={{ fontSize: '13px', fontWeight: 700, color: slo.burnRate6h > 2 ? '#fca5a5' : 'var(--text-main)' }}>
                        {slo.burnRate6h.toFixed(1)}x
                      </div>
                    </div>
                    <div style={{ padding: '6px', backgroundColor: 'var(--bg-card-elevated)', borderRadius: 'var(--radius-sm)' }}>
                      <div style={{ fontSize: '10px', color: 'var(--text-muted)' }}>24h Window</div>
                      <div style={{ fontSize: '13px', fontWeight: 700, color: slo.burnRate24h > 2 ? '#fca5a5' : 'var(--text-main)' }}>
                        {slo.burnRate24h.toFixed(1)}x
                      </div>
                    </div>
                    <div style={{ padding: '6px', backgroundColor: 'var(--bg-card-elevated)', borderRadius: 'var(--radius-sm)' }}>
                      <div style={{ fontSize: '10px', color: 'var(--text-muted)' }}>3d Window</div>
                      <div style={{ fontSize: '13px', fontWeight: 700, color: slo.burnRate3d > 2 ? '#fca5a5' : 'var(--text-main)' }}>
                        {slo.burnRate3d.toFixed(1)}x
                      </div>
                    </div>
                  </div>
                </div>

                {/* Record measurement action */}
                {hasRole(['ADMIN', 'ENGINEER']) && (
                  <div style={{ paddingTop: '8px', borderTop: '1px solid var(--border-subtle)', display: 'flex', justifyContent: 'flex-end' }}>
                    <Button
                      variant="secondary"
                      size="sm"
                      icon={<Activity size={12} />}
                      onClick={() => {
                        setSelectedSloId(slo.sloId);
                        setMeasureModalOpen(true);
                      }}
                    >
                      Record SLI Measurement
                    </Button>
                  </div>
                )}
              </div>
            </Card>
          ))}
        </div>
      )}

      {/* Create SLO Modal */}
      <Modal
        isOpen={createModalOpen}
        onClose={() => setCreateModalOpen(false)}
        title={`Create New SLO for ${selectedService}`}
      >
        <form onSubmit={handleCreateSubmit}>
          {createError && <ErrorBanner message={createError} />}

          <div className="form-group">
            <label className="form-label" htmlFor="slo-name">SLO Name *</label>
            <input
              id="slo-name"
              type="text"
              className="form-input"
              placeholder="e.g. Payment Gateway Success Rate"
              value={createForm.name}
              onChange={(e) => setCreateForm({ ...createForm, name: e.target.value })}
              required
            />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
            <div className="form-group">
              <label className="form-label" htmlFor="target-pct">Target % (e.g. 99.9) *</label>
              <input
                id="target-pct"
                type="number"
                step="0.001"
                className="form-input"
                value={createForm.targetPercentage}
                onChange={(e) => setCreateForm({ ...createForm, targetPercentage: parseFloat(e.target.value) })}
                required
              />
            </div>
            <div className="form-group">
              <label className="form-label" htmlFor="warning-pct">Warning Threshold %</label>
              <input
                id="warning-pct"
                type="number"
                step="0.001"
                className="form-input"
                value={createForm.warningThresholdPercentage}
                onChange={(e) => setCreateForm({ ...createForm, warningThresholdPercentage: parseFloat(e.target.value) })}
              />
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
            <div className="form-group">
              <label className="form-label" htmlFor="sli-type">SLI Type</label>
              <select
                id="sli-type"
                className="form-select"
                value={createForm.sliType}
                onChange={(e) => setCreateForm({ ...createForm, sliType: e.target.value })}
              >
                <option value="LATENCY">Latency (p99)</option>
                <option value="AVAILABILITY">Availability (2xx/total)</option>
                <option value="ERROR_RATE">Error Rate (5xx)</option>
                <option value="THROUGHPUT">Throughput</option>
              </select>
            </div>
            <div className="form-group">
              <label className="form-label" htmlFor="window-days">Window (Days)</label>
              <input
                id="window-days"
                type="number"
                className="form-input"
                value={createForm.windowDays}
                onChange={(e) => setCreateForm({ ...createForm, windowDays: parseInt(e.target.value, 10) })}
              />
            </div>
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="metric-query">Metric Query / Expression *</label>
            <input
              id="metric-query"
              type="text"
              className="form-input"
              placeholder="e.g. rate(http_requests_total{status='200'}[5m])"
              value={createForm.metricQuery}
              onChange={(e) => setCreateForm({ ...createForm, metricQuery: e.target.value })}
              required
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '20px' }}>
            <Button type="button" variant="secondary" onClick={() => setCreateModalOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={createLoading}>
              Save SLO
            </Button>
          </div>
        </form>
      </Modal>

      {/* Record Measurement Modal */}
      <Modal
        isOpen={measureModalOpen}
        onClose={() => setMeasureModalOpen(false)}
        title="Record SLI Measurement Event"
      >
        <form onSubmit={handleMeasureSubmit}>
          <div className="form-group">
            <label className="form-label" htmlFor="good-events">Good / Successful Events *</label>
            <input
              id="good-events"
              type="number"
              className="form-input"
              value={goodEvents}
              onChange={(e) => setGoodEvents(parseInt(e.target.value, 10))}
              required
            />
          </div>
          <div className="form-group">
            <label className="form-label" htmlFor="total-events">Total Evaluated Events *</label>
            <input
              id="total-events"
              type="number"
              className="form-input"
              value={totalEvents}
              onChange={(e) => setTotalEvents(parseInt(e.target.value, 10))}
              required
            />
          </div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '20px' }}>
            <Button type="button" variant="secondary" onClick={() => setMeasureModalOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={measureLoading}>
              Record & Re-evaluate
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
