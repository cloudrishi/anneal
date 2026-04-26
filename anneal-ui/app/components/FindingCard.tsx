'use client';

import {useState} from 'react';

// ─── Types ────────────────────────────────────────────────────────────────────

type Severity = 'BREAKING' | 'DEPRECATED' | 'MODERNIZATION';
type Effort = 'TRIVIAL' | 'LOW' | 'MEDIUM' | 'HIGH' | 'MANUAL';
type FixType = 'IMPORT_REPLACE' | 'API_REPLACE' | 'REFACTOR' | 'ADD_DEPENDENCY' | 'MODULE_INFO' | 'MANUAL';
type FindingStatus = 'OPEN' | 'ACCEPTED' | 'REJECTED' | 'DEFERRED';

interface FindingDto {
    findingId: string;
    ruleId: string;
    severity: Severity;
    filePath: string;
    lineNumber: number;
    originalCode: string;
    suggestedCode: string;
    fixType: FixType;
    autoApplicable: boolean;
    confidence: number;
    referenceUrl: string | null;
    llmExplanation: string | null;
    llmProvider: 'OLLAMA' | 'ANTHROPIC' | null;
    llmModel: string | null;
    status: FindingStatus;
}

interface FindingCardProps {
    scanId: string;                                    // needed for PATCH path
    finding: FindingDto;
    onAccept: (findingId: string) => void;
    onReject: (findingId: string) => void;
    onDefer:  (findingId: string) => void;
}

// ─── Design tokens ────────────────────────────────────────────────────────────

const SEVERITY_COLOR: Record<Severity, string> = {
    BREAKING:      'var(--breaking)',
    DEPRECATED:    'var(--warning)',
    MODERNIZATION: 'var(--success)',
};

const EFFORT_LABEL: Record<Effort, string> = {
    TRIVIAL: 'trivial',
    LOW:     'low',
    MEDIUM:  'medium',
    HIGH:    'high',
    MANUAL:  'manual',
};

const STATUS_STYLES: Record<FindingStatus, { border: string; label: string }> = {
    OPEN:     { border: 'var(--border)',   label: ''           },
    ACCEPTED: { border: 'var(--success)',  label: '✓ accepted' },
    REJECTED: { border: 'var(--breaking)', label: '✗ rejected' },
    DEFERRED: { border: 'var(--warning)',  label: '~ deferred' },
};

// ─── Component ────────────────────────────────────────────────────────────────

