# Who's After Me

A Fabric mod that shows on-screen indicators for mobs that currently intend to attack the player. This glossary defines the canonical vocabulary used across the spec, code, and issues.

## Language

**Threat**:
A mob whose current attack target is the observing player. Provoked neutral mobs qualify once they target the player; other players never do.
_Avoid_: attack intent, aggro, attacker, hostile mob

**Threat indicator**:
The on-screen marker for one threat: a short arc drawn on the orbit with a triangle at its center pointing in the threat's direction.
_Avoid_: arrow, radar ping, marker

**Orbit**:
The ellipse matched to the viewport's aspect ratio (a circle at 1:1) on which threat indicators are drawn; its radius is player-adjustable.
_Avoid_: ring, circle, track

**Detection radius**:
The fixed, wall-transparent distance from the player within which threats are detected.
_Avoid_: range, aggro range, follow range
