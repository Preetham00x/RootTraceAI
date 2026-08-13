import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { commandCenterApi } from '../api/commandCenterApi';
import type { ServiceHealthDetail } from '../types';
import { Badge } from '../components/common/Badge';
import { Card } from '../components/common/Card';
import { StatCard } from '../components/common/StatCard';
import { CircularScore } from '../components/common/CircularScore';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorBanner } from '../components/common/ErrorBanner';
import {
  Server,
  ArrowLeft,
  AlertTriangle,
  Clock,
  Target,
  Flame,
  Lightbulb,
  CheckCircle,
  AlertCircle,
} from 'lucide-react';

export const ServiceDetail: React.FC = () => {
  const { serviceName } = useParams<{ serviceName: string }>();
  const [detail, setDetail] = useState<ServiceHealthDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const navigate = useNavigate();

  const loadServiceDetail = async () => {
    if (!serviceName) return;
    setIsLoading(true);
    setError(null);
    try {
      const data = await commandCenterApi.getServiceDetail(serviceName, 30);
      setDetail(data);
    } catch (err: unknown) {
      setError((err as Error).message || `Failed to load details for ${serviceName}.`);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadServiceDetail();
  }, [serviceName]);

  if (isLoading) {
    return <LoadingSpinner message={`Compiling deep reliability diagnostics for ${serviceName}...`} />;
  }

  if (error || !detail) {
    return <ErrorBanner message={error || 'Service detail could not be retrieved.'} onRetry={loadServiceDetail} />;
  }

  return (
    <div>
      {/* Navigation Breadcrumb & Back */}
      <div style={{ marginBottom: '16px' }}>
        <button
          onClick={() => navigate('/services')}
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
          <span>Back to Services</span>
        </button>
      </div>

      {/* Hero Service Header */}
      <div
        className="sre-card"
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginBottom: '24px',
          padding: '24px',
          background: 'linear-gradient(180deg, var(--bg-card) 0%, rgba(15, 23, 42, 0.8) 100%)',
          border: '1px solid var(--border-default)',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
          <div
            style={{
              width: '56px',
              height: '56px',
              borderRadius: 'var(--radius-md)',
              backgroundColor: 'rgba(6, 182, 212, 0.15)',
              border: '1px solid rgba(6, 182, 212, 0.3)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: 'var(--accent-cyan)',
            }}
          >
            <Server size={28} />
          </div>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <h1 style={{ fontSize: '22px', fontWeight: 700, color: 'var(--text-main)' }}>
                {detail.serviceName}
              </h1>
              <Badge value={detail.riskTier} />
              <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                {detail.environment.toUpperCase()}
              </span>
            </div>
            <p style={{ fontSize: '13px', color: 'var(--text-secondary)', marginTop: '4px' }}>
              Proactive Reliability Score: <strong>{detail.healthScore.toFixed(1)} / 100</strong> • Recurrence Rate: {detail.recurrenceRatePercentage.toFixed(1)}%
            </p>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '24px' }}>
          <CircularScore score={detail.healthScore} riskTier={detail.riskTier} size={90} label="Health" />
        </div>
      </div>

      {/* KPI Stats Grid */}
      <div className="kpi-grid">
        <StatCard
          label="Incidents (30d)"
          value={detail.totalIncidents30d}
          variant={detail.activeIncidents > 0 ? 'critical' : 'default'}
          icon={<AlertTriangle size={18} />}
          subtext={
            <span>
              <strong style={{ color: detail.activeIncidents > 0 ? '#fca5a5' : 'inherit' }}>
                {detail.activeIncidents} Active
              </strong>{' '}
              ({detail.criticalIncidents30d} Critical)
            </span>
          }
        />
        <StatCard
          label="MTTR / MTTD"
          value={`${detail.meanTimeToResolveMinutes.toFixed(0)}m / ${detail.meanTimeToDetectMinutes.toFixed(0)}m`}
          variant="accent"
          icon={<Clock size={18} />}
          subtext="Mean resolution & detection"
        />
        <StatCard
          label="Avg Error Budget"
          value={`${detail.averageErrorBudgetConsumedPercentage.toFixed(1)}%`}
          variant={detail.averageErrorBudgetConsumedPercentage > 75 ? 'critical' : detail.averageErrorBudgetConsumedPercentage > 50 ? 'warning' : 'healthy'}
          icon={<Flame size={18} />}
          subtext={`Highest burn rate: ${detail.highestBurnRate.toFixed(1)}x`}
        />
        <StatCard
          label="SLO Health"
          value={`${detail.slos.filter((s) => s.status === 'HEALTHY').length} / ${detail.slos.length}`}
          variant={detail.slos.some((s) => s.status === 'BREACHED') ? 'critical' : 'healthy'}
          icon={<Target size={18} />}
          subtext={
            detail.slos.some((s) => s.status === 'BREACHED')
              ? `${detail.slos.filter((s) => s.status === 'BREACHED').length} SLO(s) breached`
              : 'All targets satisfied'
          }
        />
      </div>

      {/* 2-Column Detail Grid */}
      <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '20px', marginBottom: '24px' }}>
        {/* Left Column: SLOs, Recommendations & Root Causes */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          {/* Active Service Level Objectives */}
          <Card title="Active Service Level Objectives (SLOs)" subtitle="Target compliance and error budget consumption">
            {detail.slos.length === 0 ? (
              <div style={{ padding: '20px', textAlign: 'center', color: 'var(--text-muted)' }}>
                No SLOs configured for {detail.serviceName}.
              </div>
            ) : (
              <div className="sre-table-container">
                <table className="sre-table">
                  <thead>
                    <tr>
                      <th>SLO Name</th>
                      <th>Target</th>
                      <th>Compliance</th>
                      <th>Status</th>
                      <th>Budget Consumed</th>
                      <th>1h Burn Rate</th>
                    </tr>
                  </thead>
                  <tbody>
                    {detail.slos.map((slo) => (
                      <tr key={slo.sloId}>
                        <td style={{ fontWeight: 600 }}>{slo.name}</td>
                        <td>{slo.targetPercentage.toFixed(3)}%</td>
                        <td style={{ fontWeight: 600, color: slo.currentCompliancePercentage < slo.targetPercentage ? '#fca5a5' : 'var(--text-main)' }}>
                          {slo.currentCompliancePercentage.toFixed(3)}%
                        </td>
                        <td>
                          <Badge value={slo.status} />
                        </td>
                        <td>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                            <span style={{ fontSize: '11px', minWidth: '35px' }}>
                              {slo.budgetConsumedPercentage.toFixed(0)}%
                            </span>
                            <div className="progress-bar-container" style={{ width: '50px' }}>
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
                        </td>
                        <td>
                          <span style={{ color: slo.burnRate1h > 2.0 ? '#fca5a5' : 'var(--text-secondary)' }}>
                            {slo.burnRate1h.toFixed(1)}x
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Card>

          {/* Automated Reliability Recommendations */}
          <Card
            title={
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Lightbulb size={18} color="var(--accent-purple)" />
                <span>Reliability Engineering Recommendations</span>
              </div>
            }
            subtitle="Rule-based actionable steps to improve service health"
          >
            {detail.recommendations.length === 0 ? (
              <div style={{ fontSize: '13px', color: 'var(--status-healthy)', display: 'flex', alignItems: 'center', gap: '6px' }}>
                <CheckCircle size={16} /> No urgent recommendations. Service is operating within safe parameters.
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                {detail.recommendations.map((rec, i) => (
                  <div
                    key={i}
                    style={{
                      padding: '14px 16px',
                      backgroundColor: 'var(--bg-card-elevated)',
                      borderLeft: `4px solid ${
                        rec.priority === 'CRITICAL'
                          ? 'var(--status-critical)'
                          : rec.priority === 'HIGH'
                          ? 'var(--status-high)'
                          : 'var(--accent-primary)'
                      }`,
                      borderRadius: '0 var(--radius-sm) var(--radius-sm) 0',
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '4px' }}>
                      <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--text-main)' }}>
                        {rec.title}
                      </span>
                      <Badge value={rec.priority} />
                    </div>
                    <p style={{ fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '8px' }}>
                      {rec.description}
                    </p>
                    <div
                      style={{
                        padding: '6px 10px',
                        backgroundColor: 'var(--bg-input)',
                        borderRadius: 'var(--radius-sm)',
                        fontSize: '11px',
                        color: 'var(--accent-cyan)',
                        fontFamily: 'JetBrains Mono, monospace',
                      }}
                    >
                      Action: {rec.actionableStep}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </Card>
        </div>

        {/* Right Column: Risk Factors, Action Items & Root Causes */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          {/* Explainable Risk Factors */}
          <Card title="Explainable Risk Factors" subtitle="Identified reliability vulnerabilities">
            {detail.riskFactors.length === 0 ? (
              <div style={{ fontSize: '13px', color: 'var(--status-healthy)' }}>
                No active risk factors identified.
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                {detail.riskFactors.map((rf, idx) => (
                  <div
                    key={idx}
                    style={{
                      padding: '8px 12px',
                      backgroundColor: 'rgba(239, 68, 68, 0.08)',
                      border: '1px solid rgba(239, 68, 68, 0.2)',
                      borderRadius: 'var(--radius-sm)',
                      fontSize: '12px',
                      color: '#fca5a5',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '8px',
                    }}
                  >
                    <AlertCircle size={15} color="var(--status-critical)" />
                    <span>{rf}</span>
                  </div>
                ))}
              </div>
            )}
          </Card>

          {/* Historical Root Causes */}
          <Card title="Historical Failure Patterns" subtitle="Frequency of diagnosed root causes">
            {Object.keys(detail.commonRootCauses).length === 0 ? (
              <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                No historical root causes logged.
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                {Object.entries(detail.commonRootCauses).map(([cause, count], i) => (
                  <div
                    key={i}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      padding: '6px 10px',
                      backgroundColor: 'var(--bg-card-elevated)',
                      borderRadius: 'var(--radius-sm)',
                      fontSize: '12px',
                    }}
                  >
                    <span style={{ color: 'var(--text-secondary)' }}>{cause}</span>
                    <span style={{ fontWeight: 700, color: 'var(--accent-primary)' }}>{count}x</span>
                  </div>
                ))}
              </div>
            )}
          </Card>

          {/* Open Action Items */}
          <Card title="Postmortem Action Items" subtitle={`${detail.openActionItems.length} open items for this service`}>
            {detail.openActionItems.length === 0 ? (
              <div style={{ fontSize: '12px', color: 'var(--status-healthy)' }}>
                All postmortem action items completed.
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                {detail.openActionItems.map((item) => (
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
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                      <span style={{ fontWeight: 600, color: 'var(--text-main)' }}>{item.title}</span>
                      <Badge value={item.priority} />
                    </div>
                    <div style={{ display: 'flex', gap: '10px', marginTop: '4px', color: 'var(--text-muted)', fontSize: '11px' }}>
                      <span>Assignee: {item.assignedTo || 'Unassigned'}</span>
                      {item.overdue && <span style={{ color: '#fca5a5', fontWeight: 600 }}>OVERDUE</span>}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </Card>
        </div>
      </div>
    </div>
  );
};
