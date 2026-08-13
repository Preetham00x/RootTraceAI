import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { commandCenterApi } from '../api/commandCenterApi';
import type { ServiceHealthSummary } from '../types';
import { Badge } from '../components/common/Badge';
import { Card } from '../components/common/Card';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorBanner } from '../components/common/ErrorBanner';
import { Server, Search, ArrowRight, ShieldAlert } from 'lucide-react';

export const Services: React.FC = () => {
  const [services, setServices] = useState<ServiceHealthSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Filters & Search
  const [search, setSearch] = useState('');
  const [riskFilter, setRiskFilter] = useState<string>('ALL');
  const [sortBy, setSortBy] = useState<string>('risk');

  const navigate = useNavigate();

  const loadServices = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await commandCenterApi.getServiceSummaries(30, 100, sortBy);
      setServices(data);
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to load services.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadServices();
  }, [sortBy]);

  const filteredServices = services.filter((svc) => {
    const matchesSearch = svc.serviceName.toLowerCase().includes(search.toLowerCase());
    const matchesRisk = riskFilter === 'ALL' || svc.riskTier === riskFilter;
    return matchesSearch && matchesRisk;
  });

  if (isLoading) {
    return <LoadingSpinner message="Querying microservices health telemetry..." />;
  }

  if (error) {
    return <ErrorBanner message={error} onRetry={loadServices} />;
  }

  const criticalCount = services.filter((s) => s.riskTier === 'CRITICAL' || s.riskTier === 'HIGH').length;

  return (
    <div>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px' }}>
        <div>
          <h1 className="page-title">
            <Server size={22} color="var(--accent-cyan)" />
            <span>Monitored Services Directory</span>
          </h1>
          <p className="page-subtitle">
            Reliability health scores, proactive risk tiers, error budget burn rates, and active SLO compliance
          </p>
        </div>
        {criticalCount > 0 && (
          <div
            style={{
              padding: '6px 12px',
              backgroundColor: 'var(--status-critical-bg)',
              border: '1px solid var(--status-critical-border)',
              borderRadius: 'var(--radius-sm)',
              color: '#fca5a5',
              fontSize: '12px',
              fontWeight: 600,
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
            }}
          >
            <ShieldAlert size={16} />
            <span>{criticalCount} Services At Elevated Risk</span>
          </div>
        )}
      </div>

      {/* Filter and Search Bar */}
      <Card className="mb-4" style={{ marginBottom: '16px', padding: '12px 16px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px', flexWrap: 'wrap' }}>
          {/* Search */}
          <div style={{ flex: 1, minWidth: '240px', position: 'relative' }}>
            <Search size={16} style={{ position: 'absolute', left: '10px', top: '9px', color: 'var(--text-muted)' }} />
            <input
              type="text"
              className="form-input"
              style={{ paddingLeft: '32px' }}
              placeholder="Search services by name..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>

          {/* Risk Tier Filter */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>Risk Tier:</span>
            <select
              className="form-select"
              style={{ width: 'auto' }}
              value={riskFilter}
              onChange={(e) => setRiskFilter(e.target.value)}
            >
              <option value="ALL">All Tiers</option>
              <option value="CRITICAL">Critical Risk</option>
              <option value="HIGH">High Risk</option>
              <option value="MEDIUM">Medium Risk</option>
              <option value="LOW">Low Risk</option>
            </select>
          </div>

          {/* Sort By */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>Sort:</span>
            <select
              className="form-select"
              style={{ width: 'auto' }}
              value={sortBy}
              onChange={(e) => setSortBy(e.target.value)}
            >
              <option value="risk">Highest Risk First</option>
              <option value="incidents">Most Incidents</option>
              <option value="mttr">Highest MTTR</option>
              <option value="slo">Lowest SLO Compliance</option>
              <option value="name">Service Name (A-Z)</option>
            </select>
          </div>
        </div>
      </Card>

      {/* Services Table */}
      <Card>
        <div className="sre-table-container">
          <table className="sre-table">
            <thead>
              <tr>
                <th>Service Name</th>
                <th>Health Score</th>
                <th>Risk Tier</th>
                <th>Incidents (30d)</th>
                <th>MTTR</th>
                <th>SLO Compliance</th>
                <th>Avg Error Budget</th>
                <th>Open Actions</th>
                <th>Runbook Status</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {filteredServices.length === 0 ? (
                <tr>
                  <td colSpan={10} style={{ textAlign: 'center', padding: '32px', color: 'var(--text-muted)' }}>
                    No services match the active filters.
                  </td>
                </tr>
              ) : (
                filteredServices.map((svc) => (
                  <tr
                    key={svc.serviceName}
                    onClick={() => navigate(`/services/${svc.serviceName}`)}
                    style={{ cursor: 'pointer' }}
                  >
                    <td>
                      <div style={{ fontWeight: 600, color: 'var(--accent-cyan)' }}>{svc.serviceName}</div>
                      <div style={{ fontSize: '11px', color: 'var(--text-dim)' }}>{svc.environment}</div>
                    </td>
                    <td>
                      <span style={{ fontWeight: 700, fontSize: '14px' }}>{svc.healthScore.toFixed(1)}</span>
                      <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}> / 100</span>
                    </td>
                    <td>
                      <Badge value={svc.riskTier} />
                    </td>
                    <td>
                      <span style={{ fontWeight: 600 }}>{svc.totalIncidents30d}</span>
                      {svc.activeIncidents > 0 && (
                        <span style={{ color: '#fca5a5', fontWeight: 600, marginLeft: '4px' }}>
                          ({svc.activeIncidents} active)
                        </span>
                      )}
                    </td>
                    <td>{svc.meanTimeToResolveMinutes.toFixed(0)}m</td>
                    <td>
                      <span style={{ color: svc.breachedSlos > 0 ? '#fca5a5' : 'var(--text-main)', fontWeight: 600 }}>
                        {svc.totalSlos - svc.breachedSlos} / {svc.totalSlos}
                      </span>
                      {svc.breachedSlos > 0 && (
                        <span style={{ fontSize: '10px', color: '#fca5a5', display: 'block' }}>
                          {svc.breachedSlos} breached
                        </span>
                      )}
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
                    <td>
                      {svc.openActionItems > 0 ? (
                        <span>
                          {svc.openActionItems}{' '}
                          {svc.overdueActionItems > 0 && (
                            <strong style={{ color: '#fca5a5' }}>({svc.overdueActionItems} overdue)</strong>
                          )}
                        </span>
                      ) : (
                        <span style={{ color: 'var(--text-dim)' }}>0</span>
                      )}
                    </td>
                    <td>
                      {svc.recentRunbookFailures > 0 ? (
                        <span style={{ color: '#fca5a5', fontWeight: 600 }}>
                          {svc.recentRunbookFailures} failed
                        </span>
                      ) : (
                        <span style={{ color: 'var(--status-healthy)' }}>Healthy</span>
                      )}
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      <ArrowRight size={14} color="var(--text-muted)" />
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
};
