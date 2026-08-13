import React from 'react';

interface StatCardProps {
  label: string;
  value: string | number;
  subtext?: React.ReactNode;
  icon?: React.ReactNode;
  variant?: 'default' | 'critical' | 'warning' | 'healthy' | 'accent';
  onClick?: () => void;
}

export const StatCard: React.FC<StatCardProps> = ({
  label,
  value,
  subtext,
  icon,
  variant = 'default',
  onClick,
}) => {
  return (
    <div
      className={`kpi-card kpi-${variant}`}
      onClick={onClick}
      style={{ cursor: onClick ? 'pointer' : 'default' }}
    >
      <div className="kpi-label">
        <span>{label}</span>
        {icon && <span style={{ color: 'var(--text-muted)' }}>{icon}</span>}
      </div>
      <div className="kpi-value">{value}</div>
      {subtext && <div className="kpi-subtext">{subtext}</div>}
    </div>
  );
};
