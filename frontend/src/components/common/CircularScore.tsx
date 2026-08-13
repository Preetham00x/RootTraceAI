import React from 'react';
import type { RiskTier } from '../../types';

interface CircularScoreProps {
  score: number;
  size?: number;
  strokeWidth?: number;
  riskTier?: RiskTier;
  label?: string;
}

export const CircularScore: React.FC<CircularScoreProps> = ({
  score,
  size = 130,
  strokeWidth = 10,
  riskTier,
  label = 'Score',
}) => {
  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  const clampedScore = Math.max(0, Math.min(100, score));
  const strokeDashoffset = circumference - (clampedScore / 100) * circumference;

  let strokeColor = '#10b981'; // Healthy green
  if (score < 50 || riskTier === 'CRITICAL') {
    strokeColor = '#ef4444'; // Red
  } else if (score < 70 || riskTier === 'HIGH') {
    strokeColor = '#f97316'; // Orange
  } else if (score < 85 || riskTier === 'MEDIUM') {
    strokeColor = '#eab308'; // Yellow
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
      <div style={{ position: 'relative', width: size, height: size }}>
        <svg width={size} height={size} style={{ transform: 'rotate(-90deg)' }}>
          {/* Background track */}
          <circle
            cx={size / 2}
            cy={size / 2}
            r={radius}
            stroke="var(--border-default)"
            strokeWidth={strokeWidth}
            fill="transparent"
          />
          {/* Progress arc */}
          <circle
            cx={size / 2}
            cy={size / 2}
            r={radius}
            stroke={strokeColor}
            strokeWidth={strokeWidth}
            strokeDasharray={circumference}
            strokeDashoffset={strokeDashoffset}
            strokeLinecap="round"
            fill="transparent"
            style={{ transition: 'stroke-dashoffset 0.8s ease' }}
          />
        </svg>
        <div
          style={{
            position: 'absolute',
            inset: 0,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <span style={{ fontSize: `${size * 0.26}px`, fontWeight: 800, color: 'var(--text-main)' }}>
            {score.toFixed(1)}
          </span>
          <span style={{ fontSize: `${size * 0.09}px`, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
            {label}
          </span>
        </div>
      </div>
    </div>
  );
};
