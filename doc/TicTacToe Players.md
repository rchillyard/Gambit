# TicTacToe Players — Design Document

## Overview

This document captures the design decisions behind the TicTacToe player
implementations in the `claude` package: MENACE (reinforcement learning),
and PerfectPlayer (minimax via Visitor).

---

## File Structure

| File | Contents |
|------|----------|
| `Matchbox.scala` | `Matchbox` — weighted bead selection and reward/penalise logic |
| `Matchboxes.scala` | `Matchboxes` — D4-symmetric registry; `MatchResult` |
| `MenacePlayer.scala` | `Player`, `MenacePlayer`, `RandomPlayer`, `HeuristicPlayer`, `GameRunner`, `GameResult`, `GameStats` |
| `PerfectPlayer.scala` | `PerfectPlayer` — minimax via Visitor post-order DFS |
| `TicTacToeUtils.scala` | `TicTacToeUtils` — shared utility methods |

---

## Matchbox

A `Matchbox` holds a `Map[Int, Int]` from cell index (0..8, row-major) to bead
count. It is immutable — all operations return a new `Matchbox`.

### Weighted Selection

`select(random)` samples a move proportional to bead counts using a single-pass
`scanLeft` over the entries. The total bead count is computed once; a random integer
in `[0, total)` is drawn; the scan accumulates weights until the running total
exceeds the draw.

### Reward and Penalise

After a game, `reward(cell)` adds `winDelta` (default 3) beads to the played cell,
and `penalise(cell)` removes `lossDelta` (default 1) beads, floored at `beadFloor`
(default 1) so a move is never permanently eliminated. Draws produce no update.

### Initial Beads

Each open cell starts with `initialBeads = 4` beads, matching the original MENACE
machine's opening configuration.

---

## Matchboxes and D4 Symmetry

### Motivation

A TicTacToe board has up to 8 symmetrically equivalent orientations under the
dihedral group D4 (4 rotations × 2 for reflection). Without symmetry reduction,
MENACE would need ~5,000 matchboxes; with it, ~765 equivalence classes suffice,
and learning is roughly 8× faster.

### Canonical Form

The canonical form of a board is the minimum `Board.value` over all 8 D4 transforms.
The minimum is arbitrary but deterministic — any fixed choice from the orbit works.
The 8 transforms are composed from `TicTacToeOps.rotateBoard` (90° CW) and
`TicTacToeOps.transposeBoard`.

### The Coordinate Space Problem

The central design challenge is that the `Matchboxes` registry stores beads indexed
by **canonical cell indices**, but callers (players, the game runner) operate in
**original board orientation**. Three operations must each use the correct transform
direction:

| Operation | Direction | Method |
|-----------|-----------|--------|
| Create matchbox | original → canonical | `canonicalMatchbox` via forward transform |
| Record a played move for update | original → canonical | `transformCell` via inverse of forward transform |
| Return a selected move to caller | canonical → original | `selectMove` via inverse transform |

### d4CellPerms — Empirical Derivation

Cell permutations for each D4 transform are derived empirically by applying each
board transform to a board with a single X at each cell position and observing where
it lands. This guarantees consistency with `TicTacToeOps` regardless of the exact
implementation of `rotateBoard`/`transposeBoard`, and avoids fragile assumptions
about their geometric meaning.

Specifically, `transposeBoard` in `TicTacToeOps` is implemented as
`rotateBoard(hFlip(x))` — not a pure matrix transpose — so hand-written geometric
formulas for cell permutations were incorrect. The empirical approach is immune to
this.

### The Key Bug

During development a subtle coordinate space mismatch caused `selectMove` to return
canonical cell indices to the caller as if they were original-orientation indices.
This caused `playX`/`play0` to occasionally write into already-occupied cells
(producing bit pattern `11` = "corrupted empty"), which rendered as `.` and counted
as open, creating an infinite loop.

The fix: `selectMove` maps the sampled canonical cell back to original orientation
using `d4CellPerms(d4InverseIndex(transform))(canonCell)` before returning.

### Note on X/O Symmetry

