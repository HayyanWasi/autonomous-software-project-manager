# React Project Structure

---

## Directory Layout

```
frontend/
├── src/
│   ├── components/
│   │   ├── InputPanel.jsx          ← user types project description here
│   │   ├── AgentStatusCard.jsx     ← shows each agent's status
│   │   ├── OutputViewer.jsx        ← displays agent output (JSON rendered)
│   │   ├── ApprovalControls.jsx    ← approve / give feedback buttons
│   │   └── FinalOutputPanel.jsx    ← shows full final package
│   ├── hooks/
│   │   ├── useAgentStatus.js       ← subscribes to SSE events
│   │   └── usePipelineControl.js   ← sends start/approve/reject calls
│   ├── services/
│   │   └── api.js                  ← all fetch() calls to backend
│   ├── App.jsx
│   └── index.js
├── public/
└── package.json
```

---

## Approved Dependencies

```json
{
  "dependencies": {
    "react": "^18.0.0",
    "react-dom": "^18.0.0",
    "axios": "^1.6.0"
  }
}
```

No other frontend libraries without user approval.