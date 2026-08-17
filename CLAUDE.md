# Paper Plugin Development

This project is a Minecraft Paper plugin targeting **Paper 26.2**.

## Project Goal

Develop, maintain, debug, and improve this plugin using modern Paper APIs and clean Java practices.

Always prioritize correctness, compatibility with the configured Paper API version, maintainability, and minimal unnecessary changes.

---

## API Documentation

Use the official Paper documentation and Javadocs as the primary source of truth.

Official documentation:

- https://docs.papermc.io/paper/dev/api/
- https://papermc.io/javadocs/
- https://jd.papermc.io/paper/26.2/

### API Research Rules

Before using an unfamiliar, uncertain, or potentially version-dependent API:

1. Check the official Paper 26.2 Javadocs.
2. Verify that the class, method, constructor, event, or interface actually exists.
3. Verify method parameters and return types.
4. Check whether the API is deprecated.
5. Prefer the modern Paper API when available.
6. Never guess or invent API names.

If documentation cannot be accessed, clearly state the uncertainty instead of pretending an API exists.

---

## Version Requirements

Target:

- Minecraft: 26.2
- Paper: 26.2

The version configured in `build.gradle` is the authoritative dependency version.

Never introduce APIs from newer or older Paper versions unless explicitly requested.

Before changing dependencies:

- Inspect the existing `build.gradle`.
- Preserve the project's existing dependency structure.
- Do not randomly upgrade dependencies.

---

## API Preferences

Prefer:

1. Paper API
2. Bukkit API when Paper does not provide an appropriate alternative
3. Other dependencies already present in the project

Avoid introducing new dependencies unless they are actually necessary.

Do not use NMS, CraftBukkit internals, reflection, or implementation-specific APIs unless explicitly required.

If NMS or internal APIs are required, explain why before using them.

---

## Coding Standards

Write clean, readable, maintainable Java.

Prefer:

- Small, focused methods
- Clear naming
- Proper null handling
- Early returns where appropriate
- Minimal duplication
- Existing project patterns
- Modern Java features supported by the project

Avoid:

- Unnecessary abstractions
- Overengineering
- Duplicate functionality
- Dead code
- Unused imports
- Magic numbers/strings where constants are appropriate
- Large methods doing unrelated tasks

Do not rewrite unrelated code when implementing a feature or fixing a bug.

---

## Existing Codebase

Before making changes:

1. Inspect the relevant existing classes.
2. Understand how the current implementation works.
3. Search for existing utilities or patterns that can be reused.
4. Follow the project's existing architecture.
5. Make the smallest reasonable change.

Do not create a second implementation of functionality that already exists.

---

## Commands

Before assuming build/test commands, inspect the project configuration.

Typical commands:

```bash
./gradlew build
./gradlew test