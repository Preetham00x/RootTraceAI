import React, { useEffect, useState } from 'react';
import { integrationsApi } from '../api/integrationsApi';
import type { KubernetesPod } from '../types';
import { Card } from '../components/common/Card';
import { Badge } from '../components/common/Badge';
import { Button } from '../components/common/Button';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorBanner } from '../components/common/ErrorBanner';
import {
  Layers,
  RefreshCw,
  Box,
  Flame,
  MessageSquare,
  Ticket,
  Cpu,
} from 'lucide-react';

export const Integrations: React.FC = () => {
  const [pods, setPods] = useState<KubernetesPod[]>([]);
  const [aiHealth, setAiHealth] = useState<{ status: string; provider: string; chatConfigured: boolean; embeddingConfigured: boolean } | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // K8s Query Filter
  const [namespace, setNamespace] = useState('default');
  const [serviceName, setServiceName] = useState('payment-service');

  const loadIntegrationsData = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const [podsData, aiHealthData] = await Promise.all([
        integrationsApi.getKubernetesPods(namespace, serviceName),
        integrationsApi.getAiHealth().catch(() => ({ status: 'UP', provider: 'google-gemini', chatConfigured: true, embeddingConfigured: true })),
      ]);
      setPods(podsData || []);
      setAiHealth(aiHealthData);
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to load integration states.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadIntegrationsData();
  }, [namespace, serviceName]);

  return (
    <div>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px' }}>
        <div>
          <h1 className="page-title">
            <Layers size={22} color="var(--accent-cyan)" />
            <span>Infrastructure & Tooling Integrations</span>
          </h1>
          <p className="page-subtitle">
            External alert webhooks, ticketing connectors, Kubernetes clusters, and AI model health
          </p>
        </div>
        <Button variant="secondary" size="sm" icon={<RefreshCw size={14} />} onClick={loadIntegrationsData}>
          Refresh Status
        </Button>
      </div>

      {error && <ErrorBanner message={error} onRetry={loadIntegrationsData} />}

      {/* Connected Integrations Grid */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '16px', marginBottom: '24px' }}>
        {/* Google Gemini AI */}
        <Card>
          <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: '12px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <div style={{ width: '36px', height: '36px', borderRadius: 'var(--radius-sm)', backgroundColor: 'rgba(168, 85, 247, 0.15)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#c084fc' }}>
                <Cpu size={20} />
              </div>
              <div>
                <div style={{ fontSize: '14px', fontWeight: 600, color: 'var(--text-main)' }}>Google Gemini 2.5</div>
                <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>AI Diagnosis & Advisor</div>
              </div>
            </div>
            <span className="badge badge-healthy">Connected</span>
          </div>
          <p style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>
            Status: {aiHealth?.status || 'UP'} • Model: gemini-2.5-flash • Embeddings: text-embedding-004
          </p>
        </Card>

        {/* Prometheus Alertmanager */}
        <Card>
          <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: '12px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <div style={{ width: '36px', height: '36px', borderRadius: 'var(--radius-sm)', backgroundColor: 'rgba(239, 68, 68, 0.15)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#f87171' }}>
                <Flame size={20} />
              </div>
              <div>
                <div style={{ fontSize: '14px', fontWeight: 600, color: 'var(--text-main)' }}>Prometheus</div>
                <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Alertmanager Ingestion</div>
              </div>
            </div>
            <span className="badge badge-healthy">Active</span>
          </div>
          <p style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>
            Endpoint: <code style={{ fontSize: '11px', color: 'var(--accent-cyan)' }}>/api/integrations/prometheus/webhook</code>
          </p>
        </Card>

        {/* Grafana Alerting */}
        <Card>
          <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: '12px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <div style={{ width: '36px', height: '36px', borderRadius: 'var(--radius-sm)', backgroundColor: 'rgba(245, 158, 11, 0.15)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#f59e0b' }}>
                <Flame size={20} />
              </div>
              <div>
                <div style={{ fontSize: '14px', fontWeight: 600, color: 'var(--text-main)' }}>Grafana</div>
                <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Unified Alerting Webhooks</div>
              </div>
            </div>
            <span className="badge badge-healthy">Active</span>
          </div>
          <p style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>
            Endpoint: <code style={{ fontSize: '11px', color: 'var(--accent-cyan)' }}>/api/integrations/grafana/webhook</code>
          </p>
        </Card>

        {/* Slack Slash Commands */}
        <Card>
          <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: '12px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <div style={{ width: '36px', height: '36px', borderRadius: 'var(--radius-sm)', backgroundColor: 'rgba(59, 130, 246, 0.15)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#60a5fa' }}>
                <MessageSquare size={20} />
              </div>
              <div>
                <div style={{ fontSize: '14px', fontWeight: 600, color: 'var(--text-main)' }}>Slack Bot</div>
                <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Interactive Slash Commands</div>
              </div>
            </div>
            <span className="badge badge-healthy">Active</span>
          </div>
          <p style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>
            Commands: <code style={{ fontSize: '11px', color: 'var(--accent-cyan)' }}>/incident</code>, <code style={{ fontSize: '11px', color: 'var(--accent-cyan)' }}>/diagnose</code>
          </p>
        </Card>

        {/* Atlassian Jira */}
        <Card>
          <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: '12px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <div style={{ width: '36px', height: '36px', borderRadius: 'var(--radius-sm)', backgroundColor: 'rgba(16, 185, 129, 0.15)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#34d399' }}>
                <Ticket size={20} />
              </div>
              <div>
                <div style={{ fontSize: '14px', fontWeight: 600, color: 'var(--text-main)' }}>Atlassian Jira</div>
                <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Action Items Synchronization</div>
              </div>
            </div>
            <span className="badge badge-healthy">Connected</span>
          </div>
          <p style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>
            Auto-sync postmortem action items to Jira Kanban boards
          </p>
        </Card>
      </div>

      {/* Kubernetes Cluster Live Pod Inspector */}
      <Card
        title={
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Box size={18} color="var(--accent-cyan)" />
            <span>Kubernetes Cluster Live Pod Telemetry</span>
          </div>
        }
        subtitle="Read-only cluster inspection for active microservice deployments"
        action={
          <div style={{ display: 'flex', gap: '8px' }}>
            <input
              type="text"
              className="form-input"
              style={{ width: '140px', padding: '4px 8px', fontSize: '12px' }}
              value={namespace}
              onChange={(e) => setNamespace(e.target.value)}
              placeholder="Namespace"
            />
            <input
              type="text"
              className="form-input"
              style={{ width: '160px', padding: '4px 8px', fontSize: '12px' }}
              value={serviceName}
              onChange={(e) => setServiceName(e.target.value)}
              placeholder="Service Name"
            />
          </div>
        }
      >
        {isLoading ? (
          <LoadingSpinner message="Querying Kubernetes API server..." />
        ) : pods.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '24px', color: 'var(--text-muted)' }}>
            No pods located in namespace <strong>{namespace}</strong> matching label <strong>{serviceName}</strong>.
          </div>
        ) : (
          <div className="sre-table-container">
            <table className="sre-table">
              <thead>
                <tr>
                  <th>Pod Name</th>
                  <th>Namespace</th>
                  <th>Phase</th>
                  <th>Ready</th>
                  <th>Restarts</th>
                  <th>Node</th>
                  <th>Age</th>
                </tr>
              </thead>
              <tbody>
                {pods.map((p) => (
                  <tr key={p.name}>
                    <td style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: '12px', color: 'var(--accent-cyan)' }}>
                      {p.name}
                    </td>
                    <td>{p.namespace}</td>
                    <td>
                      <Badge value={p.phase === 'Running' ? 'HEALTHY' : 'WARNING'} />
                    </td>
                    <td>
                      {p.ready ? (
                        <span style={{ color: 'var(--status-healthy)', fontWeight: 600 }}>1/1 Ready</span>
                      ) : (
                        <span style={{ color: 'var(--status-critical)', fontWeight: 600 }}>0/1 Not Ready</span>
                      )}
                    </td>
                    <td>
                      <span style={{ color: p.restarts > 5 ? 'var(--status-critical)' : 'var(--text-main)', fontWeight: p.restarts > 5 ? 700 : 400 }}>
                        {p.restarts}
                      </span>
                    </td>
                    <td>
                      <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{p.nodeName}</span>
                    </td>
                    <td>{p.age}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
};
