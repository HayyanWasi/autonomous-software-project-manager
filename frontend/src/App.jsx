/**
 * App — the Zeptex workspace shell (sidebar + topbar + main), wiring the whole
 * pipeline UI to the single usePipeline hook. Branding and layout follow the
 * Stitch "Synthetic Intelligence Interface" design.
 */
import { AGENTS } from './services/api'
import { usePipeline, Phase } from './hooks/usePipeline'
import InputPanel from './components/InputPanel'
import AgentStatusCard from './components/AgentStatusCard'
import OutputViewer from './components/OutputViewer'
import ApprovalControls from './components/ApprovalControls'
import FinalOutputPanel from './components/FinalOutputPanel'

const NAV = [
  { icon: 'dashboard', label: 'Dashboard', active: true },
  { icon: 'history', label: 'History' },
  { icon: 'travel_explore', label: 'Explorations' },
  { icon: 'summarize', label: 'Reports' },
]

function Sidebar({ onNew }) {
  return (
    <aside className="hidden w-64 shrink-0 flex-col border-r border-white/10 bg-surface-container-lowest/60 p-5 backdrop-blur-md lg:flex">
      <div className="mb-8 flex items-center gap-2">
        <span className="material-symbols-outlined text-primary">all_inclusive</span>
        <span className="text-headline-md font-semibold tracking-tight text-on-surface">Zeptex</span>
      </div>

      <button
        type="button"
        onClick={onNew}
        className="text-body-md mb-6 inline-flex items-center justify-center gap-2 rounded-lg bg-primary-container px-4 py-2.5 font-semibold text-on-primary-container transition hover:opacity-90"
      >
        <span className="material-symbols-outlined text-[20px]">add</span>
        New Project
      </button>

      <nav className="space-y-1">
        {NAV.map((item) => (
          <div
            key={item.label}
            className={[
              'text-body-md flex items-center gap-3 rounded-lg px-3 py-2',
              item.active
                ? 'bg-surface-container-high/70 text-on-surface'
                : 'text-on-surface-variant hover:bg-surface-container/50',
            ].join(' ')}
          >
            <span className="material-symbols-outlined text-[20px]">{item.icon}</span>
            {item.label}
          </div>
        ))}
      </nav>

      <div className="mt-8">
        <div className="text-label-sm mb-2 px-3 text-outline">RECENT</div>
        {['Astro-Engine Migration', 'Project Alpha'].map((p) => (
          <div
            key={p}
            className="text-body-md flex items-center gap-3 rounded-lg px-3 py-2 text-on-surface-variant hover:bg-surface-container/50"
          >
            <span className="material-symbols-outlined text-[18px]">folder</span>
            {p}
          </div>
        ))}
      </div>

      <div className="mt-auto space-y-1 border-t border-white/10 pt-4">
        {[
          { icon: 'settings', label: 'Settings' },
          { icon: 'group_add', label: 'Invite Team' },
          { icon: 'help', label: 'Help' },
        ].map((item) => (
          <div
            key={item.label}
            className="text-body-md flex items-center gap-3 rounded-lg px-3 py-2 text-on-surface-variant hover:bg-surface-container/50"
          >
            <span className="material-symbols-outlined text-[20px]">{item.icon}</span>
            {item.label}
          </div>
        ))}
      </div>
    </aside>
  )
}

function Topbar() {
  return (
    <header className="flex items-center justify-between border-b border-white/10 bg-surface-container-lowest/40 px-6 py-4 backdrop-blur-md">
      <div className="flex items-center gap-2 lg:hidden">
        <span className="material-symbols-outlined text-primary">all_inclusive</span>
        <span className="text-headline-md font-semibold text-on-surface">Zeptex</span>
      </div>
      <div className="text-label-sm hidden text-outline lg:block">
        workspace / dashboard / new-analysis
      </div>
      <div className="flex items-center gap-4">
        <span className="material-symbols-outlined text-on-surface-variant">notifications</span>
        <div className="flex h-8 w-8 items-center justify-center rounded-full bg-primary-container text-label-md font-semibold text-on-primary-container">
          RD
        </div>
      </div>
    </header>
  )
}

export default function App() {
  const pipeline = usePipeline()
  const {
    phase,
    agents,
    selectedAgentId,
    finalReport,
    error,
    start,
    reset,
    approve,
    reject,
    selectAgent,
  } = pipeline

  const isIdle = phase === Phase.IDLE
  const isRunning = phase === Phase.RUNNING
  const selectedAgent = AGENTS.find((a) => a.id === selectedAgentId) || null
  const showApproved = phase === Phase.APPROVED

  return (
    <div className="flex min-h-screen bg-background text-on-background">
      <Sidebar onNew={reset} />

      <div className="flex min-w-0 flex-1 flex-col">
        <Topbar />

        <main className="mx-auto w-full max-w-container flex-1 px-6 py-8">
          {/* Greeting */}
          <div className="mb-8">
            <h1 className="text-display-lg text-on-surface">Hola!</h1>
            <p className="text-body-lg mt-1 text-on-surface-variant">
              What are we exploring today?
            </p>
          </div>

          {/* Input */}
          <div className="mb-8">
            <InputPanel onStart={start} disabled={isRunning} />
          </div>

          {error && (
            <div className="text-body-md mb-6 flex items-center gap-2 rounded-lg border border-error/30 bg-error-container/10 px-4 py-3 text-error">
              <span className="material-symbols-outlined text-[20px]">warning</span>
              {error}
            </div>
          )}

          {/* Pipeline view (hidden until a run starts) */}
          {!isIdle && (
            <>
              <div className="mb-6">
                <div className="text-label-md mb-3 text-on-surface-variant">AGENT PIPELINE</div>
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
                  {AGENTS.map((agent) => (
                    <AgentStatusCard
                      key={agent.id}
                      agent={agent}
                      state={agents[agent.id]}
                      selected={agent.id === selectedAgentId}
                      onSelect={selectAgent}
                    />
                  ))}
                </div>
              </div>

              <div className="mb-6">
                <ApprovalControls phase={phase} onApprove={approve} onReject={reject} />
              </div>

              <div className="mb-6">
                <OutputViewer
                  agent={selectedAgent}
                  state={selectedAgent ? agents[selectedAgent.id] : null}
                />
              </div>

              {showApproved && finalReport && (
                <div className="mb-6">
                  <FinalOutputPanel report={finalReport} />
                </div>
              )}
            </>
          )}
        </main>

        <footer className="border-t border-white/10 px-6 py-4">
          <span className="text-label-sm text-outline">© 2024 Zeptex. Absolute Clarity.</span>
        </footer>
      </div>
    </div>
  )
}
