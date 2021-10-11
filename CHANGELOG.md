# Unreleased

## Added
- Integration with https://github.com/lucywang000/clj-statecharts in `kee-frame.fsm.beta`.

## Changed
- Migrated from `leiningen` and `figwheel` to `deps` and `shadow-cljs`
- [BREAKING] Controllers are now restricted to only returning re-frame dispatch
  vectors. Previously they could return FSM declarations which would implicitly start the FSM.
  That is considered unnecessary implicit magic, as there is not much extra boilerplate in just dispatching
  to the FSM starter.
- `kee-frame.fsm.alpha` is now deprecated, and will be removed in future versions.

# 1.0.x (2021-10-0x / sha)

## Added

## Fixed

## Changed
