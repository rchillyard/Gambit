# DecisionTree Players — Design Document

## Overview

This document captures the design decisions behind the game-playing framework
in DecisionTree, covering the generic typeclasses in `moves` and the TicTacToe
player implementations in `examples.tictactoe`.

---

## File Structure

### `com.phasmidsoftware.decisiontree.game` (generic framework)

| File | Contents |
|------|----------|
| `State.scala` | `State[P, S]`, `Transition[P, S]`, `Move[P, S]` — game state typeclass |
| `Game.scala` | `Game[S, M, Pl]` — game mechanics typeclass |
| `Player.scala` | `Player[S, M, Pl]` — player strategy trait; `GameResult[Pl]`, `MatchResult[Pl]` |
| `GameRunner.scala` | `GameRunner[P, S, M, Pl]` — generic game execution |
| `Evaluator.scala` | `Evaluator[S]` — tree search evaluator (attic) |

### `com.phasmidsoftware.decisiontree.examples.tictactoe`

| File | Contents |
|------|----------|
| `TicTacToe.scala` | `TicTacToe`, `Board` — bitboard state; `TicTacToeState$` |
| `TicTacToeOps.java` | Low-level bit manipulation (rotate, transpose, play, render) |
| `TicTacToeUtils.scala` | `TicTacToeUtils` — shared utility (`cellFromDiff`) |
| `Matchbox.scala` | `Matchbox` — weighted bead selection and reward/penalise |
| `Matchboxes.scala` | `Matchboxes` — D4-symmetric registry; `BeadResult` |
| `MenacePlayer.scala` | `MenacePlayer`, `RandomPlayer`, `HeuristicPlayer`; `given tictactoeGame`; `TicTacToeGameRunner` |
| `PerfectPlayer.scala` | `PerfectPlayer` — minimax via Visitor post-order DFS |

---

## Generic Framework (`moves`)

### Typeclass Hierarchy

```
State[P, S]          — game state: legal moves, goal detection, heuristic
Game[S, M, Pl]       — game mechanics: start, applyMove, nextPlayer, winner
Player[S, M, Pl]     — player strategy: chooseMove, gameOver
GameRunner[P,S,M,Pl] — execution: driven by State and Game givens
```

### Game[S, M, Pl]

The central new typeclass. Separates game *rules* from game *strategy*:

- `start: S` — the initial state
- `startingPlayer: Pl` — who moves first
- `players: Seq[Pl]` — all player identities in turn order
- `applyMove(s, m, pl): S` — transition function
- `nextPlayer(s, pl): Pl` — whose turn is next
- `winner(s, current): GameResult[Pl]` — default zero-sum two-player winner;
  override for multiplayer or non-zero-sum games (e.g. bridge trick counts)

### Player[S, M, Pl]

A trait (not a typeclass) because players have instance identity and mutable state
(e.g. `MenacePlayer` holds a history list). Two methods:

- `chooseMove(s, random): Option[M]` — returns `None` for terminal positions
- `gameOver(result: GameResult[Pl], me: Pl): Unit` — default no-op; override
  for learning or logging. The full `GameResult[Pl]` is provided so players can
  inspect all scores, not just their own.

### GameResult[Pl] and MatchResult[Pl]

```scala
type GameResult[Pl] = Map[Pl, Int]   // one game: player → score
case class MatchResult[Pl](results: Seq[GameResult[Pl]])  // many games
```

Scores are typically -1 (loss), 0 (draw), +1 (win) for two-player zero-sum games.
For trick-taking games like bridge, scores could represent tricks won per side.
`MatchResult` provides `winsFor`, `lossesFor`, `drawsFor`, `total`, and `summary`.

**Vocabulary:**
- **Move** — a single transition (one card played, one cell filled)
- **Game** — a complete play from start to terminal (one deal, one board)
- **Match** — a series of games (a rubber, a session, a training run)

This maps cleanly onto bridge: a Trick is four Moves; a Game is 13 Tricks (one deal);
a Match is multiple deals with cumulative scoring.

### GameRunner[P, S, M, Pl]

Driven entirely by `given State[P, S]` and `given Game[S, M, Pl]`. Takes only a
`playerMap: Map[Pl, Player[S, M, Pl]]` and a `Random`. The `loop` function is
tail-recursive; `playGames(n)` returns a `MatchResult[Pl]`.

---

## TicTacToe Wiring

### given tictactoeGame

The single place where TicTacToe-specific mechanics are defined:

