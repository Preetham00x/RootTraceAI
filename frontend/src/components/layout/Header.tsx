import React from 'react';
import { useAuth } from '../../context/AuthContext';
import { LogOut, Globe } from 'lucide-react';

export const Header: React.FC = () => {
  const { user, logout } = useAuth();

  return (
    <header className="top-header">
      {/* Environment pill */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '6px',
            padding: '4px 10px',
            borderRadius: 'var(--radius-sm)',
            backgroundColor: 'rgba(59, 130, 246, 0.1)',
            border: '1px solid rgba(59, 130, 246, 0.25)',
            fontSize: '11px',
            fontWeight: 600,
            color: 'var(--accent-primary)',
            textTransform: 'uppercase',
            letterSpacing: '0.05em',
          }}
        >
          <Globe size={13} />
          <span>PRODUCTION • US-EAST-1</span>
        </div>
      </div>

      {/* User profile & actions */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
        {user && (
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <div style={{ textAlign: 'right' }}>
              <div style={{ fontSize: '13px', fontWeight: 600, color: 'var(--text-main)' }}>
                {user.firstName} {user.lastName}
              </div>
              <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                {user.email}
              </div>
            </div>

            {/* Role chip */}
            <div
              style={{
                padding: '2px 8px',
                borderRadius: 'var(--radius-sm)',
                fontSize: '10px',
                fontWeight: 700,
                textTransform: 'uppercase',
                letterSpacing: '0.05em',
                backgroundColor:
                  user.role === 'ADMIN'
                    ? 'rgba(168, 85, 247, 0.2)'
                    : user.role === 'ENGINEER'
                    ? 'rgba(59, 130, 246, 0.2)'
                    : 'rgba(100, 116, 139, 0.2)',
                color:
                  user.role === 'ADMIN'
                    ? '#c084fc'
                    : user.role === 'ENGINEER'
                    ? '#93c5fd'
                    : '#cbd5e1',
                border: `1px solid ${
                  user.role === 'ADMIN'
                    ? 'rgba(168, 85, 247, 0.4)'
                    : user.role === 'ENGINEER'
                    ? 'rgba(59, 130, 246, 0.4)'
                    : 'rgba(100, 116, 139, 0.4)'
                }`,
              }}
            >
              {user.role}
            </div>

            {/* Logout button */}
            <button
              onClick={() => logout()}
              title="Sign Out"
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                padding: '6px',
                background: 'transparent',
                border: '1px solid var(--border-default)',
                borderRadius: 'var(--radius-sm)',
                color: 'var(--text-muted)',
                cursor: 'pointer',
                transition: 'all 0.15s ease',
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.color = '#fca5a5';
                e.currentTarget.style.borderColor = 'var(--status-critical-border)';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.color = 'var(--text-muted)';
                e.currentTarget.style.borderColor = 'var(--border-default)';
              }}
            >
              <LogOut size={16} />
            </button>
          </div>
        )}
      </div>
    </header>
  );
};
