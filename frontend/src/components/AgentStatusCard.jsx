/**
 * AgentStatusCard — one card per pipeline agent, showing live status.
 * Selectable: clicking a completed agent shows its output in the OutputViewer.
 */
import { AgentStatus } from '../hooks/usePipeline'

const STATUS_META = {
  [AgentStatus.PENDING]: {
    chip: 'Pending',
    chipClass: 'bg-outline/10 text-on-surface-variant',
    icon: 'schedule',
    iconClass: 'text-outline',
  },
  [AgentStatus.ACTIVE]: {
    chip: 'Running',
    chipClass: 'bg-primary-container/10 text-primary',
    icon: 'progress_activity',
    iconClass: 'text-primary animate-spin',
  },
  [AgentStatus.COMPLETE]: {
    chip: 'Complete',
    chipClass: 'bg-secondary-container/10 text-secondary',
    icon: 'check_circle',
    iconClass: 'text-secondary',
  },
  [AgentStatus.FAILED]: {
    chip: 'Failed',
    chipClass: 'bg-error-container/20 text-error',
    icon: 'error',
    iconClass: 'text-error',
  },
}

export default function AgentStatusCard({ agent, state, selected, onSelect }) {
  const meta = STATUS_META[state.status] ?? STATUS_META[AgentStatus.PENDING]
  const isComplete = state.status === AgentStatus.COMPLETE
  const isActive = state.status === AgentStatus.ACTIVE

  return (
    <button
      type="button"
      onClick={() => isComplete && onSelect(agent.id)}
      disabled={!isComplete}
      className={[
        'group flex w-full flex-col rounded-xl border p-4 text-left backdrop-blur-md transition',
        selected
          ? 'border-primary-container bg-surface-container-high/70'
          : 'border-white/10 bg-surface-container/50',
        isComplete ? 'cursor-pointer hover:border-primary-container/60' : 'cursor-default',
        isActive ? 'ring-1 ring-primary-container/40' : '',
      ].join(' ')}
    >
      <div className="flex items-start justify-between">
        <span
          className={`material-symbols-outlined text-[22px] ${
            selected ? 'text-primary' : 'text-on-surface-variant'
          }`}
        >
          {agent.icon}
        </span>
        <span
          className={`text-label-sm inline-flex items-center gap-1 rounded-full px-2 py-0.5 ${meta.chipClass}`}
        >
          <span className={`material-symbols-outlined text-[12px] ${meta.iconClass}`}>
            {meta.icon}
          </span>
          {meta.chip}
        </span>
      </div>

      <div className="text-body-md mt-3 font-semibold text-on-surface">{agent.label}</div>
      <div className="text-label-sm mt-1 text-on-surface-variant">
        {state.message || agent.blurb}
      </div>
    </button>
  )
}
