# Who's After Me

A Fabric mod that shows on-screen indicators for mobs that currently intend to attack the player. This glossary defines the canonical vocabulary used across the spec, code, and issues. Finalized for v1 in the v1 spec ([#11](https://github.com/imyifeng/whos-after-me/issues/11)).

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

**Threat set**:
The set of threats currently detected for one player: everything the server detects, syncs, and the client renders against (bounded for display by the indicator cap).
_Avoid_: aggro list, target list

**Threat kind**:
The reserved classification byte carried per synced threat. v1 sends only `TARGETING`; the byte exists so post-v1 escalation levels (e.g. an actively attacking mob) need no protocol change.
_Avoid_: threat type, mob category

**Aiming mode**:
The player's choice of how threat directions map onto the orbit: **Absolute** (compass bearing, pitch ignored; the default) or **Screen-relative** (projected through the camera's pitch and FOV).
_Avoid_: tracking mode, view mode, projection mode

**Anchor**:
One of the six Minecraft versions v1 ships (1.21.1, 1.21.4, 1.21.8, 1.21.11, 26.1.2, 26.2); the build, test, and CI matrix is defined over the anchors.
_Avoid_: target version, supported version