export default function FindingCard({ scanId, finding, onAccept, onReject, onDefer }: FindingCardProps) {
    const [expanded,  setExpanded ] = useState(false);
    const [status,    setStatus   ] = useState<FindingStatus>(finding.status);
    const [loading,   setLoading  ] = useState<FindingStatus | null>(null); // which button is in flight
    const [error,     setError    ] = useState<string | null>(null);

    const severityColor = SEVERITY_COLOR[finding.severity];
    const statusStyle   = STATUS_STYLES[status];
    const isActioned    = status !== 'OPEN';

    async function updateStatus(newStatus: FindingStatus) {
        setLoading(newStatus);
        setError(null);

        try {
            const res = await fetch(
                `http://localhost:8080/api/scans/${scanId}/findings/${finding.findingId}`,
                {
                    method:  'PATCH',
                    headers: { 'Content-Type': 'application/json' },
                    body:    JSON.stringify({ status: newStatus }),
                }
            );

            if (!res.ok) {
                const body = await res.text();
                throw new Error(body || `HTTP ${res.status}`);
            }

            // Confirmed — update local state and notify parent
            setStatus(newStatus);
            if (newStatus === 'ACCEPTED') onAccept(finding.findingId);
            if (newStatus === 'REJECTED') onReject(finding.findingId);
            if (newStatus === 'DEFERRED') onDefer(finding.findingId);

        } catch (e: unknown) {
            const message = e instanceof Error ? e.message : 'unknown error';
            setError(`Failed to update status: ${message}`);
        } finally {
            setLoading(null);
        }
    }

    return (
        <div style={{
            background:   'var(--surface)',
            border:       `1px solid ${statusStyle.border}`,
            borderLeft:   `3px solid ${severityColor}`,
            marginBottom: '8px',
            opacity:      isActioned ? 0.7 : 1,
            transition:   'opacity 0.15s ease, border-color 0.15s ease',
        }}>

            {/* ── Header ── */}
            <button
                onClick={() => setExpanded(e => !e)}
                style={{
                    width:      '100%',
                    display:    'flex',
                    alignItems: 'center',
                    gap:        '12px',
                    padding:    '10px 14px',
                    background: 'none',
                    border:     'none',
                    cursor:     'pointer',
                    textAlign:  'left',
                    color:      'var(--foreground)',
                    fontFamily: 'var(--font-mono)',
                }}
            >
                {/* Severity badge */}
                <span style={{
                    fontSize:      '9px',
                    fontWeight:    700,
                    letterSpacing: '0.1em',
                    textTransform: 'uppercase',
                    color:         severityColor,
                    border:        `1px solid ${severityColor}`,
                    padding:       '2px 5px',
                    flexShrink:    0,
                }}>
                    {finding.severity}
                </span>

                {/* Rule ID */}
                <span style={{
                    fontSize:     '12px',
                    color:        'var(--foreground)',
                    flexGrow:     1,
                    overflow:     'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace:   'nowrap',
                }}>
                    {finding.ruleId}
                </span>

                {/* File + line */}
                <span style={{
                    fontSize:     '11px',
                    color:        'var(--foreground)',
                    opacity:      0.45,
                    flexShrink:   0,
                    maxWidth:     '220px',
                    overflow:     'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace:   'nowrap',
                }}>
                    {shortPath(finding.filePath)}:{finding.lineNumber}
                </span>

                {/* Status label */}
                {isActioned && (
                    <span style={{
                        fontSize:      '10px',
                        color:         statusStyle.border,
                        letterSpacing: '0.05em',
                        flexShrink:    0,
                    }}>
                        {statusStyle.label}
                    </span>
                )}

                {/* Chevron */}
                <span style={{
                    fontSize:   '10px',
                    color:      'var(--foreground)',
                    opacity:    0.4,
                    flexShrink: 0,
                    transform:  expanded ? 'rotate(90deg)' : 'none',
                    transition: 'transform 0.12s ease',
                }}>
                    ▶
                </span>
            </button>

            {/* ── Expanded body ── */}
            {expanded && (
                <div style={{ padding: '0 14px 14px' }}>

                    {/* Confidence + effort row */}
                    <div style={{
                        display:       'flex',
                        gap:           '16px',
                        marginBottom:  '12px',
                        fontSize:      '10px',
                        color:         'var(--foreground)',
                        opacity:       0.5,
                        fontFamily:    'var(--font-mono)',
                        letterSpacing: '0.05em',
                    }}>
                        <span>confidence {pct(finding.confidence)}</span>
                        {finding.autoApplicable && (
                            <span style={{ color: 'var(--success)', opacity: 1 }}>auto-applicable</span>
                        )}
                    </div>

                    {/* Original code */}
                    <Section label="detected">
                        <CodeBlock code={finding.originalCode} dimmed />
                    </Section>

                    {/* Suggested fix */}
                    <Section label="suggested fix">
                        <CodeBlock code={finding.suggestedCode} />
                    </Section>

                    {/* LLM explanation */}
                    {finding.llmExplanation && (
                        <div style={{
                            marginBottom: '12px',
                            padding:      '10px 12px',
                            background:   'var(--bg)',
                            border:       '1px solid var(--border)',
                            borderLeft:   '3px solid var(--accent)',
                        }}>
                            <div style={{
                                display:        'flex',
                                alignItems:     'center',
                                justifyContent: 'space-between',
                                marginBottom:   '6px',
                            }}>
                                <span style={{
                                    fontSize:      '9px',
                                    fontFamily:    'var(--font-mono)',
                                    color:         'var(--accent)',
                                    textTransform: 'uppercase',
                                    letterSpacing: '0.1em',
                                    fontWeight:    700,
                                }}>
                                    AI explanation
                                </span>
                                {finding.llmModel && (
                                    <span style={{
                                        fontSize:      '9px',
                                        fontFamily:    'var(--font-mono)',
                                        color:         'var(--foreground)',
                                        opacity:       0.35,
                                        letterSpacing: '0.04em',
                                    }}>
                                        via {finding.llmModel}
                                    </span>
                                )}
                            </div>
                            <p style={{
                                margin:     0,
                                fontSize:   '12px',
                                fontFamily: 'var(--font-mono)',
                                color:      'var(--foreground)',
                                lineHeight: '1.65',
                                opacity:    0.85,
                            }}>
                                {finding.llmExplanation}
                            </p>
                        </div>
                    )}

                    {/* Reference link */}
                    {finding.referenceUrl && (
                        <div style={{ marginBottom: '12px' }}>
                            <a
                                href={finding.referenceUrl}
                                target="_blank"
                                rel="noopener noreferrer"
                                style={{
                                    fontSize:       '10px',
                                    fontFamily:     'var(--font-mono)',
                                    color:          'var(--accent)',
                                    opacity:        0.7,
                                    textDecoration: 'none',
                                    letterSpacing:  '0.04em',
                                }}
                            >
                                ↗ reference
                            </a>
                        </div>
                    )}

                    {/* Error message */}
                    {error && (
                        <div style={{
                            marginBottom:  '10px',
                            padding:       '6px 10px',
                            border:        '1px solid var(--breaking)',
                            fontSize:      '10px',
                            fontFamily:    'var(--font-mono)',
                            color:         'var(--breaking)',
                            letterSpacing: '0.03em',
                        }}>
                            {error}
                        </div>
                    )}

                    {/* Accept / Reject / Defer */}
                    {!isActioned && (
                        <div style={{ display: 'flex', gap: '6px' }}>
                            <ActionButton
                                label="accept"
                                color="var(--success)"
                                loading={loading === 'ACCEPTED'}
                                disabled={loading !== null}
                                onClick={() => updateStatus('ACCEPTED')}
                            />
                            <ActionButton
                                label="reject"
                                color="var(--breaking)"
                                loading={loading === 'REJECTED'}
                                disabled={loading !== null}
                                onClick={() => updateStatus('REJECTED')}
                            />
                            <ActionButton
                                label="defer"
                                color="var(--warning)"
                                loading={loading === 'DEFERRED'}
                                disabled={loading !== null}
                                onClick={() => updateStatus('DEFERRED')}
                            />
                        </div>
                    )}

                </div>
            )}
        </div>
    );
}

