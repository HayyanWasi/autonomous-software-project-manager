/**
 * OutputViewer — renders the selected agent's structured output.
 *
 * Database and Planner outputs include Mermaid diagrams (ERD / Gantt), which are
 * rendered client-side only via the `mermaid` package (project decision).
 */
import { useEffect, useId, useRef, useState } from 'react'

// ---------------------------------------------------------------------------
// Mermaid (client-side rendering, themed from the Zeptex design tokens)
//
// Lazy-loaded via dynamic import() so the ~600 kB mermaid bundle is only
// fetched the first time an ERD or Gantt is actually shown — not on app load.
// ---------------------------------------------------------------------------

let mermaidPromise = null
function loadMermaid() {
  if (!mermaidPromise) {
    mermaidPromise = import('mermaid').then(({ default: mermaid }) => {
      mermaid.initialize({
        startOnLoad: false,
        theme: 'dark',
        securityLevel: 'strict',
        themeVariables: {
          fontFamily: 'JetBrains Mono, monospace',
          background: '#0b1326',
          primaryColor: '#171f33',
          primaryBorderColor: '#38bdf8',
          primaryTextColor: '#dae2fd',
          lineColor: '#87929a',
          secondaryColor: '#222a3d',
          tertiaryColor: '#131b2e',
        },
      })
      return mermaid
    })
  }
  return mermaidPromise
}

function MermaidDiagram({ chart }) {
  const containerRef = useRef(null)
  const [error, setError] = useState(null)
  const rawId = useId().replace(/[^a-zA-Z0-9]/g, '')

  useEffect(() => {
    if (!chart) return
    let active = true
    loadMermaid()
      .then((mermaid) => mermaid.render(`mermaid-${rawId}`, chart))
      .then(({ svg }) => {
        if (active && containerRef.current) containerRef.current.innerHTML = svg
      })
      .catch((e) => {
        if (active) setError(e?.message || 'Diagram render failed')
      })
    return () => {
      active = false
    }
  }, [chart, rawId])

  if (!chart) return <Empty label="No diagram produced." />
  if (error) {
    return (
      <div>
        <p className="text-label-sm mb-2 text-error">Diagram failed to render — showing source.</p>
        <CodeBlock value={chart} />
      </div>
    )
  }
  return <div ref={containerRef} className="overflow-auto rounded-lg bg-surface-container-lowest/60 p-4" />
}

// ---------------------------------------------------------------------------
// Shared primitives
// ---------------------------------------------------------------------------

function Section({ title, children }) {
  return (
    <div className="mb-6">
      <h4 className="text-label-md mb-2 text-primary">{title}</h4>
      {children}
    </div>
  )
}

function Empty({ label }) {
  return <p className="text-body-md text-on-surface-variant">{label}</p>
}

function BulletList({ items }) {
  if (!items?.length) return <Empty label="—" />
  return (
    <ul className="space-y-1.5">
      {items.map((item, i) => (
        <li key={i} className="text-body-md flex gap-2 text-on-surface">
          <span className="material-symbols-outlined mt-0.5 text-[16px] text-outline">
            chevron_right
          </span>
          <span>{item}</span>
        </li>
      ))}
    </ul>
  )
}

function Chip({ children, tone = 'primary' }) {
  const tones = {
    primary: 'bg-primary-container/10 text-primary',
    secondary: 'bg-secondary-container/10 text-secondary',
    tertiary: 'bg-tertiary-container/10 text-tertiary',
  }
  return (
    <span className={`text-label-sm rounded-full px-2.5 py-1 ${tones[tone]}`}>{children}</span>
  )
}

function CodeBlock({ value }) {
  return (
    <pre className="text-label-md overflow-auto rounded-lg border border-outline-variant bg-surface-container-lowest/80 p-4 text-on-surface-variant">
      {value}
    </pre>
  )
}

// ---------------------------------------------------------------------------
// Per-agent renderers
// ---------------------------------------------------------------------------