```scala
given tictactoeGame(using State[Board, TicTacToe]): Game[TicTacToe, Int, Boolean] with
  def start          = TicTacToe.start
  def startingPlayer = true            // X moves first
  def players        = Seq(true, false)
  def applyMove(ttt, cell, isX) = ...  // delegates to playX / play0
  def nextPlayer(ttt, current)  = !current
```

`Pl = Boolean` (true = X, false = O), `M = Int` (flat cell index 0..8).

### TicTacToeGameRunner

A two-line factory that builds a `GameRunner[Board, TicTacToe, Int, Boolean]`
from two players and a `Random`, relying on the `given tictactoeGame` and
`given TicTacToeState$` in scope.

---

## MENACE

### Matchbox

An immutable `Map[Int, Int]` from canonical cell index to bead count.
`select(random)` uses weighted sampling via `scanLeft`.
`reward`/`penalise` return new instances with updated counts, floored at
`beadFloor = 1` so no move is permanently eliminated.

### Matchboxes and D4 Symmetry

The registry maps canonical board values to `Matchbox` instances.
Canonicalization uses the 8-element dihedral group D4 (4 rotations × 2 reflections),
reducing ~5,000 reachable positions to ~765 equivalence classes (~8× learning speedup).

**Coordinate space invariant** — three operations must use consistent transform directions:

| Operation | Direction | Method |
|-----------|-----------|--------|
| Create matchbox open cells | original → canonical | `canonicalMatchbox` via forward transform |
| Record played move for update | original → canonical | `transformCell` via inverse transform |
| Return selected move to caller | canonical → original | `selectMove` via inverse transform |

**d4CellPerms** are derived empirically from `TicTacToeOps` — applying each board
transform to a single-X board and observing where it lands. This avoids geometric
assumptions: `transposeBoard` is `rotateBoard(hFlip(x))`, not a pure matrix transpose.

**BeadResult** (`Win`/`Loss`/`Draw`) is the MENACE-internal result type, distinct
from the generic `GameResult[Pl]`. `MenacePlayer.gameOver` maps `GameResult.score`
(+1/0/-1) to `BeadResult` before calling `Matchboxes.update`.

**The key bug** — during development, `selectMove` returned canonical cell indices
to the caller. `playX`/`play0` then wrote into already-occupied cells, producing
bit pattern `11` ("corrupted empty") which rendered as `.` and caused an infinite
loop. Fix: `selectMove` applies the inverse D4 transform before returning.

### MenacePlayer

Extends `Player[TicTacToe, Int, Boolean]`. Records `(position, cell)` history
during play; back-propagates via `Matchboxes.update` in `gameOver`; clears history
after each game.

---

## PerfectPlayer

Extends `Player[TicTacToe, Int, Boolean]`. Builds a complete minimax score map
over the TicTacToe game DAG on first use via `Traversal.dfs(DfsOrder.Post)`.

**Build phase** — `given Evaluable[TicTacToe, Int]` closes over a mutable score map;
post-order guarantees children are scored before parents; terminal positions score
directly (+1/-1/0); internal nodes aggregate via max (X to move) or min (O to move).

**Play phase** — checks `isGoal` first (returns `None` for terminal positions),
then picks the successor with the best score for the current player.
`TicTacToe.player` is true when X just moved, so `xToMove = !ttt.player`.

**DAG traversal** — `given VisitedSet[TicTacToe]` (backed by board-value equality)
ensures each of the ~5,000 reachable positions is scored exactly once.

---

## Learning Dynamics

After ~2000 training games against `RandomPlayer`, `MenacePlayer` as X loses fewer
than 20% of evaluation games. Training against `PerfectPlayer` is harder but
produces a stronger agent.

### Tuning Parameters

| Parameter | Default | Effect |
|-----------|---------|--------|
| `initialBeads` | 4 | Higher = more exploration early |
| `winDelta` | 3 | Higher = stronger reinforcement of wins |
| `lossDelta` | 1 | Higher = faster pruning of losing moves |
| `beadFloor` | 1 | Higher = more sustained exploration |

---

## Future Work

- **Self-play** — two `MenacePlayer` instances with shared or separate registries
- **X/O symmetry** — `exchangeBoard` canonicalization to halve matchbox count
- **Persistence** — serialising `Matchboxes` for trained agent reuse
- **Visualisation** — bead distribution rendering
- **Chess** — `given chessGame: Game[ChessState, ChessMove, Boolean]`; minimax
  with alpha-beta pruning
- **Bridge** — `given bridgeGame: Game[BridgeState, Card, Seat]`; four-player,
  `winner` returns tricks per side; MCTS with determinization for hidden information