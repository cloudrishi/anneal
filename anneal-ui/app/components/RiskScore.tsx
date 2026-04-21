'use client'

interface BoundaryScore {
  from: string
  to: string
  score: number
  band: string
  findingCount: number
}

interface RiskScoreProps {
  score: number
  band: string
  detectedVersion: string
  targetVersion: string
  filesScanned: number
  filesWithFindings: number
  boundaryScores: BoundaryScore[]
}

const bandColor = (band: string) => {
  switch (band.toUpperCase()) {
    case 'CRITICAL': return 'var(--breaking)'
    case 'HIGH':     return '#f97316'
    case 'MEDIUM':   return 'var(--warning)'
    case 'LOW':      return 'var(--modernization)'
    default:         return 'var(--muted)'
  }
}

export default function RiskScore({
  score, band, detectedVersion, targetVersion,
  filesScanned, filesWithFindings, boundaryScores
}: RiskScoreProps) {
  return (
    <div style={{
      borderBottom: '1px solid var(--border)',
      padding: '16px 24px',
      display: 'grid',
      gridTemplateColumns: 'auto 1fr',
      gap: '24px',
      alignItems: 'start',
    }}>
      {/* Risk score block */}
      <div>
        <div style={{ fontSize: '11px', color: 'var(--muted)', marginBottom: '4px' }}>
          risk score
        </div>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: '8px' }}>
          <span style={{
            fontSize: '48px',
            fontWeight: 700,
            color: bandColor(band),
            lineHeight: 1,
          }}>
            {score}
          </span>
          <span style={{
            fontSize: '13px',
            color: bandColor(band),
            fontWeight: 500,
          }}>
            / {band.toLowerCase()}
          </span>
        </div>
        <div style={{ marginTop: '8px', fontSize: '11px', color: 'var(--muted)' }}>
          {detectedVersion} → {targetVersion}
          <span style={{ margin: '0 8px', color: 'var(--border)' }}>·</span>
          {filesScanned} files
          <span style={{ margin: '0 8px', color: 'var(--border)' }}>·</span>
          {filesWithFindings} with findings
        </div>
      </div>

      {/* Per-boundary breakdown */}
      <div>
        <div style={{ fontSize: '11px', color: 'var(--muted)', marginBottom: '8px' }}>
          boundary breakdown
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
          {boundaryScores.map((bs) => (
            <div key={`${bs.from}-${bs.to}`} style={{
              display: 'grid',
              gridTemplateColumns: '160px 40px auto 1fr auto',
              gap: '8px',
              alignItems: 'center',
              fontSize: '12px',
            }}>
              <span style={{ color: 'var(--muted)' }}>
                {bs.from} → {bs.to}
              </span>
              <span style={{ color: bandColor(bs.band), fontWeight: 500 }}>
                {bs.score}
              </span>
              <span style={{ color: bandColor(bs.band), fontSize: '11px' }}>
                {bs.band.toLowerCase()}
              </span>
              <div style={{
                height: '2px',
                background: 'var(--border)',
                borderRadius: '1px',
                overflow: 'hidden',
              }}>
                <div style={{
                  height: '100%',
                  width: `${Math.min(100, bs.score)}%`,
                  background: bandColor(bs.band),
                  transition: 'width 0.4s ease',
                }} />
              </div>
              <span style={{ color: 'var(--muted)', fontSize: '11px' }}>
                {bs.findingCount} findings
              </span>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
