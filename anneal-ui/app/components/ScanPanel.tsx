'use client'

import {useState} from 'react'

interface ScanPanelProps {
    onScanComplete: (result: any) => void
}

const JAVA_VERSIONS = ['auto-detect', '8', '11', '17', '21']

export default function ScanPanel({onScanComplete}: ScanPanelProps) {
    const [repoPath, setRepoPath] = useState('')
    const [sourceVersion, setSourceVersion] = useState('auto-detect')
    const [status, setStatus] = useState<'idle' | 'scanning' | 'done' | 'error'>('idle')
    const [message, setMessage] = useState('')
    const [progress, setProgress] = useState<{
        filesScanned: number;
        totalFiles: number;
        file: string;
    } | null>(null)

    const handleScan = async () => {
        if (!repoPath.trim()) return

        setStatus('scanning')
        setMessage('')
        setProgress(null)

        // Build SSE URL with query params
        const params = new URLSearchParams({repoPath: repoPath.trim()})
        if (sourceVersion !== 'auto-detect') {
            params.set('sourceVersion', sourceVersion)
        }

        const url = `${process.env.NEXT_PUBLIC_API_URL}/api/scan/stream?${params}`
        const es = new EventSource(url)

        es.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data)

                if (data.type === 'FILE') {
                    // Update progress counter
                    setProgress({
                        filesScanned: data.filesScanned,
                        totalFiles: data.totalFiles,
                        file: data.file,
                    })
                    return
                }

                if (data.type === 'DONE') {
                    // Scan complete — fetch the full result for findings
                    es.close()
                    setProgress(null)
                    setStatus('done')
                    setMessage(`✓ ${data.filesScanned} files scanned — ${data.findingCount} findings`)

                    // Fetch full scan result — findings need the complete DTO
                    fetch(`${process.env.NEXT_PUBLIC_API_URL}/api/scans/${data.scanId}`)
                        .then(r => r.json())
                        .then(onScanComplete)
                        .catch(err => console.warn('Failed to fetch full scan result:', err))
                    return
                }

                if (data.type === 'ERROR') {
                    es.close()
                    setStatus('error')
                    setProgress(null)
                    setMessage(data.message || 'Scan failed')
                }

            } catch (e) {
                console.warn('Failed to parse SSE event:', event.data, e)
            }
        }

        es.onerror = () => {
            es.close()
            setStatus('error')
            setMessage('Failed to connect to anneal backend')
        }
    }

    // ─── Progress label ───────────────────────────────────────────────────────

    const progressLabel = (() => {
        if (status !== 'scanning') return null
        if (!progress) return 'scanning…'
        const pct = progress.totalFiles > 0
            ? Math.round((progress.filesScanned / progress.totalFiles) * 100)
            : 0
        return `scanning… ${progress.filesScanned}/${progress.totalFiles} (${pct}%) — ${progress.file}`
    })()

    // ─── Render ───────────────────────────────────────────────────────────────

    return (
        <div style={{
            borderBottom: '1px solid var(--border)',
            padding: '16px 24px',
        }}>
            <div style={{display: 'flex', gap: '8px', alignItems: 'center'}}>
                <input
                    type="text"
                    value={repoPath}
                    onChange={(e) => setRepoPath(e.target.value)}
                    onKeyDown={(e) => e.key === 'Enter' && handleScan()}
                    placeholder="/path/to/your/java/project"
                    disabled={status === 'scanning'}
                    style={{
                        flex: 1,
                        padding: '8px 12px',
                        background: 'var(--surface)',
                        border: '1px solid var(--border)',
                        color: 'var(--foreground)',
                        opacity: status === 'scanning' ? 0.6 : 1,
                    }}
                />

                <select
                    value={sourceVersion}
                    onChange={(e) => setSourceVersion(e.target.value)}
                    disabled={status === 'scanning'}
                    style={{
                        padding: '8px 12px',
                        background: 'var(--surface)',
                        border: '1px solid var(--border)',
                        color: sourceVersion === 'auto-detect' ? 'var(--muted)' : 'var(--foreground)',
                        width: '140px',
                        opacity: status === 'scanning' ? 0.6 : 1,
                    }}
                >
                    {JAVA_VERSIONS.map(v => (
                        <option key={v} value={v}>
                            {v === 'auto-detect' ? 'auto-detect' : `java ${v}`}
                        </option>
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
                        cursor: status === 'scanning' ? 'not-allowed' : 'pointer',
                    }}
                    onMouseEnter={(e) => {
                        if (status !== 'scanning') {
                            (e.target as HTMLElement).style.background = 'var(--accent)'
                            ;(e.target as HTMLElement).style.color = '#0a0a0a'
                        }
                    }}
                    onMouseLeave={(e) => {
                        (e.target as HTMLElement).style.background = 'transparent'
                        ;(e.target as HTMLElement).style.color =
                            status === 'scanning' ? 'var(--muted)' : 'var(--accent)'
                    }}
                >
                    {status === 'scanning' ? 'scanning...' : 'scan →'}
                </button>
            </div>

            {/* Progress bar */}
            {status === 'scanning' && progress && progress.totalFiles > 0 && (
                <div style={{marginTop: '8px'}}>
                    <div style={{
                        height: '2px',
                        background: 'var(--border)',
                        marginBottom: '4px',
                    }}>
                        <div style={{
                            height: '2px',
                            background: 'var(--accent)',
                            width: `${Math.round((progress.filesScanned / progress.totalFiles) * 100)}%`,
                            transition: 'width 0.1s ease',
                        }}/>
                    </div>
                </div>
            )}

            {/* Status message */}
            {(progressLabel || message) && (
                <p style={{
                    marginTop: '6px',
                    fontSize: '11px',
                    fontFamily: 'var(--font-mono)',
                    color: status === 'error'
                        ? 'var(--breaking)'
                        : status === 'scanning'
                            ? 'var(--muted)'
                            : 'var(--success)',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                }}>
                    {status === 'scanning' ? progressLabel : message}
                </p>
            )}
        </div>
    )
}
