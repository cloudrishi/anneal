'use client'

import { useState } from 'react'

interface ScanPanelProps {
  onScanComplete: (result: any) => void
}

const JAVA_VERSIONS = ['auto-detect', '8', '11', '17', '21']

export default function ScanPanel({ onScanComplete }: ScanPanelProps) {
  const [repoPath, setRepoPath] = useState('')
  const [sourceVersion, setSourceVersion] = useState('auto-detect')
  const [status, setStatus] = useState<'idle' | 'scanning' | 'done' | 'error'>('idle')
  const [message, setMessage] = useState('')

  const handleScan = async () => {
    if (!repoPath.trim()) return
    setStatus('scanning')
    setMessage('')

    try {
      const body: any = { repoPath: repoPath.trim() }
      if (sourceVersion !== 'auto-detect') {
        body.sourceVersion = sourceVersion
      }

      const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/api/scan`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      })

      if (!res.ok) {
        const err = await res.json()
        setStatus('error')
        setMessage(err.error || 'Scan failed')
        return
      }

      const data = await res.json()
      setStatus('done')
      setMessage(`✓ ${data.filesScanned} files scanned — ${data.findings.length} findings`)
      onScanComplete(data)
    } catch (err) {
      setStatus('error')
      setMessage('Failed to connect to anneal backend')
    }
  }

  return (
    <div style={{
      borderBottom: '1px solid var(--border)',
      padding: '16px 24px',
    }}>
      <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
        <input
          type="text"
          value={repoPath}
          onChange={(e) => setRepoPath(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && handleScan()}
          placeholder="/path/to/your/java/project"
          style={{
            flex: 1,
            padding: '8px 12px',
            background: 'var(--surface)',
            border: '1px solid var(--border)',
            color: 'var(--foreground)',
          }}
        />

        <select
          value={sourceVersion}
          onChange={(e) => setSourceVersion(e.target.value)}
          style={{
            padding: '8px 12px',
            background: 'var(--surface)',
            border: '1px solid var(--border)',
            color: sourceVersion === 'auto-detect' ? 'var(--muted)' : 'var(--foreground)',
            width: '140px',
          }}
        >
          {JAVA_VERSIONS.map(v => (
            <option key={v} value={v}>{v === 'auto-detect' ? 'auto-detect' : `java ${v}`}</option>
          ))}
        </select>

        <button
          onClick={handleScan}
          disabled={status === 'scanning' || !repoPath.trim()}
          style={{
            padding: '8px 20px',
            border: '1px solid var(--accent)',
            color: status === 'scanning' ? 'var(--muted)' : 'var(--accent)',
            background: 'transparent',
            transition: 'all 0.15s',
          }}
          onMouseEnter={(e) => {
            if (status !== 'scanning') {
              (e.target as HTMLElement).style.background = 'var(--accent)'
              ;(e.target as HTMLElement).style.color = '#0a0a0a'
            }
          }}
          onMouseLeave={(e) => {
            (e.target as HTMLElement).style.background = 'transparent'
            ;(e.target as HTMLElement).style.color = status === 'scanning' ? 'var(--muted)' : 'var(--accent)'
          }}
        >
          {status === 'scanning' ? 'scanning...' : 'scan →'}
        </button>
      </div>

      {message && (
        <p style={{
          marginTop: '8px',
          fontSize: '12px',
          color: status === 'error' ? 'var(--danger)' : 'var(--success)',
        }}>
          {message}
        </p>
      )}
    </div>
  )
}
