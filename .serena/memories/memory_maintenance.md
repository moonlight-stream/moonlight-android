# Memory maintenance

- `mem:core` is a compact index of current durable project facts, not a session
  log. Link focused memories instead of growing it indefinitely.
- Record ownership, boundaries, lifecycle invariants, stable commands, accepted
  limitations and locations of important tests.
- Do not record volatile line numbers, temporary build paths, one-off debugging,
  speculative plans, generic Android knowledge or facts not confirmed by code.
- Update a memory when code makes it false. If a task changes no durable project
  knowledge, do not create memory noise; report that no update was needed.
