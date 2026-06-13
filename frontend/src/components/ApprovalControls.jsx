/**
 * ApprovalControls — the human review gate after the pipeline finishes.
 *
 * NOTE: the backend (com.autonomouspm) exposes no approve/reject endpoint — the
 * pipeline runs straight through. These controls therefore act on client-side
 * state only: "Approve & Finalize" locks the result and reveals the final
 * package; "Discard & Restart" clears state for a fresh run.
 */
import { Phase } from '../hooks/usePipeline'

export default function ApprovalControls({ phase, onApprove, onReject }) {
  const isComplete = phase === Phase.COMPLETE
  const isApproved = phase === Phase.APPROVED
  const isPartial = phase === Phase.PARTIAL
  const isFailed = phase === Phase.FAILED

  if (isApproved) {
    return (
      <div className="flex items-center gap-2 rounded-xl border border-secondary/30 bg-secondary-container/10 px-5 py-4 backdrop-blur-md">
        <span className="material-symbols-outlined text-secondary">verified</span>
        <span className="text-body-md text-secondary">
          Package approved — see the final output below.
        </span>
      </div>
    )
  }

  if (isFailed) {
    return (
      <div className="flex items-center justify-between rounded-xl border border-error/30 bg-error-container/10 px-5 py-4 backdrop-blur-md">
        <div className="flex items-center gap-2">
          <span className="material-symbols-outlined text-error">error</span>
          <span className="text-body-md text-error">The pipeline did not complete.</span>
        </div>
        <button
          type="button"
          onClick={onReject}
          className="text-body-md inline-flex items-center gap-2 rounded-lg border border-outline-variant px-4 py-2 text-on-surface transition hover:border-primary-container hover:text-primary"
        >
          <span className="material-symbols-outlined text-[20px]">restart_alt</span>
          Start over
        </button>
      </div>
    )
  }

  if (!isComplete && !isPartial) return null

  return (
    <div className="flex flex-col items-start justify-between gap-4 rounded-xl border border-white/10 bg-surface-container/60 px-5 py-4 backdrop-blur-md sm:flex-row sm:items-center">
      <div>
        <div className="text-body-md font-semibold text-on-surface">Review the analysis package</div>
        <div className="text-label-sm text-on-surface-variant">
          {isPartial
            ? 'Pipeline finished partially — review what was produced, or restart.'
            : 'All five agents completed. Approve to finalize, or discard and try a new brief.'}
        </div>
      </div>

      <div className="flex items-center gap-3">
        <button
          type="button"
          onClick={onReject}
          className="text-body-md inline-flex items-center gap-2 rounded-lg border border-outline-variant px-4 py-2 text-on-surface transition hover:border-error hover:text-error"
        >
          <span className="material-symbols-outlined text-[20px]">replay</span>
          Discard &amp; Restart
        </button>
        {isComplete && (
          <button
            type="button"
            onClick={onApprove}
            className="text-body-md inline-flex items-center gap-2 rounded-lg bg-secondary-container px-5 py-2 font-semibold text-on-secondary-container transition hover:opacity-90"
          >
            <span className="material-symbols-outlined text-[20px]">check</span>
            Approve &amp; Finalize
          </button>
        )}
      </div>
    </div>
  )
}
