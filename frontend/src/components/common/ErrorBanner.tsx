import React from 'react';
import { AlertCircle, RefreshCw } from 'lucide-react';
import { Button } from './Button';

interface ErrorBannerProps {
  message: string;
  onRetry?: () => void;
}

export const ErrorBanner: React.FC<ErrorBannerProps> = ({ message, onRetry }) => {
  return (
    <div
      style={{
        padding: '16px 20px',
        backgroundColor: 'var(--status-critical-bg)',
        border: '1px solid var(--status-critical-border)',
        borderRadius: 'var(--radius-md)',
        color: '#fca5a5',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        marginBottom: '20px',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
        <AlertCircle size={20} color="var(--status-critical)" />
        <span style={{ fontSize: '13px', fontWeight: 500 }}>{message}</span>
      </div>
      {onRetry && (
        <Button variant="secondary" size="sm" icon={<RefreshCw size={14} />} onClick={onRetry}>
          Retry
        </Button>
      )}
    </div>
  );
};
