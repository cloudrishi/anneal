'use client'

import { useState } from 'react'

interface Finding {
  findingId: string
  ruleId: string
  ruleName: string
  category: string
  severity: string
  effort: string
  filePath: string
  lineNumber: number
  originalCode: string
  description: string
  confidence: number
  affectsVersion: string
  fixType: string | null
  suggestedCode: string | null
  autoApplicable: boolean
  status: string
}

interface FindingCardProps {
  finding: Finding
}

const severityColor = (s: string) => {
  switch (s.toUpperCase()) {
    case 'BREAKING':      return 'var(--breaking)'
    case 'DEPRECATED':    return 'var(--warning)'
    case 'MODERNIZATION': return 'var(--modernization)'
    default:              return 'var(--muted)'
  }
}

const effortLabel = (e: string) => e.toLowerCase()

export default function FindingCard({ finding }: FindingCardProps) {
  const [expanded, setExpanded] = useState(false)
  const [status, setStatus] = useState(finding.status)

  const fileName = finding.filePath.split('/').pop()

  return (
    <div style={{
      borderBottom: '1px solid var(--border)',
      padding: '12px 24px',
      transition: 'background 0.1s',
    }}
    onMouseEnter={(e) => (e.currentTarget.style.background = 'var(--surface)')}
    onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
    >
      {/* Header row */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: '80px 1fr auto auto auto',
        gap: '12px',
        alignItems: 'center',
        cursor: 'pointer',
      }}
      onClick={() => setExpanded(!expanded)}
      >
        {/* Severity badge */}
        <span style={{
          fontSize: '10px',
          color: severityColor(finding.severity),
          border: `1px solid ${severityColor(finding.severity)}`,
          padding: '1px 6px',
          textAlign: 'center',
        }}>
          {finding.severity.toLowerCase()}
        </span>

        {/* Rule + file */}
        <div>
          <span style={{ color: 'var(--foreground)', fontWeight: 500 }}>
            {finding.ruleId}
          </span>
          <span style={{ color: 'var(--muted)', marginLeft: '12px', fontSize: '11px' }}>
            {fileName}:{finding.lineNumber}
          </span>
        </div>

        {/* Confidence */}
        <span style={{ color: 'var(--muted)', fontSize: '11px' }}>
          {Math.round(finding.confidence * 100)}%
        </span>

        {/* Effort */}
        <span style={{ color: 'var(--muted)', fontSize: '11px' }}>
          {effortLabel(finding.effort)}
        </span>

        {/* Expand toggle */}
        <span style={{ color: 'var(--muted)', fontSize: '11px' }}>
          {expanded ? '▲' : '▼'}
        </span>
      </div>

      {/* Expanded detail */}
      {expanded && (
        <div style={{ marginTop: '12px', paddingLeft: '0' }}>

          {/* Description */}
          <p style={{
            color: 'var(--muted)',
            fontSize: '12px',
            marginBottom: '12px',
          }}>
            {finding.description}
          </p>

          {/* Original code */}
          <div style={{ marginBottom: '12px' }}>
            <div style={{ fontSize: '11px', color: 'var(--muted)', marginBottom: '4px' }}>
              detected
            </div>
            <pre style={{
              background: 'var(--surface)',
              border: '1px solid var(--border)',
              borderLeft: `3px solid ${severityColor(finding.severity)}`,
              padding: '10px 12px',
              fontSize: '12px',
              color: 'var(--foreground)',
            }}>
              {finding.originalCode}
            </pre>
          </div>

          {/* Suggested fix */}
          {finding.suggestedCode && (
            <div style={{ marginBottom: '12px' }}>
              <div style={{
                fontSize: '11px',
                color: 'var(--muted)',
                marginBottom: '4px',
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
              }}>
                suggested fix
                {finding.autoApplicable && (
                  <span style={{
                    color: 'var(--modernization)',
                    border: '1px solid var(--modernization)',
                    padding: '0px 4px',
                    fontSize: '10px',
                  }}>
                    auto-applicable
                  </span>
                )}
              </div>
              <pre style={{
                background: 'var(--surface)',
                border: '1px solid var(--border)',
                borderLeft: '3px solid var(--modernization)',
                padding: '10px 12px',
                fontSize: '12px',
                color: 'var(--foreground)',
              }}>
                {finding.suggestedCode}
              </pre>
            </div>
          )}

          {/* Actions */}
          <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
            {['ACCEPTED', 'REJECTED', 'DEFERRED'].map(action => (
              <button
                key={action}
                onClick={() => setStatus(action)}
                style={{
                  padding: '4px 12px',
                  fontSize: '11px',
                  border: status === action
                    ? `1px solid var(--accent)`
                    : '1px solid var(--border)',
                  color: status === action ? 'var(--accent)' : 'var(--muted)',
                  background: 'transparent',
                  transition: 'all 0.1s',
                }}
              >
                {action.toLowerCase()}
              </button>
            ))}
            <span style={{
              marginLeft: 'auto',
              fontSize: '11px',
              color: 'var(--muted)',
            }}>
              affects: {finding.affectsVersion}
            </span>
          </div>
        </div>
      )}
    </div>
  )
}
