# Gambit — Version History

## 1.1.0 (2026-05-12)

### New features

- `AlphaBetaPlayer[P, S, M, Pl]` — generic minimax player with alpha-beta pruning
  and heuristic-based move ordering; configurable depth
- `Tournament[P, S, M, Pl]` / `Contestant[S, M, Pl]` — generic round-robin league
  with 3-1-0 scoring and formatted league table
- `Connect4Tournament` — concrete tournament runner for Connect Four with
  `main` entry point; accepts `gamesPerPairing` and `seed` as command-line args
- `GambitConfig` — typed configuration loader backed by `application.conf`;
  all player parameters (depths, iteration counts, exploration constant) are
  configurable without recompilation
- MCTS tree reuse — `MCTSPlayer` retains the chosen subtree between `chooseMove`
  calls, carrying forward accumulated visit and win counts; `gameOver` resets
  state between games

### Infrastructure

- `it` source set for slow functional tests (separate from unit tests)
- GitHub Pages API documentation at https://rchillyard.github.io/Gambit/latest/api/
- `CODE_OF_CONDUCT.md` and `CONTRIBUTING.md` added
- `GamePlayingDesign.md` updated to cover AlphaBeta, Tournament, tree reuse,
  and configuration
- CircleCI config split into fast (unit) and slow (integration) test jobs

---

## 1.0.8 (2026-05-11)

### New features

- `MCTSPlayer[P, S, M, Pl]` — generic Monte Carlo Tree Search player with UCB1
  selection and random rollout simulation
- `AlphaBetaPlayer` bugs fixed: `Double.MinValue` as initial alpha corrected to
  `-Double.MaxValue`; heuristic sign at leaf nodes corrected for minimizing player
- `MatchResult.summary` — formatted match summary string
- `Connect4GameRunner` factory object
- `taggedAs Slow` applied to all multi-game tests; CircleCI excludes slow tests
  on feature branches

---

## 1.0.7 (2025)

- Renamed project from DecisionTree to Gambit
- Generalized `Player`, `GameRunner` with `Game[S, M, Pl]` typeclass
- Connect Four implementation: bitboard state, heuristic, demo

---

## 1.0.6 (2025)

- `HeuristicPlayer` tests, `TicTacToeOpsTest`
- Terminal position handling fixes

---

## 1.0.5 and earlier

- TicTacToe implementation with MENACE reinforcement learning
- `PerfectPlayer` via Visitor post-order minimax DFS
- D4 symmetry reduction in `Matchboxes`
- Generic `State[P, S]` typeclass and `GameRunner`