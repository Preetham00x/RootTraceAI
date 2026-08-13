import React, { useEffect, useState } from 'react';
import { intelligenceApi } from '../api/intelligenceApi';
import { incidentsApi } from '../api/incidentsApi';
import type { IncidentBriefing, IncidentCluster, IncidentSummary, SreMetricsSummary } from '../types';
import { Card } from '../components/common/Card';
import { StatCard } from '../components/common/StatCard';
import { Button } from '../components/common/Button';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorBanner } from '../components/common/ErrorBanner';
import {
  BrainCircuit,
  Clock,
  Layers,
  Sparkles,
  TrendingUp,
  AlertCircle,
  CheckCircle,
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

export const Intelligence: React.FC = () => {
  const [metrics, setMetrics] = useState<SreMetricsSummary | null>(null);
  const [trends, setTrends] = useState<{ date: string; incidentCount: number; mttrMinutes: number }[]>([]);
  const [clusters, setClusters] = useState<IncidentCluster[]>([]);
  const [incidents, setIncidents] = useState<IncidentSummary[]>([]);
  const [selectedIncidentId, setSelectedIncidentId] = useState<string>('');
  const [briefing, setBriefing] = useState<IncidentBriefing | null>(null);
  const [briefingLoading, setBriefingLoading] = useState(false);

  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadIntelligenceData = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const [metricsData, trendsData, clustersData, incidentsPage] = await Promise.all([
        intelligenceApi.getSreMetrics(30),
        intelligenceApi.getIncidentTrends(30, 'daily'),
        intelligenceApi.getClusters(),
        incidentsApi.listIncidents({ size: 10 }),
      ]);
      setMetrics(metricsData);
      setTrends(trendsData.dataPoints || []);
      setClusters(clustersData.clusters || []);
      setIncidents(incidentsPage.content || []);
      if (incidentsPage.content?.length > 0) {
        setSelectedIncidentId(incidentsPage.content[0].id);
      }
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to compile SRE intelligence telemetry.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadIntelligenceData();
  }, []);

  const handleGenerateBriefing = async () => {
    if (!selectedIncidentId) return;
    setBriefingLoading(true);
    try {
      const briefingData = await intelligenceApi.getIncidentBriefing(selectedIncidentId);
      setBriefing(briefingData);
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to generate AI incident briefing.');
    } finally {
      setBriefingLoading(false);
    }
  };

  if (isLoading) {
    return <LoadingSpinner message="Synthesizing multi-dimensional correlation graphs & SRE metrics..." />;
  }

  if (error || !metrics) {
    return <ErrorBanner message={error || 'Failed to aggregate intelligence.'} onRetry={loadIntelligenceData} />;
  }

  return (
    <div>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '24px' }}>
        <div>
          <h1 className="page-title">
            <BrainCircuit size={22} color="var(--accent-purple)" />
            <span>SRE Intelligence & Failure Correlation</span>
          </h1>
          <p className="page-subtitle">
            Systemic recurrence detection, cross-service failure clustering, and retrospective AI briefings
          </p>
        </div>
      </div>

      {/* SRE Metrics Overview KPI Grid */}
      <div className="kpi-grid">
        <StatCard
          label="MTTR (Mean Time to Resolve)"
          value={`${(metrics.meanTimeToResolveMinutes ?? 0).toFixed(0)} min`}
          variant="accent"
          icon={<Clock size={18} />}
          subtext="Target: < 45 min"
        />
        <StatCard
          label="MTTD (Mean Time to Detect)"
          value={`${(metrics.meanTimeToDetectMinutes ?? 0).toFixed(0)} min`}
          variant="healthy"
          icon={<Clock size={18} />}
          subtext="Target: < 15 min"
        />
        <StatCard
          label="Recurrence Rate"
          value={`${((metrics.recurrenceRatePercentage ?? (metrics as any).recurrenceRate ?? 0)).toFixed(1)}%`}
          variant={((metrics.recurrenceRatePercentage ?? (metrics as any).recurrenceRate ?? 0)) > 20 ? 'warning' : 'healthy'}
          icon={<TrendingUp size={18} />}
          subtext="Repeated failure patterns"
        />
        <StatCard
          label="Resolved Incidents"
          value={`${metrics.resolvedIncidents ?? 0} / ${metrics.totalIncidents ?? 0}`}
          variant="healthy"
          icon={<CheckCircle size={18} />}
          subtext="30-day resolution rate"
        />
      </div>

      {/* Incident Frequency & MTTR Curves */}
      <Card title="Historical Reliability & MTTR Trends (30d)" subtitle="Operational recovery velocity over time" style={{ marginBottom: '24px' }}>
        <div style={{ height: '220px', width: '100%' }}>
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={trends} margin={{ top: 10, right: 20, left: -20, bottom: 0 }}>
              <defs>
                <linearGradient id="mttrGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#8b5cf6" stopOpacity={0.4} />
                  <stop offset="95%" stopColor="#8b5cf6" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
              <XAxis dataKey="date" stroke="#64748b" fontSize={11} tickLine={false} />
              <YAxis stroke="#64748b" fontSize={11} tickLine={false} />
              <Tooltip
                contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', borderRadius: '4px', fontSize: '12px' }}
              />
              <Area type="monotone" dataKey="mttrMinutes" stroke="#8b5cf6" fillOpacity={1} fill="url(#mttrGrad)" name="MTTR (min)" />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      </Card>

      {/* 2-Column Section: Failure Clusters & Interactive AI Briefing */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px', marginBottom: '24px' }}>
        {/* Left Column: Failure Clusters */}
        <Card
          title={
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <Layers size={18} color="var(--accent-cyan)" />
              <span>Incident Failure Clusters</span>
            </div>
          }
          subtitle="Grouped by shared symptoms, service concentration, and root causes"
        >
          {clusters.length === 0 ? (
            <div style={{ padding: '24px', textAlign: 'center', color: 'var(--text-muted)' }}>
              No recurring failure clusters detected.
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', maxHeight: '500px', overflowY: 'auto' }}>
              {clusters.map((c) => (
                <div
                  key={c.clusterId}
                  style={{
                    padding: '14px',
                    backgroundColor: 'var(--bg-card-elevated)',
                    border: '1px solid var(--border-subtle)',
                    borderRadius: 'var(--radius-sm)',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '4px' }}>
                    <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--accent-cyan)' }}>
                      {c.service}
                    </span>
                    <span className="badge badge-warning">{c.incidentCount} Incidents</span>
                  </div>
                  <p style={{ fontSize: '12px', color: 'var(--text-main)', marginBottom: '8px' }}>
                    {c.patternDescription}
                  </p>
                  <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginBottom: '6px' }}>
                    <strong>Shared Symptoms:</strong> {Array.isArray(c.sharedSymptoms) ? c.sharedSymptoms.join(', ') : String(c.sharedSymptoms || '')}
                  </div>
                  <div style={{ padding: '6px 10px', backgroundColor: 'var(--bg-input)', borderRadius: 'var(--radius-sm)', fontSize: '11px', color: '#93c5fd' }}>
                    <strong>SRE Action:</strong> {c.suggestedAction}
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>

        {/* Right Column: AI Incident Retrospective Briefing */}
        <Card
          title={
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <Sparkles size={18} color="#c084fc" />
              <span>AI Incident Intelligence Briefing</span>
              <span className="badge badge-ai">Retrospective</span>
            </div>
          }
          subtitle="Generate actionable briefing from historical incident resolutions"
        >
          <div style={{ display: 'flex', gap: '10px', marginBottom: '16px' }}>
            <select
              className="form-select"
              value={selectedIncidentId}
              onChange={(e) => setSelectedIncidentId(e.target.value)}
            >
              {incidents.map((inc) => (
                <option key={inc.id} value={inc.id}>
                  [{inc.severity}] {inc.title} ({inc.service})
                </option>
              ))}
            </select>
            <Button
              variant="ai"
              icon={<Sparkles size={14} />}
              loading={briefingLoading}
              onClick={handleGenerateBriefing}
            >
              Generate
            </Button>
          </div>

          {briefingLoading ? (
            <LoadingSpinner message="Consulting historical vector repository & generating briefing..." />
          ) : !briefing ? (
            <div style={{ padding: '32px 20px', textAlign: 'center', color: 'var(--text-muted)', fontSize: '13px' }}>
              Select an incident above and click "Generate" to synthesize past lessons and investigation steps.
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '14px', maxHeight: '420px', overflowY: 'auto' }}>
              <div>
                <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: '4px' }}>
                  Executive Summary
                </div>
                <p style={{ fontSize: '13px', color: 'var(--text-secondary)', lineHeight: 1.5 }}>
                  {briefing.executiveSummary}
                </p>
              </div>

              {briefing.recurringIssueDetected && (
                <div style={{ padding: '8px 12px', backgroundColor: 'var(--status-critical-bg)', border: '1px solid var(--status-critical-border)', borderRadius: 'var(--radius-sm)', color: '#fca5a5', fontSize: '12px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <AlertCircle size={15} />
                  <span>Historical pattern matched: This is a recurring failure mode.</span>
                </div>
              )}

              {briefing.provenInvestigationSteps?.length > 0 && (
                <div>
                  <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: '4px' }}>
                    Proven Investigation Steps
                  </div>
                  <ul style={{ paddingLeft: '18px', fontSize: '12px', color: 'var(--text-secondary)', display: 'flex', flexDirection: 'column', gap: '4px' }}>
                    {briefing.provenInvestigationSteps.map((s, i) => (
                      <li key={i}>{s}</li>
                    ))}
                  </ul>
                </div>
              )}

              {briefing.postmortemLessonsLearned?.length > 0 && (
                <div>
                  <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: '4px' }}>
                    Lessons from Past Postmortems
                  </div>
                  <ul style={{ paddingLeft: '18px', fontSize: '12px', color: 'var(--text-secondary)', display: 'flex', flexDirection: 'column', gap: '4px' }}>
                    {briefing.postmortemLessonsLearned.map((l, i) => (
                      <li key={i}>{l}</li>
                    ))}
                  </ul>
                </div>
              )}

              {briefing.recommendedTriageActions?.length > 0 && (
                <div>
                  <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: '4px' }}>
                    Recommended Triage Actions
                  </div>
                  <ol style={{ paddingLeft: '18px', fontSize: '12px', color: '#93c5fd', display: 'flex', flexDirection: 'column', gap: '4px' }}>
                    {briefing.recommendedTriageActions.map((a, i) => (
                      <li key={i}>{a}</li>
                    ))}
                  </ol>
                </div>
              )}
            </div>
          )}
        </Card>
      </div>
    </div>
  );
};