function RequirementOutput({ data }) {
  return (
    <>
      {data.executiveSummary && (
        <Section title="EXECUTIVE SUMMARY">
          <p className="text-body-md text-on-surface">{data.executiveSummary}</p>
        </Section>
      )}
      <Section title="CORE FEATURES">
        <BulletList items={data.coreFeatures} />
      </Section>
      <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
        <Section title="USER ROLES">
          <div className="flex flex-wrap gap-2">
            {(data.userRoles ?? []).map((r, i) => (
              <Chip key={i}>{r}</Chip>
            ))}
          </div>
        </Section>
        <Section title="NON-FUNCTIONAL">
          <BulletList items={data.nonFunctionalRequirements} />
        </Section>
        <Section title="ASSUMPTIONS">
          <BulletList items={data.assumptions} />
        </Section>
        <Section title="CONSTRAINTS">
          <BulletList items={data.constraints} />
        </Section>
      </div>
      {data.openQuestions?.length > 0 && (
        <Section title="OPEN QUESTIONS">
          <BulletList items={data.openQuestions} />
        </Section>
      )}
      {typeof data.completionScore === 'number' && (
        <div className="text-label-sm text-on-surface-variant">
          Completeness score:{' '}
          <span className="text-secondary">{Math.round(data.completionScore * 100)}%</span>
        </div>
      )}
    </>
  )
}

function BusinessOutput({ data }) {
  return (
    <>
      {data.businessSummary && (
        <Section title="BUSINESS SUMMARY">
          <p className="text-body-md text-on-surface">{data.businessSummary}</p>
        </Section>
      )}
      <Section title="USER STORIES">
        {data.userStories?.length ? (
          <div className="space-y-2">
            {data.userStories.map((s, i) => (
              <div
                key={i}
                className="text-body-md rounded-lg border border-outline-variant bg-surface-container-lowest/50 p-3 text-on-surface"
              >
                <span className="text-secondary">As a</span> {s.actor},{' '}
                <span className="text-secondary">I want to</span> {s.action},{' '}
                <span className="text-secondary">so that</span> {s.benefit}.
              </div>
            ))}
          </div>
        ) : (
          <Empty label="—" />
        )}
      </Section>
      <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
        <Section title="BUSINESS GOALS">
          <BulletList items={data.businessGoals} />
        </Section>
        <Section title="EPICS">
          <BulletList items={data.epics} />
        </Section>
        <Section title="MARKET PAIN POINTS">
          <BulletList items={data.marketPainPoints} />
        </Section>
        <Section title="COMPETITOR INSIGHTS">
          <BulletList items={data.competitorInsights} />
        </Section>
      </div>
      <Section title="RECOMMENDED FEATURES">
        <BulletList items={data.recommendedFeatures} />
      </Section>
    </>
  )
}

function DatabaseOutput({ data }) {
  return (
    <>
      <Section title="ENTITY RELATIONSHIP DIAGRAM">
        <MermaidDiagram chart={data.mermaidErdChart} />
      </Section>
      <Section title="TABLES">
        <div className="space-y-3">
          {(data.tables ?? []).map((t, i) => (
            <div
              key={i}
              className="rounded-lg border border-outline-variant bg-surface-container-lowest/50 p-3"
            >
              <div className="flex items-center justify-between">
                <span className="text-body-md font-semibold text-primary">{t.name}</span>
                <span className="text-label-sm text-outline">{t.columns?.length ?? 0} cols</span>
              </div>
              {t.description && (
                <p className="text-label-sm mt-1 text-on-surface-variant">{t.description}</p>
              )}
              <div className="mt-2 flex flex-wrap gap-1.5">
                {(t.columns ?? []).map((c, j) => (
                  <span
                    key={j}
                    className="text-label-sm rounded border border-outline-variant px-2 py-0.5 text-on-surface-variant"
                  >
                    {c.name}
                    {c.isPrimaryKey ? ' · PK' : ''}
                    {c.isForeignKey ? ' · FK' : ''}
                  </span>
                ))}
              </div>
            </div>
          ))}
        </div>
      </Section>
      {data.relationships?.length > 0 && (
        <Section title="RELATIONSHIPS">
          <div className="space-y-1.5">
            {data.relationships.map((r, i) => (
              <div key={i} className="text-body-md text-on-surface">
                <span className="text-primary">{r.fromTable}</span>
                <span className="text-outline"> → </span>
                <span className="text-primary">{r.toTable}</span>
                <span className="text-label-sm text-on-surface-variant"> ({r.cardinality})</span>
              </div>
            ))}
          </div>
        </Section>
      )}
    </>
  )
}