// ─── Sub-components ───────────────────────────────────────────────────────────

function Section({ label, children }: { label: string; children: React.ReactNode }) {
    return (
        <div style={{ marginBottom: '12px' }}>
            <div style={{
                fontSize:      '9px',
                fontFamily:    'var(--font-mono)',
                color:         'var(--foreground)',
                opacity:       0.4,
                textTransform: 'uppercase',
                letterSpacing: '0.1em',
                marginBottom:  '4px',
            }}>
                {label}
            </div>
            {children}
        </div>
    );
}

function CodeBlock({ code, dimmed = false }: { code: string; dimmed?: boolean }) {
    return (
        <pre style={{
            margin:     0,
            padding:    '8px 10px',
            background: 'var(--bg)',
            border:     '1px solid var(--border)',
            fontSize:   '11px',
            fontFamily: 'var(--font-mono)',
            color:      'var(--foreground)',
            opacity:    dimmed ? 0.55 : 0.9,
            overflowX:  'auto',
            lineHeight: '1.5',
            whiteSpace: 'pre-wrap',
            wordBreak:  'break-word',
        }}>
            {code}
        </pre>
    );
}

function ActionButton({
    label,
    color,
    loading,
    disabled,
    onClick,
}: {
    label:    string;
    color:    string;
    loading:  boolean;
    disabled: boolean;
    onClick:  () => void;
}) {
    return (
        <button
            onClick={onClick}
            disabled={disabled}
            style={{
                padding:       '5px 12px',
                background:    loading ? color : 'none',
                border:        `1px solid ${color}`,
                color:         loading ? 'var(--bg)' : color,
                fontFamily:    'var(--font-mono)',
                fontSize:      '10px',
                letterSpacing: '0.08em',
                textTransform: 'uppercase',
                cursor:        disabled ? 'not-allowed' : 'pointer',
                opacity:       disabled && !loading ? 0.4 : 1,
                transition:    'opacity 0.1s ease',
            }}
            onMouseEnter={e => {
                if (disabled) return;
                (e.target as HTMLButtonElement).style.background = color;
                (e.target as HTMLButtonElement).style.color      = 'var(--bg)';
            }}
            onMouseLeave={e => {
                if (loading) return;
                (e.target as HTMLButtonElement).style.background = 'none';
                (e.target as HTMLButtonElement).style.color      = color;
            }}
        >
            {loading ? '…' : label}
        </button>
    );
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

function shortPath(path: string): string {
    const parts = path.replace(/\\/g, '/').split('/');
    return parts.length > 2 ? '…/' + parts.slice(-2).join('/') : path;
}

function pct(n: number): string {
    return Math.round(n * 100) + '%';
}
