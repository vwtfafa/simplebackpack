---
name: parallel-agent-controller
description: Coordinate multiple feature-dev agents to work on independent features simultaneously without conflicts. Use when implementing multiple features from a list - starts agents only as far as needed to avoid interference.
---

# Parallel Agent Controller

Orchestrates multiple feature agents working on independent features simultaneously.

## When to Use

- Implementing multiple features from a roadmap
- When features are independent and can be developed in parallel
- After feature planning is complete

## How It Works

1. **Receive Feature List** - Gets a list of features to implement
2. **Analyze Dependencies** - Checks which features can run in parallel
3. **Set Containment Boundaries** - Each agent gets isolated scope:
   - Different Java files (never same file)
   - Different config sections
   - Different message keys
4. **Launch Agents** - Starts agents with clear boundaries
5. **Monitor Progress** - Reports completion status
6. **Handle Conflicts** - If conflicts detected, pauses and resolves

## Containment Rules

| Rule | Description |
|------|-------------|
| **File Isolation** | Each agent works on different Java files |
| **Config Separation** | Each agent modifies different config sections |
| **Message Uniqueness** | Each agent adds unique message keys |
| **Permission Distinct** | Each agent uses different permission nodes |
| **No Shared State** | Agents don't modify shared state simultaneously |

## Launch Pattern

```
Feature List: [A, B, C, D]

Analysis:
- A (Core Locking) → LockReasonManager.java ✓
- B (Notifications) → PreviewNotificationManager.java ✓
- C (Logging) → AttemptLogger.java ✓
- D (Scheduling) → ScheduleManager.java ✓

All independent → Launch all 4 agents
```

## Conflict Resolution

If two agents need same file:
1. Pause one agent
2. Merge plans
3. Re-launch with adjusted scope

## Example Usage

```
User: "Implement these features in parallel:
- Lock tiers
- Boss bar visibility
- Discord integration"

Controller:
1. Analyzes dependencies
2. Assigns: LockTierManager, BossBarManager, DiscordWebhook
3. Launches 3 agents
4. Reports: "3 agents running, isolated scopes"
```

## Output Format

```
=== Parallel Agent Controller ===

[FEATURES]
- Feature A: LockReasonManager (agent-1)
- Feature B: BossBarManager (agent-2)
- Feature C: DiscordWebhook (agent-3)

[STATUS]
- agent-1: RUNNING (100% isolated)
- agent-2: RUNNING (100% isolated)
- agent-3: RUNNING (100% isolated)

[CONFLICTS] None detected
```

## Dependencies

- Uses `feature-planner` for blueprint creation
- Uses `paper-api-researcher` for API checks
- Uses `code-reviewer` for conflict detection