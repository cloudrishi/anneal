'use client'

import {useEffect, useRef, useState} from 'react'
import Header from './components/Header'
import ScanPanel from './components/ScanPanel'
import RiskScore from './components/RiskScore'
import FindingCard from './components/FindingCard'

type Tab = 'scan' | 'history'

const RISK_BAND_COLOR: Record<string, string> = {
    LOW:      'var(--success)',
    MEDIUM:   'var(--warning)',
    HIGH:     'var(--warning)',
    CRITICAL: 'var(--breaking)',
}

export default function Home() {
    const [activeTab, setActiveTab]                   = useState<Tab>('scan')
    const [scanResult, setScanResult]                 = useState<any>(null)
    const [activeFilter, setActiveFilter]             = useState<string>('ALL')
    const [enrichmentComplete, setEnrichmentComplete] = useState(false)
    const [history, setHistory]                       = useState<any[]>([])
    const [historyLoading, setHistoryLoading]         = useState(false)
    const pollRef                                     = useRef<NodeJS.Timeout | null>(null)

    const severities = ['ALL', 'BREAKING', 'DEPRECATED', 'MODERNIZATION']

    const filteredFindings = scanResult?.findings?.filter((f: any) =>
        activeFilter === 'ALL' || f.severity === activeFilter
    ) ?? []

    // ─── Load history when tab switches ──────────────────────────────────────

    useEffect(() => {
        if (activeTab !== 'history') return
        loadHistory()
    }, [activeTab])

    async function loadHistory() {
        setHistoryLoading(true)
        try {
            const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/api/scans`)
            if (res.ok) setHistory(await res.json())
        } catch (e) {
            console.warn('Failed to load history:', e)
        } finally {
            setHistoryLoading(false)
        }
    }

    async function loadScan(scanId: string) {
        try {
            const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/api/scans/${scanId}`)
            if (!res.ok) return
            const data = await res.json()
            setScanResult(data)
            setActiveFilter('ALL')
            setActiveTab('scan')
        } catch (e) {
            console.warn('Failed to load scan:', e)
        }
    }

    // ─── Enrichment polling ───────────────────────────────────────────────────

    useEffect(() => {
        setEnrichmentComplete(false)
        if (pollRef.current) clearInterval(pollRef.current)
    }, [scanResult?.scanId])

    useEffect(() => {
        if (!scanResult || enrichmentComplete) return

        const allEnriched = scanResult.findings.every(
            (f: any) => f.llmExplanation !== null
        )
        if (allEnriched) {
            setEnrichmentComplete(true)
            return
        }

        pollRef.current = setInterval(async () => {
            try {
                const res = await fetch(
                    `${process.env.NEXT_PUBLIC_API_URL}/api/scans/${scanResult.scanId}`
                )
                if (!res.ok) return
                const updated = await res.json()

                setScanResult((prev: any) => ({
                    ...prev,
                    findings: prev.findings.map((f: any) => {
                        const u = updated.findings.find((u: any) => u.findingId === f.findingId)
                        if (!u) return f
                        return {
                            ...f,
                            llmExplanation: u.llmExplanation,
                            llmProvider:    u.llmProvider,
                            llmModel:       u.llmModel,
                        }
                    }),
                }))

                const done = updated.findings.every((f: any) => f.llmExplanation !== null)
                if (done) {
                    setEnrichmentComplete(true)
                    if (pollRef.current) clearInterval(pollRef.current)
                }
            } catch (e) {
                console.warn('Enrichment poll failed:', e)
            }
        }, Number(process.env.NEXT_PUBLIC_POLL_INTERVAL_MS) || 3000)

        return () => {
            if (pollRef.current) clearInterval(pollRef.current)
        }
    }, [scanResult?.scanId, enrichmentComplete])

    // ─── Render ───────────────────────────────────────────────────────────────

    return (
        <div style={{minHeight: '100vh', background: 'var(--bg)'}}>
            <Header/>

            {/* ── Tab bar ── */}
            <div style={{
                display:      'flex',
                gap:          '0',
                borderBottom: '1px solid var(--border)',
                padding:      '0 24px',
            }}>
                {(['scan', 'history'] as Tab[]).map(tab => (
                    <button
                        key={tab}
                        onClick={() => setActiveTab(tab)}
                        style={{
                            padding:       '10px 16px',
                            background:    'none',
                            border:        'none',
                            borderBottom:  activeTab === tab
                                ? '2px solid var(--accent)'
                                : '2px solid transparent',
                            color:         activeTab === tab ? 'var(--accent)' : 'var(--muted)',
                            fontFamily:    'var(--font-mono)',
                            fontSize:      '11px',
                            letterSpacing: '0.08em',
                            textTransform: 'uppercase',
                            cursor:        'pointer',
                            marginBottom:  '-1px',
                        }}
                    >
                        {tab}
                    </button>
                ))}
            </div>

            {/* ── Scan tab ── */}
            {activeTab === 'scan' && (
                <>
                    <ScanPanel onScanComplete={(result) => {
                        setScanResult(result)
                        setActiveFilter('ALL')
                    }}/>

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
                                padding:      '12px 24px',
                                borderBottom: '1px solid var(--border)',
                                display:      'flex',
                                alignItems:   'center',
                                gap:          '16px',
                            }}>
                                <span style={{color: 'var(--muted)', fontSize: '11px'}}>
                                    {filteredFindings.length} findings
                                </span>

                                {!enrichmentComplete && (
                                    <span style={{
                                        fontSize:      '10px',
                                        fontFamily:    'var(--font-mono)',
                                        color:         'var(--accent)',
                                        opacity:       0.6,
                                        letterSpacing: '0.05em',
                                    }}>
                                        ◌ enriching…
                                    </span>
                                )}

                                <div style={{display: 'flex', gap: '8px', marginLeft: 'auto'}}>
                                    {severities.map(s => (
                                        <button
                                            key={s}
                                            onClick={() => setActiveFilter(s)}
                                            style={{
                                                padding:    '2px 10px',
                                                fontSize:   '11px',
                                                border:     activeFilter === s
                                                    ? '1px solid var(--accent)'
                                                    : '1px solid var(--border)',
                                                color:      activeFilter === s ? 'var(--accent)' : 'var(--muted)',
                                                background: 'transparent',
                                                transition: 'all 0.1s',
                                                cursor:     'pointer',
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
                                    padding:   '48px 24px',
                                    textAlign: 'center',
                                    color:     'var(--muted)',
                                    fontSize:  '12px',
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
                            display:        'flex',
                            flexDirection:  'column',
                            alignItems:     'center',
                            justifyContent: 'center',
                            height:         'calc(100vh - 140px)',
                            color:          'var(--muted)',
                            fontSize:       '12px',
                            gap:            '8px',
                        }}>
                            <span style={{color: 'var(--accent)', fontSize: '24px', fontWeight: 700}}>
                                anneal
                            </span>
                            <span>► enter a java repository path to begin</span>
                        </div>
                    )}
                </>
            )}

            {/* ── History tab ── */}
            {activeTab === 'history' && (
                <div style={{padding: '24px'}}>

                    {/* Header row */}
                    <div style={{
                        display:       'flex',
                        alignItems:    'center',
                        justifyContent:'space-between',
                        marginBottom:  '16px',
                    }}>
                        <span style={{
                            fontFamily:    'var(--font-mono)',
                            fontSize:      '11px',
                            color:         'var(--muted)',
                            letterSpacing: '0.05em',
                        }}>
                            {history.length} past scans
                        </span>
                        <button
                            onClick={loadHistory}
                            style={{
                                padding:       '4px 12px',
                                background:    'none',
                                border:        '1px solid var(--border)',
                                color:         'var(--muted)',
                                fontFamily:    'var(--font-mono)',
                                fontSize:      '10px',
                                letterSpacing: '0.08em',
                                cursor:        'pointer',
                            }}
                        >
                            ↺ refresh
                        </button>
                    </div>

                    {/* Loading */}
                    {historyLoading && (
                        <div style={{
                            padding:   '48px',
                            textAlign: 'center',
                            color:     'var(--muted)',
                            fontSize:  '12px',
                            fontFamily:'var(--font-mono)',
                        }}>
                            ◌ loading…
                        </div>
                    )}

                    {/* Empty state */}
                    {!historyLoading && history.length === 0 && (
                        <div style={{
                            padding:   '48px',
                            textAlign: 'center',
                            color:     'var(--muted)',
                            fontSize:  '12px',
                        }}>
                            ► no past scans
                        </div>
                    )}

                    {/* Scan list */}
                    {!historyLoading && history.map((scan: any) => (
                        <button
                            key={scan.scanId}
                            onClick={() => loadScan(scan.scanId)}
                            style={{
                                width:        '100%',
                                display:      'flex',
                                alignItems:   'center',
                                gap:          '16px',
                                padding:      '12px 14px',
                                marginBottom: '6px',
                                background:   'var(--surface)',
                                border:       '1px solid var(--border)',
                                borderLeft:   `3px solid ${RISK_BAND_COLOR[scan.riskBand] ?? 'var(--border)'}`,
                                cursor:       'pointer',
                                textAlign:    'left',
                                transition:   'border-color 0.1s ease',
                            }}
                            onMouseEnter={e => {
                                (e.currentTarget as HTMLElement).style.borderColor = 'var(--accent)'
                            }}
                            onMouseLeave={e => {
                                (e.currentTarget as HTMLElement).style.borderColor = 'var(--border)'
                            }}
                        >
                            {/* Risk score */}
                            <span style={{
                                fontSize:   '18px',
                                fontWeight: 700,
                                fontFamily: 'var(--font-mono)',
                                color:      RISK_BAND_COLOR[scan.riskBand] ?? 'var(--foreground)',
                                flexShrink: 0,
                                width:      '36px',
                            }}>
                                {scan.riskScore}
                            </span>

                            {/* Repo path */}
                            <span style={{
                                fontSize:     '12px',
                                fontFamily:   'var(--font-mono)',
                                color:        'var(--foreground)',
                                flexGrow:     1,
                                overflow:     'hidden',
                                textOverflow: 'ellipsis',
                                whiteSpace:   'nowrap',
                            }}>
                                {shortPath(scan.repoPath)}
                            </span>

                            {/* Version range */}
                            <span style={{
                                fontSize:   '10px',
                                fontFamily: 'var(--font-mono)',
                                color:      'var(--muted)',
                                flexShrink: 0,
                            }}>
                                {scan.detectedVersion} → {scan.targetVersion}
                            </span>

                            {/* Finding count */}
                            <span style={{
                                fontSize:   '10px',
                                fontFamily: 'var(--font-mono)',
                                color:      'var(--muted)',
                                flexShrink: 0,
                                width:      '72px',
                                textAlign:  'right',
                            }}>
                                {scan.findingCount} findings
                            </span>

                            {/* Date */}
                            <span style={{
                                fontSize:   '10px',
                                fontFamily: 'var(--font-mono)',
                                color:      'var(--muted)',
                                flexShrink: 0,
                                width:      '80px',
                                textAlign:  'right',
                            }}>
                                {formatDate(scan.scannedAt)}
                            </span>

                            {/* Load arrow */}
                            <span style={{
                                fontSize:   '10px',
                                color:      'var(--muted)',
                                flexShrink: 0,
                            }}>
                                →
                            </span>
                        </button>
                    ))}
                </div>
            )}
        </div>
    )
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

function shortPath(path: string): string {
    const parts = path.replace(/\\/g, '/').split('/')
    return parts.length > 3 ? '…/' + parts.slice(-3).join('/') : path
}

function formatDate(iso: string): string {
    const d = new Date(iso)
    return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}
