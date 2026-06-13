/**
 * FinalOutputPanel — shows the assembled Markdown report (the pipeline-complete
 * payload) with copy + download actions.
 *
 * No Markdown library is in the approved dependency set, so the report is shown
 * in a clean, scrollable, whitespace-preserving block rather than parsed to HTML.
 */
import { useState } from 'react'

export default function FinalOutputPanel({ report }) {
  const [copied, setCopied] = useState(false)
  if (!report) return null

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(report)
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    } catch {
      // Clipboard may be unavailable (e.g. non-secure context) — ignore silently.
    }
  }

  const download = () => {
    const blob = new Blob([report], { type: 'text/markdown' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'zeptex-project-report.md'
    a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <div className="rounded-xl border border-white/10 bg-surface-container/60 backdrop-blur-md">
      <div className="flex items-center justify-between border-b border-white/10 px-6 py-4">
        <div className="flex items-center gap-2">
          <span className="material-symbols-outlined text-primary">description</span>
          <h3 className="text-headline-md text-on-surface">Final Project Package</h3>
        </div>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={copy}
            className="text-label-md inline-flex items-center gap-1.5 rounded-lg border border-outline-variant px-3 py-1.5 text-on-surface-variant transition hover:border-primary-container hover:text-primary"
          >
            <span className="material-symbols-outlined text-[16px]">
              {copied ? 'check' : 'content_copy'}
            </span>
            {copied ? 'Copied' : 'Copy'}
          </button>
          <button
            type="button"
            onClick={download}
            className="text-label-md inline-flex items-center gap-1.5 rounded-lg border border-outline-variant px-3 py-1.5 text-on-surface-variant transition hover:border-primary-container hover:text-primary"
          >
            <span className="material-symbols-outlined text-[16px]">download</span>
            .md
          </button>
        </div>
      </div>

      <pre className="text-body-md max-h-[36rem] overflow-auto whitespace-pre-wrap px-6 py-5 font-sans text-on-surface">
        {report}
      </pre>
    </div>
  )
}
