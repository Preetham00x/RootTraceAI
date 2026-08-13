import React from 'react';
import { NavLink } from 'react-router-dom';
import {
  Activity,
  Server,
  AlertTriangle,
  Target,
  BrainCircuit,
  FileText,
  Terminal,
  Layers,
  Settings,
  Shield,
} from 'lucide-react';

export const Sidebar: React.FC = () => {
  const navItems = [
    { to: '/', label: 'Command Center', icon: <Activity size={18} /> },
    { to: '/services', label: 'Services', icon: <Server size={18} /> },
    { to: '/incidents', label: 'Incidents', icon: <AlertTriangle size={18} /> },
    { to: '/slos', label: 'SLOs & Budgets', icon: <Target size={18} /> },
    { to: '/intelligence', label: 'Intelligence', icon: <BrainCircuit size={18} /> },
    { to: '/postmortems', label: 'Postmortems', icon: <FileText size={18} /> },
    { to: '/runbooks', label: 'Runbooks', icon: <Terminal size={18} /> },
    { to: '/integrations', label: 'Integrations', icon: <Layers size={18} /> },
    { to: '/settings', label: 'Settings', icon: <Settings size={18} /> },
  ];

  return (
    <aside className="sidebar">
      {/* Brand Header */}
      <div
        style={{
          height: '56px',
          padding: '0 16px',
          display: 'flex',
          alignItems: 'center',
          gap: '10px',
          borderBottom: '1px solid var(--border-subtle)',
        }}
      >
        <div
          style={{
            width: '32px',
            height: '32px',
            borderRadius: 'var(--radius-sm)',
            background: 'linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#fff',
            boxShadow: '0 0 10px rgba(59, 130, 246, 0.4)',
          }}
        >
          <Shield size={18} />
        </div>
        <div className="sidebar-logo-text">
          <div style={{ fontSize: '14px', fontWeight: 700, color: '#fff', letterSpacing: '-0.02em' }}>
            RootTrace<span style={{ color: 'var(--accent-primary)' }}>AI</span>
          </div>
          <div style={{ fontSize: '10px', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.08em' }}>
            SRE Command Console
          </div>
        </div>
      </div>

      {/* Navigation list */}
      <nav style={{ padding: '12px 8px', flex: 1, overflowY: 'auto' }}>
        <div style={{ fontSize: '10px', fontWeight: 600, color: 'var(--text-dim)', textTransform: 'uppercase', letterSpacing: '0.08em', padding: '6px 12px', marginBottom: '4px' }}>
          Operations
        </div>
        {navItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/'}
            style={({ isActive }) => ({
              display: 'flex',
              alignItems: 'center',
              gap: '12px',
              padding: '8px 12px',
              borderRadius: 'var(--radius-sm)',
              fontSize: '13px',
              fontWeight: isActive ? 600 : 500,
              color: isActive ? '#fff' : 'var(--text-secondary)',
              backgroundColor: isActive ? 'rgba(59, 130, 246, 0.15)' : 'transparent',
              borderLeft: isActive ? '3px solid var(--accent-primary)' : '3px solid transparent',
              textDecoration: 'none',
              marginBottom: '2px',
              transition: 'all 0.12s ease',
            })}
          >
            {item.icon}
            <span className="nav-label">{item.label}</span>
          </NavLink>
        ))}
      </nav>

      {/* System Status footer */}
      <div
        style={{
          padding: '12px 16px',
          borderTop: '1px solid var(--border-subtle)',
          backgroundColor: 'rgba(11, 17, 30, 0.5)',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <div
            style={{
              width: '8px',
              height: '8px',
              borderRadius: '50%',
              backgroundColor: 'var(--status-healthy)',
              boxShadow: '0 0 8px rgba(16, 185, 129, 0.6)',
            }}
          />
          <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
            System <span style={{ color: 'var(--text-secondary)' }}>ONLINE</span>
          </div>
        </div>
      </div>
    </aside>
  );
};
