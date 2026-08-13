import React from 'react';
import type { IncidentSeverity, IncidentStatus, RiskTier, RunbookStatus, SloStatus } from '../../types';

interface BadgeProps {
  type?: 'severity' | 'status' | 'risk' | 'slo' | 'runbook' | 'custom';
  value: IncidentSeverity | IncidentStatus | RiskTier | SloStatus | RunbookStatus | string;
  className?: string;
}

export const Badge: React.FC<BadgeProps> = ({ value, className = '' }) => {
  const val = String(value).toUpperCase();

  let badgeClass = 'badge-low';

  if (val === 'CRITICAL' || val === 'BREACHED' || val === 'FAILED') {
    badgeClass = 'badge-critical';
  } else if (val === 'HIGH' || val === 'WARNING' || val === 'INVESTIGATING') {
    badgeClass = 'badge-high';
  } else if (val === 'MEDIUM' || val === 'DRAFT' || val === 'IN_REVIEW') {
    badgeClass = 'badge-medium';
  } else if (val === 'LOW' || val === 'REQUESTED' || val === 'RUNNING') {
    badgeClass = 'badge-low';
  } else if (val === 'HEALTHY' || val === 'RESOLVED' || val === 'SUCCEEDED' || val === 'PUBLISHED' || val === 'COMPLETED') {
    badgeClass = 'badge-healthy';
  } else if (val === 'OPEN') {
    badgeClass = 'badge-open';
  } else if (val === 'CLOSED' || val === 'CANCELLED' || val === 'SKIPPED') {
    badgeClass = 'badge-closed';
  }

  return (
    <span className={`badge ${badgeClass} ${className}`}>
      {val}
    </span>
  );
};
