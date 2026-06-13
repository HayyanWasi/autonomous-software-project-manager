/**
 * usePipeline — single hook that owns ALL pipeline state (project decision).
 *
 * Replaces the spec's useAgentStatus + usePipelineControl. It manages the SSE
 * lifecycle, per-agent status/output, the final report, and the client-side
 * review gate (the backend exposes no approve/reject endpoint).
 */
import { useCallback, useRef, useState } from 'react'
import {
  AGENTS,
  EVENT_TO_AGENT,
  PIPELINE_COMPLETE,
  PIPELINE_FAILED,
  runPipeline,
  getPipeline,
} from '../services/api'

/** Pipeline lifecycle phases. */
export const Phase = {
  IDLE: 'idle',
  RUNNING: 'running',
  COMPLETE: 'complete',
  PARTIAL: 'partial',
  FAILED: 'failed',
  APPROVED: 'approved',
}

/** Per-agent status values. */
export const AgentStatus = {
  PENDING: 'pending',
  ACTIVE: 'active',
  COMPLETE: 'complete',
  FAILED: 'failed',
}

function initialAgents() {
  const map = {}
  for (const agent of AGENTS) {
    map[agent.id] = { status: AgentStatus.PENDING, output: null, message: '' }
  }
  return map
}

function markActiveFailed(agents) {
  const next = { ...agents }
  for (const id of Object.keys(next)) {
    if (next[id].status === AgentStatus.ACTIVE) {
      next[id] = { ...next[id], status: AgentStatus.FAILED }
    }
  }
  return next
}

export function usePipeline() {
  const [idea, setIdea] = useState('')
  const [phase, setPhase] = useState(Phase.IDLE)
  const [agents, setAgents] = useState(initialAgents)
  const [activeAgentId, setActiveAgentId] = useState(null)
  const [selectedAgentId, setSelectedAgentId] = useState(null)
  const [finalReport, setFinalReport] = useState(null)
  const [error, setError] = useState(null)

  const abortRef = useRef(null)

  const reset = useCallback(() => {
    abortRef.current?.abort()
    abortRef.current = null
    setIdea('')
    setPhase(Phase.IDLE)
    setAgents(initialAgents())
    setActiveAgentId(null)
    setSelectedAgentId(null)
    setFinalReport(null)
    setError(null)
  }, [])

  /** Routes one SSE event into state. */
  const handleEvent = useCallback(({ event, payload }) => {
    if (event === PIPELINE_COMPLETE) {
      setFinalReport(typeof payload?.data === 'string' ? payload.data : '')
      setActiveAgentId(null)
      setPhase(Phase.COMPLETE)
      return
    }

    if (event === PIPELINE_FAILED) {
      setError(payload?.message || 'Pipeline failed')
      setActiveAgentId(null)
      setAgents((prev) => markActiveFailed(prev))
      setPhase(Phase.FAILED)
      return
    }

    const agentId = EVENT_TO_AGENT[event]
    if (!agentId) return

    const isPartial = payload?.status === 'PARTIAL'

    setAgents((prev) => ({
      ...prev,
      [agentId]: {
        status: isPartial ? AgentStatus.FAILED : AgentStatus.COMPLETE,
        output: payload?.data ?? null,
        message: payload?.message || '',
      },
    }))
    // Auto-select the first completed agent so output is visible immediately.
    setSelectedAgentId((current) => current ?? agentId)

    if (isPartial) {
      setActiveAgentId(null)
      setPhase(Phase.PARTIAL)
      return
    }

    // Promote the next agent to active.
    const idx = AGENTS.findIndex((a) => a.id === agentId)
    const next = AGENTS[idx + 1]
    if (next) {
      setActiveAgentId(next.id)
      setAgents((prev) => ({
        ...prev,
        [next.id]: { ...prev[next.id], status: AgentStatus.ACTIVE },
      }))
    }
  }, [])

  /** Starts a new pipeline run for the given idea. */
  const start = useCallback(
    async (ideaText) => {
      const text = (ideaText ?? '').trim()
      if (!text) return

      const controller = new AbortController()
      abortRef.current = controller

      const fresh = initialAgents()
      fresh[AGENTS[0].id].status = AgentStatus.ACTIVE

      setIdea(text)
      setAgents(fresh)
      setActiveAgentId(AGENTS[0].id)
      setSelectedAgentId(null)
      setFinalReport(null)
      setError(null)
      setPhase(Phase.RUNNING)

      try {
        await runPipeline(text, { onEvent: handleEvent, signal: controller.signal })
      } catch (err) {
        if (controller.signal.aborted) return
        setError(err?.message || 'Connection to pipeline failed')
        setAgents((prev) => markActiveFailed(prev))
        setPhase(Phase.FAILED)
      }
    },
    [handleEvent],
  )

  /** Loads a previously-persisted run by id (non-streaming). */
  const loadPipeline = useCallback(async (pipelineId) => {
    setPhase(Phase.RUNNING)
    setError(null)
    try {
      const state = await getPipeline(pipelineId)
      const map = initialAgents()
      const pairs = [
        ['requirement', state.requirementContext],
        ['business', state.businessContext],
        ['database', state.databaseContext],
        ['planner', state.ganttContext],
        ['risk', state.riskContext],
      ]
      for (const [id, output] of pairs) {
        if (output) {
          map[id] = { status: AgentStatus.COMPLETE, output, message: 'Loaded from history' }
        }
      }
      setIdea(state.rawUserInput || '')
      setAgents(map)
      setActiveAgentId(null)
      setSelectedAgentId('requirement')
      setFinalReport(state.finalReport || null)
      setPhase(state.finalReport ? Phase.COMPLETE : Phase.PARTIAL)
    } catch (err) {
      setError(err?.message || 'Could not load pipeline')
      setPhase(Phase.FAILED)
    }
  }, [])

  const selectAgent = useCallback((id) => setSelectedAgentId(id), [])

  // Client-side review gate — backend has no approve/reject endpoint.
  const approve = useCallback(() => setPhase(Phase.APPROVED), [])
  const reject = useCallback(() => reset(), [reset])

  return {
    idea,
    phase,
    agents,
    activeAgentId,
    selectedAgentId,
    finalReport,
    error,
    start,
    reset,
    approve,
    reject,
    selectAgent,
    loadPipeline,
  }
}
