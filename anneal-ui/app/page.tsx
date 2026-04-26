'use client'

import { useState } from 'react'
import Header from './components/Header'
import ScanPanel from './components/ScanPanel'
import RiskScore from './components/RiskScore'
import FindingCard from './components/FindingCard'

export default function Home() {
  const [scanResult, setScanResult] = useState<any>(null)
  const [activeFilter, setActiveFilter] = useState<string>('ALL')

  const severities = ['ALL', 'BREAKING', 'DEPRECATED', 'MODERNIZATION']

  const filteredFindings = scanResult?.findings?.filter((f: any) =>
    activeFilter === 'ALL' || f.severity === activeFilter
  ) ?? []

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg)' }}>
      <Header />
      <ScanPanel onScanComplete={setScanResult} />

      {scanResult && (
        <>
          <RiskScore
            score={scanResult.riskScore}
            band={scanResult.riskBand}
            detectedVersion={scanResult.detectedVersion}
            targetVersion={scanResult.targetVersion}
            filesScanned={scanResult.filesScanned}
            filesWithFindings={scanResult.filesWithFindings}
            boundaryScores={scanResult.boundaryScores}
          />

          {/* Findings header + filter */}
          <div style={{
            padding: '12px 24px',
            borderBottom: '1px solid var(--border)',
            display: 'flex',
            alignItems: 'center',
            gap: '16px',
          }}>
            <span style={{ color: 'var(--muted)', fontSize: '11px' }}>
              {filteredFindings.length} findings
            </span>
            <div style={{ display: 'flex', gap: '8px' }}>
              {severities.map(s => (
                <button
                  key={s}
                  onClick={() => setActiveFilter(s)}
                  style={{
                    padding: '2px 10px',
                    fontSize: '11px',
                    border: activeFilter === s
                      ? '1px solid var(--accent)'
                      : '1px solid var(--border)',
                    color: activeFilter === s ? 'var(--accent)' : 'var(--muted)',
                    background: 'transparent',
                    transition: 'all 0.1s',
                  }}
                >
                  {s.toLowerCase()}
                </button>
              ))}
            </div>
          </div>

          {/* Findings list */}
          {filteredFindings.length === 0 ? (
            <div style={{
              padding: '48px 24px',
              textAlign: 'center',
              color: 'var(--muted)',
              fontSize: '12px',
            }}>
              ► no findings in this category
            </div>
          ) : (
            filteredFindings.map((finding: any) => (
              <FindingCard
                  key={finding.findingId}
                  scanId={scanResult.scanId}
                  finding={finding}
                  onAccept={(id) => console.log('accepted', id)}
                  onReject={(id) => console.log('rejected', id)}
                  onDefer={(id) => console.log('deferred', id)}
              />
            ))
          )}
        </>
      )}

      {!scanResult && (
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          height: 'calc(100vh - 100px)',
          color: 'var(--muted)',
          fontSize: '12px',
          gap: '8px',
        }}>
          <span style={{ color: 'var(--accent)', fontSize: '24px', fontWeight: 700 }}>
            anneal
          </span>
          <span>► enter a java repository path to begin</span>
        </div>
      )}
    </div>
  )
}
