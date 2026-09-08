# Threat detection is a uniform structural read, not a per-mob whitelist

v1 mob coverage ([#9]) is defined structurally: a mob is a Threat when its attack target —
read server-side as `getTarget()`, or the brain's `ATTACK_TARGET` memory for mobs that store
their target without overriding `getTarget()` (e.g. the nautilus line, 1.21.11+) — is the
observing player, and the mob is within the detection radius. The spec maintains no per-mob
whitelist: goal-system mobs, brain-system mobs, provoked neutrals, and any mob added in
future versions are covered by the same read. That is what keeps coverage automatic across
the rolling 1.21.1–26.2 multi-version target.

## Considered options

- **Per-mob whitelist** (rejected): explicit and auditable, but every version line would need
  manual onboarding of new mobs, which scales badly against the rolling multi-version policy.
- **`getTarget()` only, no brain-memory supplement** (rejected): the single-read-point purity
  from the detection research would leave provoked nautilus-line mobs (1.21.11+) undetected,
  contradicting the locked "provoked neutrals qualify" decision. The supplement is public API
  (`LivingEntity.getBrain()`), needs no mixin, and coincides with `getTarget()` for every mob
  that overrides it.

Structural gaps are accepted and documented in the spec rather than special-cased:
ender dragon (phase-local targeting), goat rams (`RAM_TARGET`, no attack target),
pufferfish/sulfur-cube contact damage, wither side heads, slime/magma-cube contact damage,
and creepers ignited without a player target (flint and steel).

Evidence trail: decision in [#9]; detection research in [#6] — note its ghast claim
("never sets a target") was falsified against untampered 1.21.1/1.21.11 sources; ghasts are
visible to the uniform read.

[#6]: https://github.com/imyifeng/whos-after-me/issues/6
[#9]: https://github.com/imyifeng/whos-after-me/issues/9