`exchangeBoard` (swapping X and O bits) is not included in canonicalization. X
always moves first so X-positions and O-positions carry different learning signals
and should not share matchboxes. Including exchange symmetry would halve the
matchbox count further but would conflate the two players' perspectives.

---

## Players

### MenacePlayer

Consults `Matchboxes.selectMove` to choose a move, records `(position, cell)` pairs
in a history list, and back-propagates the game result via `Matchboxes.update` after
`gameOver` is called. History is cleared after each game. All cell indices in history
are in original board orientation.

### RandomPlayer

Selects uniformly at random from open cells. Useful as a training opponent and
baseline.

### HeuristicPlayer

Greedily selects the move leading to the highest-heuristic successor state, using
the existing `TicTacToeState$` heuristic. Useful as a stronger baseline.

### PerfectPlayer

Builds a complete minimax score map over the TicTacToe game DAG on first use,
via a single post-order DFS using Visitor's `Traversal.dfs(DfsOrder.Post)`.

**Build phase:**

1. A mutable `scoreMap: mutable.Map[TicTacToe, Int]` is allocated.
2. A `given Evaluable[TicTacToe, Int]` closes over `scoreMap`. For terminal
   positions it scores directly (+1 X wins, -1 O wins, 0 draw). For internal
   nodes it reads children's scores from `scoreMap` (already populated in
   post-order) and aggregates by max (X to move) or min (O to move).
3. A `given GraphNeighbours[TicTacToe]` delegates to `State.getStates`.
4. `Traversal.dfs` is called once from `TicTacToe.start`. The default
   `given VisitedSet[TicTacToe]` (backed by `TicTacToe.equals` on board value)
   ensures each of the ~5,000 reachable positions is evaluated exactly once —
   the game tree is traversed as a DAG, not a tree.

**Play phase:**

`chooseMove` checks `isGoal` first (returning `None` for terminal positions),
then looks up all successor scores and picks the max (X to move) or min (O to move).

**Minimax convention:**

`TicTacToe.player` is true when X just moved (odd number of moves played). So
`xToMove = !ttt.player` — if X just moved, it is now O's turn.

---

## GameRunner

Plays a sequence of games between two `Player` instances. After each move it checks
`State.isGoal` to detect wins and draws. After each game it notifies both players
via `gameOver` so they can update their internal state (e.g. MENACE back-propagation).
Returns `GameStats` with win/loss/draw counts and percentages.

---

## TicTacToeUtils

Shared utility methods extracted to avoid duplication across player implementations:

- `cellFromDiff(diff: Int): Int` — given the XOR of two board values where exactly
  one cell differs, returns the flat cell index (0..8, row-major) of the changed cell.
  Used by `HeuristicPlayer` and `PerfectPlayer` to recover the move from a board diff.

---

## Learning Dynamics

After training against a `RandomPlayer` for ~2000 games, a `MenacePlayer` as X
should lose fewer than 20% of evaluation games. Training against `PerfectPlayer`
is harder but produces a stronger agent. With perfect play, X can always force at
least a draw, so a well-trained MENACE should converge toward a near-zero loss rate.

### Tuning Parameters

| Parameter | Default | Effect |
|-----------|---------|--------|
| `initialBeads` | 4 | Higher = more exploration early |
| `winDelta` | 3 | Higher = stronger reinforcement of wins |
| `lossDelta` | 1 | Higher = faster pruning of losing moves |
| `beadFloor` | 1 | Higher = more sustained exploration |

---

## Future Work

- **Self-play** — two `MenacePlayer` instances sharing or not sharing a `Matchboxes`
  registry; interesting to compare convergence rates.
- **X/O symmetry** — including `exchangeBoard` in canonicalization to halve matchbox
  count; requires careful handling of the learning signal.
- **Generalisation** — `Player` and `GameRunner` are currently tictactoe-specific
  due to `Prototype`. Making them generic in `[S, M]` (state, move) would allow
  all player types to be applied to other games.
- **Persistence** — serialising `Matchboxes` so a trained MENACE can be saved and
  restored between sessions.
- **Visualisation** — rendering bead distributions across matchboxes to illustrate
  what the machine has learned.