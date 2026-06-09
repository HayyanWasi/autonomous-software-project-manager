/**
 * InputPanel — where the user types a project idea and starts the pipeline.
 * Glassmorphic card per the Zeptex "Synthetic Intelligence Interface" design.
 */
import { useState } from 'react'

const EXAMPLES = [
  'A food delivery app for a small city',
  'A SaaS tool for managing freelance invoices',
  'A peer-to-peer campus study marketplace',
]

export default function InputPanel({ onStart, disabled }) {
  const [value, setValue] = useState('')

  const submit = () => {
    const text = value.trim()
    if (!text || disabled) return
    onStart(text)
  }

  const onKeyDown = (e) => {
    // Cmd/Ctrl + Enter submits.
    if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') {
      e.preventDefault()
      submit()
    }
  }

  return (
    <div className="rounded-xl border border-white/10 bg-surface-container/60 p-6 backdrop-blur-md">
      <label className="text-label-md mb-3 block text-on-surface-variant">
        PROJECT BRIEF
      </label>

      <textarea
        value={value}
        onChange={(e) => setValue(e.target.value)}
        onKeyDown={onKeyDown}
        disabled={disabled}
        rows={4}
        placeholder="Describe the software you want to build…"
        className="text-body-lg w-full resize-none rounded-lg border border-outline-variant bg-surface-container-lowest/80 p-4 text-on-surface placeholder:text-outline focus:border-primary-container focus:outline-none focus:ring-1 focus:ring-primary-container disabled:opacity-50"
      />

      <div className="mt-4 flex flex-wrap items-center gap-2">
        {EXAMPLES.map((ex) => (
          <button
            key={ex}
            type="button"
            disabled={disabled}
            onClick={() => setValue(ex)}
            className="text-label-sm rounded-full border border-outline-variant px-3 py-1.5 text-on-surface-variant transition hover:border-primary-container hover:text-primary disabled:opacity-50"
          >
            {ex}
          </button>
        ))}
      </div>

      <div className="mt-5 flex items-center justify-between">
        <span className="text-label-sm text-outline">⌘ / Ctrl + Enter to run</span>
        <button
          type="button"
          onClick={submit}
          disabled={disabled || !value.trim()}
          className="text-body-md inline-flex items-center gap-2 rounded-lg bg-primary-container px-5 py-2.5 font-semibold text-on-primary-container transition hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-40"
        >
          <span className="material-symbols-outlined text-[20px]">bolt</span>
          Generate
        </button>
      </div>
    </div>
  )
}
