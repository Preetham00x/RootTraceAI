import React from 'react';

interface CardProps {
  title?: React.ReactNode;
  subtitle?: string;
  action?: React.ReactNode;
  children: React.ReactNode;
  className?: string;
  style?: React.CSSProperties;
}

export const Card: React.FC<CardProps> = ({ title, subtitle, action, children, className = '', style }) => {
  return (
    <div className={`sre-card ${className}`} style={style}>
      {(title || action) && (
        <div className="sre-card-header">
          <div>
            {title && <div className="sre-card-title">{title}</div>}
            {subtitle && <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '2px' }}>{subtitle}</div>}
          </div>
          {action && <div>{action}</div>}
        </div>
      )}
      {children}
    </div>
  );
};