function PlannerOutput({ data }) {
  return (
    <>
      {data.planningSummary && (
        <Section title="PLANNING SUMMARY">
          <p className="text-body-md text-on-surface">{data.planningSummary}</p>
        </Section>
      )}
      <div className="mb-6 flex gap-3">
        <Chip tone="secondary">{data.totalPhases ?? 0} phases</Chip>
        <Chip tone="secondary">{data.totalTasks ?? 0} tasks</Chip>
      </div>
      <Section title="PROJECT ROADMAP (GANTT)">
        <MermaidDiagram chart={data.mermaidGanttChart} />
      </Section>
      {data.rootProject && (
        <Section title="WORK BREAKDOWN">
          <Tree node={data.rootProject} />
        </Section>
      )}
    </>
  )
}

function Tree({ node, depth = 0 }) {
  if (!node) return null
  const children = node.children ?? []
  const isLeaf = children.length === 0
  return (
    <div style={{ paddingLeft: depth ? 16 : 0 }}>
      <div className="text-body-md flex items-center gap-2 text-on-surface">
        <span className={`material-symbols-outlined text-[16px] ${isLeaf ? 'text-outline' : 'text-primary'}`}>
          {isLeaf ? 'task_alt' : 'folder'}
        </span>
        <span>{node.name}</span>
        {node.complexity && <Chip tone="tertiary">{node.complexity}</Chip>}
      </div>
      {children.map((child, i) => (
        <Tree key={i} node={child} depth={depth + 1} />
      ))}
    </div>
  )
}

function RiskOutput({ data }) {
  const levelTone =
    {
      LOW: 'secondary',
      MEDIUM: 'tertiary',
      HIGH: 'tertiary',
      CRITICAL: 'primary',
    }[data.overallRiskLevel] ?? 'primary'

  return (
    <>
      <div className="mb-6 flex items-center gap-3">
        <Chip tone={levelTone}>Overall: {data.overallRiskLevel || 'N/A'}</Chip>
        <span className="text-label-sm text-on-surface-variant">
          Score {data.overallRiskScore ?? '—'} / 25
        </span>
      </div>
      <Section title="RISK FACTORS">
        <div className="space-y-3">
          {(data.riskFactors ?? []).map((r, i) => (
            <div
              key={i}
              className="rounded-lg border border-outline-variant bg-surface-container-lowest/50 p-3"
            >
              <div className="flex items-center justify-between">
                <span className="text-body-md font-semibold text-on-surface">{r.category}</span>
                <span className="text-label-sm text-tertiary">
                  I{r.impactLevel} × P{r.probabilityLevel} = {r.riskScore}
                </span>
              </div>
              <p className="text-body-md mt-1 text-on-surface">{r.description}</p>
              {r.evidence && (
                <p className="text-label-sm mt-2 text-on-surface-variant">
                  <span className="text-outline">Evidence: </span>
                  {r.evidence}
                </p>
              )}
              {r.mitigationStrategy && (
                <p className="text-label-sm mt-1 text-secondary">
                  <span className="text-outline">Mitigation: </span>
                  {r.mitigationStrategy}
                </p>
              )}
            </div>
          ))}
        </div>
      </Section>
      {data.conclusion && (
        <Section title="CONCLUSION">
          <p className="text-body-md text-on-surface">{data.conclusion}</p>
        </Section>
      )}
    </>
  )
}

const RENDERERS = {
  requirement: RequirementOutput,
  business: BusinessOutput,
  database: DatabaseOutput,
  planner: PlannerOutput,
  risk: RiskOutput,
}

export default function OutputViewer({ agent, state }) {
  if (!agent) {
    return (
      <div className="flex h-full min-h-[16rem] items-center justify-center rounded-xl border border-white/10 bg-surface-container/40 backdrop-blur-md">
        <div className="text-center">
          <span className="material-symbols-outlined text-[40px] text-outline">visibility</span>
          <p className="text-body-md mt-2 text-on-surface-variant">
            Select a completed agent to view its output.
          </p>
        </div>
      </div>
    )
  }

  const Renderer = RENDERERS[agent.id]
  const data = state?.output

  return (
    <div className="rounded-xl border border-white/10 bg-surface-container/60 p-6 backdrop-blur-md">
      <div className="mb-5 flex items-center gap-2 border-b border-white/10 pb-4">
        <span className="material-symbols-outlined text-primary">{agent.icon}</span>
        <h3 className="text-headline-md text-on-surface">{agent.label}</h3>
      </div>
      {data ? (
        Renderer ? (
          <Renderer data={data} />
        ) : (
          <CodeBlock value={JSON.stringify(data, null, 2)} />
        )
      ) : (
        <Empty label="No output available for this agent." />
      )}
    </div>
  )
}
