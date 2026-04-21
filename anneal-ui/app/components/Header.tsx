'use client'

export default function Header() {
  return (
    <header style={{
      borderBottom: '1px solid var(--border)',
      padding: '0 24px',
      height: '48px',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      position: 'sticky',
      top: 0,
      background: 'var(--bg)',
      zIndex: 100,
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
        <span style={{
          color: 'var(--accent)',
          fontWeight: 700,
          fontSize: '15px',
          letterSpacing: '-0.5px',
        }}>
          anneal
        </span>
        <span style={{ color: 'var(--muted)', fontSize: '11px' }}>
          java migration assistant
        </span>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
        <span style={{ color: 'var(--muted)', fontSize: '11px' }}>
          target: <span style={{ color: 'var(--accent)' }}>java 25</span>
        </span>
      </div>
    </header>
  )
}
