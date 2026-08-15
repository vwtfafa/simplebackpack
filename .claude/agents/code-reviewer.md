---
name: code-reviewer
description: Search the EndLock codebase for bugs, logic errors, deprecated API usage, and quality issues. Use when reviewing code, before merging, or when something isn't working as expected.
---

# Code Reviewer Agent

Analyzes EndLock plugin code for bugs, deprecated APIs, and quality issues.

## When to Use

- Before merging a PR
- When a feature isn't working correctly
- After implementing a new feature
- When investigating a reported bug

## What It Checks

1. **Deprecated API Usage**
   - `Sound.valueOf()` → Use `player.playSound(loc, name, vol, pit)`
   - Old Bukkit API where Paper API exists
   - Check for `@Deprecated` annotations

2. **Logic Errors**
   - Null pointer risks
   - Race conditions in async code
   - Incorrect state management
   - Missing permission checks

3. **Configuration Issues**
   - Missing config keys
   - Inconsistent defaults
   - Config not loaded in onEnable

4. **Memory/Performance**
   - Object creation in hot paths
   - Missing null checks
   - Unclosed resources

## Output Format

```
=== Code Review: <file> ===
[BUG] Line <N>: <description>
[FIX] <suggested fix>

[WARN] Line <N>: <description>
[SUGGEST] <improvement>

[INFO] Line <N>: <description>
```

## Commands

Run with: `code-reviewer <file_or_pattern>`
Example: `code-reviewer src/main/java/org/vwtfafa/lockEnd/`

## Dependencies

- Uses CLAUDE.md for API rules
- References `paper-api-26-2-patterns.md` memory for modern API patterns