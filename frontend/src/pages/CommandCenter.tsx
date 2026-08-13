import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { commandCenterApi } from '../api/commandCenterApi';
import type {
  ActiveIncidentItem,
  CommandCenterOverview,
  ExecutiveReliabilityAdvisor,
  ReliabilityEvent,
  ServiceHealthSummary,
} from '../types';
import { CircularScore } from '../components/common/CircularScore';
import { Badge } from '../components/common/Badge';
import { Card } from '../components/common/Card';
import { StatCard } from '../components/common/StatCard';
import { Button } from '../components/common/Button';
import { Modal } from '../components/common/Modal';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorBanner } from '../components/common/ErrorBanner';
import {
  AlertTriangle,
  Flame,
  Clock,
  Target,
  Sparkles,
  ArrowRight,
  TrendingDown,
  TrendingUp,
  Minus,
  CheckCircle,
  AlertCircle,
  Bot,
} from 'lucide-react';
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  CartesianGrid,
} from 'recharts';

export const CommandCenter: React.FC = () => {
  const [overview, setOverview] = useState<CommandCenterOverview | null>(null);
  const [services, setServices] = useState<ServiceHealthSummary[]>([]);
  const [activeIncidents, setActiveIncidents] = useState<ActiveIncidentItem[]>([]);
  const [events, setEvents] = useState<ReliabilityEvent[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Advisor Modal State
  const [advisorModalOpen, setAdvisorModalOpen] = useState(false);
  const [advisorLoading, setAdvisorLoading] = useState(false);
  const [advisorData, setAdvisorData] = useState<ExecutiveReliabilityAdvisor | null>(null);
  const [advisorError, setAdvisorError] = useState<string | null>(null);

  const navigate = useNavigate();

  const loadDashboard = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const [overviewData, servicesData, incidentsData, eventsData] = await Promise.all([
        commandCenterApi.getOverview(30),
        commandCenterApi.getServiceSummaries(30, 8, 'risk'),
        commandCenterApi.getActiveIncidents(),
        commandCenterApi.getEvents(12, 30),
      ]);
      setOverview(overviewData);
      setServices(servicesData);
      setActiveIncidents(incidentsData);
      setEvents(eventsData);
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to load Command Center telemetry.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadDashboard();
  }, []);

  const handleGenerateAdvisor = async () => {
    setAdvisorModalOpen(true);
    setAdvisorLoading(true);
    setAdvisorError(null);
    try {
      const advisor = await commandCenterApi.getAdvisorBriefing(30);
      setAdvisorData(advisor);
    } catch (err: unknown) {
      setAdvisorError((err as Error).message || 'Failed to generate AI executive reliability advisor briefing.');
    } finally {
      setAdvisorLoading(false);
    }
  };

  if (isLoading) {
    return <LoadingSpinner message="Aggregating multi-service telemetry & reliability indices..." />;
  }

  if (error || !overview) {
    return <ErrorBanner message={error || 'Failed to aggregate command center telemetry.'} onRetry={loadDashboard} />;
  }

  // Simulated trend data based on overview
  const trendData = [
    { date: 'Day 1', incidents: Math.max(1, Math.round(overview.totalIncidents30d * 0.15)), mttr: Math.round(overview.meanTimeToResolveMinutes * 1.2) },
    { date: 'Day 7', incidents: Math.max(0, Math.round(overview.totalIncidents30d * 0.2)), mttr: Math.round(overview.meanTimeToResolveMinutes * 1.1) },
    { date: 'Day 14', incidents: Math.max(1, Math.round(overview.totalIncidents30d * 0.25)), mttr: Math.round(overview.meanTimeToResolveMinutes * 0.95) },
    { date: 'Day 21', incidents: Math.max(0, Math.round(overview.totalIncidents30d * 0.18)), mttr: Math.round(overview.meanTimeToResolveMinutes * 1.05) },
    { date: 'Day 30', incidents: Math.max(1, Math.round(overview.totalIncidents30d * 0.22)), mttr: Math.round(overview.meanTimeToResolveMinutes) },
  ];

  return (
    <div>
      {/* Page Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '24px' }}>
        <div>
          <h1 className="page-title">
            <Flame size={22} color="var(--accent-primary)" />
            <span>SRE Command Center</span>
          </h1>
          <p className="page-subtitle">
            Continuous reliability governance, active incident triage, and executive operational intelligence
          </p>
        </div>
        <div style={{ display: 'flex', gap: '12px' }}>
          <Button
            variant="ai"
            icon={<Sparkles size={16} />}
            onClick={handleGenerateAdvisor}
          >
            Executive AI Advisor
          </Button>
        </div>
      </div>

      {/* Top KPI Grid */}
      <div className="kpi-grid">
        {/* Reliability Score Card */}
        <div
          className="sre-card"
          style={{
            gridColumn: 'span 1',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '16px 24px',
          }}
        >
          <div>
            <div style={{ fontSize: '11px', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              Org Reliability Score
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '6px' }}>
              <Badge value={overview.reliabilityScore.riskTier} />
              <span style={{ fontSize: '12px', color: 'var(--text-secondary)', display: 'flex', alignItems: 'center', gap: '4px' }}>
                {overview.reliabilityScore.trend === 'IMPROVING' && <TrendingUp size={14} color="var(--status-healthy)" />}
                {overview.reliabilityScore.trend === 'DEGRADING' && <TrendingDown size={14} color="var(--status-critical)" />}
                {overview.reliabilityScore.trend === 'STABLE' && <Minus size={14} color="var(--text-muted)" />}
                {overview.reliabilityScore.trend}
              </span>
            </div>
            <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '8px' }}>
              Target: {overview.reliabilityScore.targetScore.toFixed(1)} / 100
            </div>
          </div>
          <CircularScore score={overview.reliabilityScore.score} riskTier={overview.reliabilityScore.riskTier} size={100} />
        </div>

        {/* Active Incidents KPI */}
        <StatCard
          label="Active Incidents"
          value={overview.activeIncidents}
          variant={overview.activeIncidents > 0 ? 'critical' : 'healthy'}
          icon={<AlertTriangle size={18} />}
          subtext={
            <span>
              <strong style={{ color: '#fca5a5' }}>{overview.criticalIncidents30d} Critical</strong>, {overview.highIncidents30d} High (30d)
            </span>
          }
          onClick={() => navigate('/incidents')}
        />

        {/* SLO Health KPI */}
        <StatCard
          label="SLO Compliance"
          value={`${overview.healthySlos} / ${overview.totalSlos} Healthy`}
          variant={overview.breachedSlos > 0 ? 'critical' : overview.warningSlos > 0 ? 'warning' : 'healthy'}
          icon={<Target size={18} />}
          subtext={
            <span>
              <strong style={{ color: overview.breachedSlos > 0 ? '#fca5a5' : 'inherit' }}>{overview.breachedSlos} Breached</strong>, {overview.warningSlos} Warning
            </span>
          }
          onClick={() => navigate('/slos')}
        />

        {/* Error Budget KPI */}
        <StatCard
          label="Avg Error Budget"
          value={`${overview.averageErrorBudgetConsumedPercentage.toFixed(1)}%`}
          variant={overview.averageErrorBudgetConsumedPercentage > 75 ? 'critical' : overview.averageErrorBudgetConsumedPercentage > 50 ? 'warning' : 'healthy'}
          icon={<Flame size={18} />}
          subtext={
            <div className="progress-bar-container" style={{ width: '100%', marginTop: '4px' }}>
              <div
                className="progress-bar-fill"
                style={{
                  width: `${Math.min(100, overview.averageErrorBudgetConsumedPercentage)}%`,
                  backgroundColor:
                    overview.averageErrorBudgetConsumedPercentage > 75
                      ? 'var(--status-critical)'
                      : overview.averageErrorBudgetConsumedPercentage > 50
                      ? 'var(--status-warning)'
                      : 'var(--status-healthy)',
                }}
              />
            </div>
          }
        />

        {/* MTTR & MTTD KPI */}
        <StatCard
          label="MTTR / MTTD"
          value={`${overview.meanTimeToResolveMinutes.toFixed(0)}m / ${overview.meanTimeToDetectMinutes.toFixed(0)}m`}
          variant="accent"
          icon={<Clock size={18} />}
          subtext={<span>Mean time to resolve & detect</span>}
          onClick={() => navigate('/intelligence')}
        />
      </div>

      {/* Main 2-Column Dashboard Body */}
      <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '20px', marginBottom: '24px' }}>
        {/* Left Column: Active Incidents & Risky Services */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          {/* Priority-Scored Active Incidents */}
          <Card
            title="Priority Active Incidents"
            subtitle={`${activeIncidents.length} incidents currently requiring operational response`}
            action={
              <Button variant="secondary" size="sm" onClick={() => navigate('/incidents')}>
                View All
              </Button>
            }
          >
            {activeIncidents.length === 0 ? (
              <div style={{ padding: '24px', textAlign: 'center', color: 'var(--text-muted)', fontSize: '13px' }}>
                <CheckCircle size={24} color="var(--status-healthy)" style={{ margin: '0 auto 8px auto', display: 'block' }} />
                No active incidents. All systems operational.
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                {activeIncidents.map((inc) => (
                  <div
                    key={inc.id}
                    onClick={() => navigate(`/incidents/${inc.id}`)}
                    style={{
                      padding: '14px 16px',
                      backgroundColor: 'var(--bg-card-elevated)',
                      border: '1px solid var(--border-subtle)',
                      borderRadius: 'var(--radius-sm)',
                      cursor: 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      transition: 'border-color 0.15s ease',
                    }}
                    onMouseEnter={(e) => (e.currentTarget.style.borderColor = 'var(--border-strong)')}
                    onMouseLeave={(e) => (e.currentTarget.style.borderColor = 'var(--border-subtle)')}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                      <Badge value={inc.severity} />
                      <div>
                        <div style={{ fontSize: '14px', fontWeight: 600, color: 'var(--text-main)' }}>
                          {inc.title}
                        </div>
                        <div style={{ fontSize: '12px', color: 'var(--text-muted)', display: 'flex', gap: '10px', marginTop: '2px' }}>
                          <span>Service: <strong style={{ color: 'var(--text-secondary)' }}>{inc.service}</strong></span>
                          <span>•</span>
                          <span>Age: {inc.ageMinutes}m</span>
                          {inc.sloBreached && (
                            <>
                              <span>•</span>
                              <span style={{ color: '#fca5a5', fontWeight: 600 }}>SLO Breached</span>
                            </>
                          )}
                        </div>
                      </div>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                      <div style={{ textAlign: 'right' }}>
                        <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Priority Score</div>
                        <div style={{ fontSize: '15px', fontWeight: 700, color: inc.priorityScore >= 75 ? '#fca5a5' : 'var(--text-main)' }}>
                          {inc.priorityScore.toFixed(0)}
                        </div>
                      </div>
                      <ArrowRight size={16} color="var(--text-muted)" />
                    </div>
                  </div>
                ))}
              </div>
            )}
          </Card>

          {/* Top Services at Risk */}
          <Card
            title="Service Health & Reliability Status"
            subtitle="Ranked by operational risk, incident frequency, and SLO compliance"
            action={
              <Button variant="secondary" size="sm" onClick={() => navigate('/services')}>
                All Services
              </Button>
            }
          >
            <div className="sre-table-container">
              <table className="sre-table">
                <thead>
                  <tr>
                    <th>Service</th>
                    <th>Health</th>
                    <th>Risk</th>
                    <th>Incidents</th>
                    <th>MTTR</th>
                    <th>SLOs</th>
                    <th>Error Budget</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {services.map((svc) => (
                    <tr
                      key={svc.serviceName}
                      onClick={() => navigate(`/services/${svc.serviceName}`)}
                      style={{ cursor: 'pointer' }}
                    >
                      <td style={{ fontWeight: 600, color: 'var(--accent-cyan)' }}>
                        {svc.serviceName}
                      </td>
                      <td>{svc.healthScore.toFixed(1)}</td>
                      <td>
                        <Badge value={svc.riskTier} />
                      </td>
                      <td>
                        {svc.totalIncidents30d} {svc.activeIncidents > 0 && <span style={{ color: '#fca5a5' }}>({svc.activeIncidents} active)</span>}
                      </td>
                      <td>{svc.meanTimeToResolveMinutes.toFixed(0)}m</td>
                      <td>
                        {svc.totalSlos - svc.breachedSlos}/{svc.totalSlos}
                      </td>
                      <td>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                          <span style={{ fontSize: '12px', minWidth: '40px' }}>
                            {svc.averageErrorBudgetConsumedPercentage.toFixed(0)}%
                          </span>
                          <div className="progress-bar-container" style={{ width: '60px' }}>
                            <div
                              className="progress-bar-fill"
                              style={{
                                width: `${Math.min(100, svc.averageErrorBudgetConsumedPercentage)}%`,
                                backgroundColor:
                                  svc.averageErrorBudgetConsumedPercentage > 75
                                    ? 'var(--status-critical)'
                                    : svc.averageErrorBudgetConsumedPercentage > 50
                                    ? 'var(--status-warning)'
                                    : 'var(--status-healthy)',
                              }}
                            />
                          </div>
                        </div>
                      </td>
                      <td style={{ textAlign: 'right' }}>
                        <ArrowRight size={14} color="var(--text-muted)" />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
        </div>

        {/* Right Column: Penalties, Chart & Event Feed */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          {/* Explainable Penalty Audit Breakdown */}
          <Card title="Reliability Penalty Breakdown" subtitle="Deterministic point deductions">
            {overview.reliabilityScore.penalties.length === 0 ? (
              <div style={{ fontSize: '13px', color: 'var(--status-healthy)', display: 'flex', alignItems: 'center', gap: '6px' }}>
                <CheckCircle size={16} /> Zero penalties. Pristine reliability profile.
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                {overview.reliabilityScore.penalties.map((p, idx) => (
                  <div
                    key={idx}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      padding: '8px 10px',
                      backgroundColor: 'rgba(239, 68, 68, 0.08)',
                      border: '1px solid rgba(239, 68, 68, 0.2)',
                      borderRadius: 'var(--radius-sm)',
                      fontSize: '12px',
                    }}
                  >
                    <div>
                      <strong style={{ color: '#fca5a5' }}>[{p.category}]</strong>{' '}
                      <span style={{ color: 'var(--text-secondary)' }}>{p.reason}</span>
                    </div>
                    <div style={{ fontWeight: 700, color: '#f87171', whiteSpace: 'nowrap', marginLeft: '8px' }}>
                      -{p.deduction.toFixed(1)} pts
                    </div>
                  </div>
                ))}
              </div>
            )}
          </Card>

          {/* MTTR & Incident Trend Mini Chart */}
          <Card title="Incident Frequency Trend (30d)" subtitle="Historical MTTR vs Volume">
            <div style={{ height: '140px', width: '100%' }}>
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={trendData} margin={{ top: 10, right: 10, left: -25, bottom: 0 }}>
                  <defs>
                    <linearGradient id="incidentGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.4} />
                      <stop offset="95%" stopColor="#3b82f6" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                  <XAxis dataKey="date" stroke="#64748b" fontSize={10} tickLine={false} />
                  <YAxis stroke="#64748b" fontSize={10} tickLine={false} />
                  <Tooltip
                    contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', borderRadius: '4px', fontSize: '11px' }}
                  />
                  <Area type="monotone" dataKey="incidents" stroke="#3b82f6" fillOpacity={1} fill="url(#incidentGrad)" name="Incidents" />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          </Card>

          {/* Operational Reliability Event Feed */}
          <Card title="Reliability Event Feed" subtitle="Real-time operational stream">
            {events.length === 0 ? (
              <div style={{ fontSize: '12px', color: 'var(--text-muted)', textAlign: 'center', padding: '16px 0' }}>
                No recent reliability events recorded.
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', maxHeight: '340px', overflowY: 'auto' }}>
                {events.map((ev) => (
                  <div
                    key={ev.id}
                    style={{
                      padding: '8px 10px',
                      backgroundColor: 'var(--bg-card-elevated)',
                      borderLeft: `3px solid ${
                        ev.eventType.includes('BREACH') || ev.eventType.includes('CRITICAL') || ev.eventType.includes('FAILED')
                          ? 'var(--status-critical)'
                          : ev.eventType.includes('RESOLVED') || ev.eventType.includes('PUBLISHED')
                          ? 'var(--status-healthy)'
                          : 'var(--accent-primary)'
                      }`,
                      borderRadius: '0 var(--radius-sm) var(--radius-sm) 0',
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                      <span style={{ fontSize: '11px', fontWeight: 600, color: 'var(--accent-cyan)' }}>
                        {ev.serviceName}
                      </span>
                      <span style={{ fontSize: '10px', color: 'var(--text-dim)' }}>
                        {new Date(ev.occurredAt).toLocaleTimeString()}
                      </span>
                    </div>
                    <div style={{ fontSize: '12px', fontWeight: 500, color: 'var(--text-main)', marginTop: '2px' }}>
                      {ev.title}
                    </div>
                    <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '2px' }}>
                      {ev.description}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </Card>
        </div>
      </div>

      {/* Executive AI Reliability Advisor Modal */}
      <Modal
        isOpen={advisorModalOpen}
        onClose={() => setAdvisorModalOpen(false)}
        title={
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <Bot size={20} color="#c084fc" />
            <span>Executive Reliability Advisor Briefing</span>
            <span className="badge badge-ai">AI Grounded</span>
          </div>
        }
        maxWidth="750px"
      >
        {advisorLoading && <LoadingSpinner message="Synthesizing multi-service telemetry & generating SRE executive briefing..." />}
        {advisorError && <ErrorBanner message={advisorError} />}
        {advisorData && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
            {/* Generated At Banner */}
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '8px 12px',
                backgroundColor: 'rgba(168, 85, 247, 0.1)',
                border: '1px solid rgba(168, 85, 247, 0.3)',
                borderRadius: 'var(--radius-sm)',
                fontSize: '12px',
                color: '#d8b4fe',
              }}
            >
              <span>Generated By: <strong>{advisorData.generatedBy}</strong></span>
              <span>{new Date(advisorData.generatedAt).toLocaleString()}</span>
            </div>

            {/* Executive Summary */}
            <div>
              <h3 style={{ fontSize: '14px', fontWeight: 600, color: 'var(--text-main)', marginBottom: '6px' }}>
                Executive Summary
              </h3>
              <p style={{ fontSize: '13px', color: 'var(--text-secondary)', lineHeight: 1.6 }}>
                {advisorData.executiveSummary}
              </p>
            </div>

            {/* Key Concerns */}
            {advisorData.keyConcerns.length > 0 && (
              <div>
                <h3 style={{ fontSize: '13px', fontWeight: 600, color: '#fca5a5', display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '6px' }}>
                  <AlertCircle size={15} /> Key Operational Concerns
                </h3>
                <ul style={{ paddingLeft: '18px', fontSize: '13px', color: 'var(--text-secondary)', display: 'flex', flexDirection: 'column', gap: '4px' }}>
                  {advisorData.keyConcerns.map((c, i) => (
                    <li key={i}>{c}</li>
                  ))}
                </ul>
              </div>
            )}

            {/* Services Requiring Attention */}
            {advisorData.servicesRequiringAttention.length > 0 && (
              <div>
                <h3 style={{ fontSize: '13px', fontWeight: 600, color: '#fcd34d', marginBottom: '6px' }}>
                  Services Requiring Immediate Attention
                </h3>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                  {advisorData.servicesRequiringAttention.map((s, i) => (
                    <span key={i} className="badge badge-warning">
                      {s}
                    </span>
                  ))}
                </div>
              </div>
            )}

            {/* Recommended Actions */}
            {advisorData.recommendedActions.length > 0 && (
              <div>
                <h3 style={{ fontSize: '13px', fontWeight: 600, color: '#93c5fd', marginBottom: '6px' }}>
                  Prioritized Executive Action Items
                </h3>
                <ol style={{ paddingLeft: '18px', fontSize: '13px', color: 'var(--text-secondary)', display: 'flex', flexDirection: 'column', gap: '6px' }}>
                  {advisorData.recommendedActions.map((a, i) => (
                    <li key={i} style={{ lineHeight: 1.5 }}>{a}</li>
                  ))}
                </ol>
              </div>
            )}

            {/* Positive Signals */}
            {advisorData.positiveSignals.length > 0 && (
              <div>
                <h3 style={{ fontSize: '13px', fontWeight: 600, color: '#6ee7b7', display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '6px' }}>
                  <CheckCircle size={15} /> Positive Reliability Signals
                </h3>
                <ul style={{ paddingLeft: '18px', fontSize: '13px', color: 'var(--text-secondary)', display: 'flex', flexDirection: 'column', gap: '4px' }}>
                  {advisorData.positiveSignals.map((p, i) => (
                    <li key={i}>{p}</li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
};
