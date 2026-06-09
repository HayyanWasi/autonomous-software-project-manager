/**
 * api.js — all backend calls for the Zeptex pipeline UI.
 *
 * Contract (com.autonomouspm.controller.PipelineController):
 *   - POST /api/pipeline/run   → text/event-stream (SSE). Body: { idea }.
 *   - GET  /api/pipeline/{id}  → persisted ProjectState JSON.
 *
 * Per project decision: native fetch + ReadableStream for the SSE stream
 * (POST bodies rule out EventSource); axios for non-streaming reads.
 */
import axios from 'axios'

const BASE = '/api/pipeline'

/**
 * The five pipeline agents in execution order — mirrors CentralOrchestrator.
 * `event` is the SSE event name the backend emits when that agent completes.
 */
export const AGENTS = [
  {
    id: 'requirement',
    event: 'requirement-complete',
    label: 'Requirement Analyst',
    icon: 'description',
    blurb: 'Extracting requirements & scope',
  },
  {
    id: 'business',
    event: 'business-complete',
    label: 'Business Analyst',
    icon: 'insights',
    blurb: 'Market research & user stories',
  },
  {
    id: 'database',
    event: 'database-complete',
    label: 'Database Architect',
    icon: 'schema',
    blurb: 'Designing schema & ERD',
  },
  {
    id: 'planner',
    event: 'planner-complete',
    label: 'Project Planner',
    icon: 'calendar_month',
    blurb: 'Building the project roadmap',
  },
  {
    id: 'risk',
    event: 'risk-complete',
    label: 'Risk Analyst',
    icon: 'troubleshoot',
    blurb: 'Assessing real-world risks',
  },
]

/** SSE event name → agent id (terminal events are not in this map). */
export const EVENT_TO_AGENT = AGENTS.reduce((map, agent) => {
  map[agent.event] = agent.id
  return map
}, {})

export const PIPELINE_COMPLETE = 'pipeline-complete'
export const PIPELINE_FAILED = 'pipeline-failed'

/**
 * Parses a single SSE block (text between blank lines) into { event, payload }.
 * Handles multi-line `data:` fields and CRLF. Returns null for empty/comment blocks.
 */
function parseSseBlock(block) {
  let eventName = 'message'
  const dataLines = []

  for (const rawLine of block.split('\n')) {
    const line = rawLine.replace(/\r$/, '')
    if (!line || line.startsWith(':')) continue
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).replace(/^ /, ''))
    }
  }

  if (dataLines.length === 0) return null

  const raw = dataLines.join('\n')
  let payload
  try {
    payload = JSON.parse(raw)
  } catch {
    payload = { data: raw }
  }
  return { event: eventName, payload }
}

/**
 * Starts a pipeline run and streams agent results via SSE.
 *
 * @param {string} idea - the user's project idea.
 * @param {object} opts
 * @param {(evt: {event: string, payload: any}) => void} opts.onEvent - per-event callback.
 * @param {AbortSignal} [opts.signal] - aborts the in-flight stream.
 */
export async function runPipeline(idea, { onEvent, signal } = {}) {
  const res = await fetch(`${BASE}/run`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    },
    body: JSON.stringify({ idea }),
    signal,
  })

  if (!res.ok || !res.body) {
    throw new Error(`Pipeline request failed (HTTP ${res.status})`)
  }

  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })

    let sep
    // SSE events are separated by a blank line.
    while ((sep = buffer.indexOf('\n\n')) !== -1) {
      const block = buffer.slice(0, sep)
      buffer = buffer.slice(sep + 2)
      const parsed = parseSseBlock(block)
      if (parsed) onEvent?.(parsed)
    }
  }

  // Flush any trailing event without a closing blank line.
  const tail = parseSseBlock(buffer)
  if (tail) onEvent?.(tail)
}

/**
 * Fetches a previously-persisted pipeline run (non-streaming).
 * @param {string} pipelineId
 * @returns {Promise<object>} the ProjectState.
 */
export async function getPipeline(pipelineId) {
  const { data } = await axios.get(`${BASE}/${pipelineId}`)
  return data
}
