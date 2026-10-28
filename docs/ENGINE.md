# The planning engine

The planning engine is intended to help turn a site's constraints and a household's requirements into a residential floor-plan concept.

It considers plot geometry, setbacks, floors, room requirements and how spaces connect. Existing checks examine room fit, circulation and furniture placement. A designer still needs to review the result; a concept is not a construction document or structural approval.

## Availability

The engine is not connected to the current Plotus app. Plot setup now saves manually entered boundaries, orientation and road details; floor-plan generation is still in development.

## Existing technology

The engine and its CAD assets predate the hackathon and remain private. The public app references them through an optional private submodule and can be built without access to it. A future hosted connection will keep the engine behind the app's service layer.

The engine's Python service wrapper was created during the event. See [sources and credits](PROVENANCE.md) for the distinction between existing technology and new work.
