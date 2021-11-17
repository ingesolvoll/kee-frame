# Unreleased

## Added

## Fixed

## Changed

# 1.3.2 (2021-10-19)

## Changed
FSM is no longer a concept within kee-frame. We delegate to re-statecharts and glimt (for HTTP FSMs). APIs are the same,
but they moved out of this project. Kee-frame FSM APIs now show a warning in browser console when used. They will be 
removed in 1.4.0.

## Added
Builds on CircleCI. Releasing new versions is as simple as tagging in github.

# 1.3.1 (2021-10-19)

Just a minor release to fix a couple of test failures in previous one.

# 1.3.0 (2021-10-19)

## Added
- `case-route` as a more idiomatic case-like alternative to `switch-route`
- `case-fsm` that partially matches FSM states against views
- `with-fsm` as a convenient component-scoped FSM

## Fixed
- https://github.com/ingesolvoll/kee-frame/issues/103
- https://github.com/ingesolvoll/kee-frame/issues/104

## Changed
- Another iteration on FSM APIs, some breaking changes.

# 1.2.0 (2021-10-11 /  4a53132)

## Added
- Integration with https://github.com/lucywang000/clj-statecharts in `kee-frame.fsm.beta`.

## Changed
- Migrated from `leiningen` and `figwheel` to `deps` and `shadow-cljs`
- [BREAKING] Controllers are now restricted to only returning re-frame dispatch
  vectors. Previously they could return FSM declarations which would implicitly start the FSM.
  That is considered unnecessary implicit magic, as there is not much extra boilerplate in just dispatching
  to the FSM starter.
- `kee-frame.fsm.alpha` is now deprecated, and will be removed in future versions.
