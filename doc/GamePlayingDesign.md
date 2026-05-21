# Gambit — Game Playing Design Document

## Overview

This document captures the design decisions behind the game-playing framework
in Gambit, covering the generic typeclasses in `game`, the TicTacToe
and Connect Four implementations, and the player types including MCTS and
AlphaBeta.

---

## File Structure

### `com.phasmidsoftware.gambit.game` (generic framework)

| File | Contents |
|------|----------|
| `State.scala` | `State[P, S]`, `Transition[P, S]`, `Move[P, S]` — game state typeclass |
| `Game.scala` | `Game[S, M, Pl]` — game mechanics typeclass |
| `Player.scala` | `Player[S, M, Pl]`; `GameResult[Pl]`, `MatchResult[Pl]` |
| `GameRunner.scala` | `GameRunner[P, S, M, Pl]` — generic game execution |
| `AlphaBetaPlayer.scala` | `AlphaBetaPlayer[P, S, M, Pl, K]` — minimax with alpha-beta pruning |
| `MCTSPlayer.scala` | `MCTSPlayer[P, S, M, Pl]`, `MCTSNode[S, M, Pl]` — Monte Carlo Tree Search |
| `Tournament.scala` | `Tournament[P, S, M, Pl]`, `Contestant[S, M, Pl]` — round-robin league |

### `com.phasmidsoftware.gambit.examples.tictactoe`

| File | Contents |
|------|----------|
| `TicTacToe.scala` | `TicTacToe`, `Board` — bitboard state; `TicTacToeState$` |
| `TicTacToeOps.java` | Low-level bit manipulation (rotate, transpose, play, render) |
| `TicTacToeUtils.scala` | `cellFromDiff` — shared utility |
| `Matchbox.scala` | `Matchbox` — weighted bead selection and reward/penalise |
| `Matchboxes.scala` | `Matchboxes` — D4-symmetric registry; `BeadResult` |
| `MenacePlayer.scala` | `MenacePlayer`, `RandomPlayer`, `HeuristicPlayer`; `given tictactoeGame`; `TicTacToeGameRunner` |
| `PerfectPlayer.scala` | `PerfectPlayer` — minimax via Visitor post-order DFS |
| `TicTacToeDemo.scala` | `@main TicTacToeDemo` — home/away round-robin demo |

### `com.phasmidsoftware.gambit.examples.connect4`

| File | Contents |
|------|----------|
| `Connect4.scala` | `Connect4` — column-major bitboard state with gravity |
| `Connect4State.scala` | `Connect4State` — `State[Connect4, Connect4]` instance |
| `Connect4Game.scala` | `RandomPlayer`, `HeuristicPlayer`; `given connect4Game`; `Connect4GameRunner` |
| `Connect4Tournament.scala` | `Connect4Tournament` — concrete tournament runner with `main` |
| `Connect4Demo.scala` | `@main Connect4Demo` — home/away matchup demo |

---

## Generic Framework

### Typeclass Hierarchy

```
State[P, S]              — game state: legal moves, goal detection, heuristic,
                           isMaximizing, leafValue
Game[S, M, Pl]           — game mechanics: start, moves, applyMove, nextPlayer,
                           currentPlayer, winner
Player[S, M, Pl]         — player strategy: chooseMove, gameOver
GameRunner[P, S, M, Pl]  — execution: driven by State and Game givens
AlphaBetaPlayer[P,S,M,Pl,K] — generic minimax player with alpha-beta pruning
MCTSPlayer[P, S, M, Pl]  — generic MCTS player with tree reuse
Tournament[P, S, M, Pl]  — round-robin league with 3-1-0 scoring
```

### State[P, S]

The existing Visitor-derived typeclass, extended with:

- `isFirstPlayerToMove(s: S): Boolean` — `sequence(s) % 2 == 0`; true when
  it is the first player's turn to move.

- `isMaximizing(s: S, currentMaximizing: Boolean): Boolean` — determines whether
  the player to move at `s` is the maximizing player. The default implementation
  returns `!currentMaximizing` (strict alternation, suitable for TicTacToe and
  Connect Four). Games where the same player can move consecutively (e.g. bridge,
  where the trick winner leads the next trick) must override this method using
  `game.currentPlayer(s)`.

- `leafValue(s: S, maximizing: Boolean): Double` — the value to return at a
  terminal or depth-limited node. The default implementation follows the negamax
  convention: `if maximizing then -heuristic(s) else heuristic(s)`, which
  assumes `heuristic` is positive when the player who just moved is doing well.
  Games that use an absolute heuristic (e.g. always positive for NS in bridge)
  must override this to return `heuristic(s)` directly.

### Game[S, M, Pl]

Separates game *rules* from game *strategy*:

- `start: S` — the initial state
- `startingPlayer: Pl` — who moves first
- `players: Seq[Pl]` — all player identities in turn order
- `moves(s: S): Seq[M]` — legal moves as raw values (used by MCTS and players)
- `applyMove(s, m, pl): S` — transition function
- `nextPlayer(s, pl): Pl` — whose turn is next after `pl` moves
- `currentPlayer[P](s)(using State[P,S]): Pl` — default implementation via
  `isFirstPlayerToMove`; returns `startingPlayer` on even sequence, the other
  player on odd. Override for games with non-alternating play (e.g. bridge).
- `winner(s, current): GameResult[Pl]` — default zero-sum two-player winner;
  override for multiplayer or non-zero-sum games

### Player[S, M, Pl]

A trait (not a typeclass) because players have instance identity and mutable
state. Two methods:

- `chooseMove(s, random): Option[M]` — returns `None` for terminal positions
- `gameOver(result: GameResult[Pl], me: Pl): Unit` — default no-op; overridden
  by stateful players (e.g. `MenacePlayer`, `MCTSPlayer`) to reset or update
  internal state between games

### GameResult[Pl] and MatchResult[Pl]

```scala
type GameResult[Pl] = Map[Pl, Int]                        // one game
case class MatchResult[Pl](results: Seq[GameResult[Pl]])  // many games
```

Scores are -1/0/+1 for two-player zero-sum games. For trick-taking games like
bridge, scores could represent tricks won per side.

**Vocabulary:**
- **Move** — a single transition (one card played, one cell filled)
- **Game** — complete play from start to terminal (one deal, one board)
- **Match** — a series of games (rubber, session, training run)
- **Trick** (bridge) — four Moves completing one trick

### GameRunner[P, S, M, Pl]

Driven entirely by `given State[P, S]` and `given Game[S, M, Pl]`. Takes only
`playerMap: Map[Pl, Player[S, M, Pl]]` and a `Random`. Tail-recursive `loop`;
`playGames(n)` returns a `MatchResult[Pl]`.

---

## AlphaBeta

### AlphaBetaPlayer[P, S, M, Pl, K]

Generic minimax player with alpha-beta pruning to a configurable depth. The
fifth type parameter `K` is the transposition table key type; use `Any` (via
the companion `apply`) when no caching is needed.

**Heuristic convention** — `heuristic(s)` follows whatever convention the
`State` typeclass defines. At leaf nodes, `state.leafValue(s, maximizing)` is
called — the default negamax convention negates when the minimizing player just
moved, but games with an absolute heuristic override `leafValue` to return
`heuristic(s)` directly.

**`isMaximizing` delegation** — rather than hardcoding `!maximizing` at each
recursive call, `AlphaBetaPlayer` calls `state.isMaximizing(next, maximizing)`
to determine the next node's maximizing flag. This correctly handles games where
the same player can move twice in a row (non-alternating turn order).

**Alpha-beta bounds** — initial alpha is `-Double.MaxValue` (not
`Double.MinValue`, which is the smallest *positive* double ~5e-324).

**Move ordering** — successors sorted by heuristic before recursing (highest
first for maximizer, lowest first for minimizer), maximising the probability
of early pruning and approaching best-case O(b^(d/2)) node count.

**Transposition table** — an optional `keyFn: Option[S => K]` maps a state to
a cache key. Three caching modes are available via `depthTranches` and
`reuseDeeper`. **Important:** the naive transposition table can produce incorrect
results because cached values computed under one set of alpha-beta bounds may
be reused in a context with different bounds, poisoning the search. The correct
fix (Issue #14) is to store TT flags (exact / lower bound / upper bound) with
each cached entry and only reuse entries whose flag is compatible with the
current alpha-beta window. Until Issue #14 is implemented, pass `keyFn = None`
for correctness.

---

## MCTS

### MCTSNode[S, M, Pl]

Mutable tree node. Fields: `state`, `move`, `movedBy`, `visits`, `wins`,
`children`, `untriedMoves`. No parent reference — backpropagation uses an
explicit path stack accumulated during selection, avoiding circular references
and the GC/equality issues that back-references cause in tree structures.

### MCTSPlayer[P, S, M, Pl]

Generic MCTS player implementing the standard four-phase loop:

1. **Selection** — descend by UCB1, accumulating path and current player
2. **Expansion** — add one random untried child
3. **Simulation** — random rollout via `game.moves` to terminal state
4. **Backpropagation** — update `visits` and `wins` along the path;
   each node's win score credited to `movedBy`

**UCB1:** `wins/visits + C * sqrt(ln(parentVisits) / visits)` where `C = √2`.

**Most-visited child** criterion for final move selection — more robust than
highest win-rate under finite simulations.

**Tree reuse** — the chosen child subtree is retained between `chooseMove`
calls via `retainedRoot`. On the next call, `advanceTree` searches the retained
node's children for the opponent's reply state:

- **Cache hit** — the matching grandchild becomes the new root, carrying
  forward all accumulated visits and wins.
- **Cache miss** — opponent played an unexplored line; a fresh root is created.
  `gameOver` resets `retainedRoot = None` so tree state does not leak between
  games when the same instance is reused across a match.

Tree matching uses `==` on `S`; requires meaningful equality (satisfied by
`case class`).

**`me` parameter** — the player identity this instance represents; used in
backpropagation to correctly credit wins.

**`currentPlayer`** — uses `game.currentPlayer(s)(using state)` to determine
who moves at the root, correctly handling mid-game positions.

### Future Upgrades

- **Actor-based parallelism** — move the mutable tree into an Akka/Pekko actor;
  multiple rollout worker actors submit simulation results concurrently (root
  parallelization), giving near-linear speedup with core count
- **Heuristic rollouts** — replace pure random simulation with heuristic-guided
  playout for stronger play

---

## Tournament

### Tournament[P, S, M, Pl]

Generic round-robin tournament. Every ordered pair of contestants plays
`gamesPerPairing` games, so each contestant takes the first-player role
equally often. Driven by the same `given State[P, S]` and `given Game[S, M, Pl]`
as `GameRunner`.

**Scoring** — standard football 3-1-0:

- Win  = 3 points
- Draw = 1 point
- Loss = 0 points

**Standings** sorted by points descending, then goal difference (wins − losses),
then wins. `leagueTable` returns a formatted string; `standings` returns raw
tuples for programmatic use.

**`Contestant[S, M, Pl]`** — pairs a display name with a `Player` instance.
The name is purely for the league table; players are unaware of the tournament
context and optimise only for winning individual games.

### Connect4Tournament

Concrete runner in the `connect4` package. Enters six player types:
`Random`, `Heuristic`, `AlphaBeta(d=4)`, `AlphaBeta(d=6)`, `MCTS(i=200)`,
`MCTS(i=500)`. Runnable as a JVM main:

```
sbt "runMain com.phasmidsoftware.gambit.examples.connect4.Connect4Tournament [gamesPerPairing] [seed]"
```

Both arguments are optional. `gamesPerPairing` defaults to 6;
`seed` defaults to `System.currentTimeMillis()` so repeated runs produce
different results unless a seed is specified explicitly.

Sample output (6 games per pairing, seed 42):

```
Player             P     W     D     L    GD   Pts
--------------------------------------------------
1. AlphaBeta(d=6)  60    48     1    11   +37   145
2. AlphaBeta(d=4)  60    45     1    14   +31   136
3. MCTS(i=500)     60    37     2    21   +16   113
4. MCTS(i=200)     60    31     1    28    +3    94
5. Heuristic       60    16     1    43   -27    49
6. Random          60     0     0    60   -60     0
```

---

## TicTacToe

### given tictactoeGame

```scala
given tictactoeGame(using State[Board, TicTacToe]): Game[TicTacToe, Int, Boolean] with
  def start          = TicTacToe.start
  def startingPlayer = true
  def players        = Seq(true, false)
  def moves(ttt)     = ttt.open.map { case (r,c) => r * TicTacToe.size + c }
  def applyMove(...) = state.construct(ttt.playX/play0(...))
  def nextPlayer(..) = !current
```

### Player Types

| Player | Strategy |
|--------|----------|
| `RandomPlayer` | Uniform random over open cells |
| `HeuristicPlayer` | `maxBy(heuristic)` over successors |
| `MenacePlayer` | MENACE bead reinforcement learning |
| `PerfectPlayer` | Full minimax via Visitor post-order DFS |
| `AlphaBetaPlayer` | Minimax with alpha-beta pruning (generic) |
| `MCTSPlayer` | Monte Carlo Tree Search with tree reuse (generic) |

### MENACE

**Matchbox** — immutable `Map[Int, Int]` (canonical cell → bead count).
Weighted sampling via `scanLeft`. `reward`/`penalise` floored at `beadFloor=1`.

**Matchboxes** — D4 symmetry canonicalization reduces ~5,000 positions to ~765
equivalence classes (~8× speedup). `selectMove` returns original-orientation
cell; `update` accepts original-orientation cell and transforms internally.

**Coordinate space invariant:**

| Operation | Direction | Method |
|-----------|-----------|--------|
| Create matchbox | original → canonical | `canonicalMatchbox` (forward) |
| Record move for update | original → canonical | `transformCell` (inverse) |
| Return move to caller | canonical → original | `selectMove` (inverse) |

**d4CellPerms** derived empirically from `TicTacToeOps` — immune to the fact
that `transposeBoard` is `rotateBoard(hFlip(x))`, not a pure matrix transpose.

**BeadResult** (`Win`/`Loss`/`Draw`) is MENACE-internal, distinct from
`GameResult[Pl]`. `MenacePlayer.gameOver` maps score (+1/0/-1) to `BeadResult`.

### PerfectPlayer

Builds a complete minimax score map via `Traversal.dfs(DfsOrder.Post)` on first
use. `given VisitedSet[TicTacToe]` ensures DAG traversal (~5,000 positions scored
once). Heuristic convention: positive = good for player who just moved (`s.player`);
`maxBy(heuristic)` in `chooseMove`. Uses `state.isFirstPlayerToMove` to determine
whose turn it is.

---

## Connect Four

### Connect4 Bitboard

Column-major layout: bit index = `col * 7 + row` (row 0 = bottom).
Sentinel bits at `col * 7 + 6` prevent horizontal win detection wrapping.
Win detection uses four bitwise AND/shift pairs (horizontal, vertical, two diagonals).

### Connect4State

`State[Connect4, Connect4]` (P = S = Connect4; `construct = _._1`).

**Heuristic convention** — from the perspective of whoever just moved (`s.player`):

- Terminal: `±Double.MaxValue`
- Non-terminal: window scoring (3-in-window = 10, 2-in-window = 1) + centre bonus
- Windows enumerated via lowest-set-bit iteration over unblocked positions

**`isFirstPlayerToMove`** inherited from `State`; correct since `sequence =
bitCount(xBits) + bitCount(oBits)`.

### given connect4Game

```scala
given connect4Game(using State[Connect4, Connect4]): Game[Connect4, Int, Boolean] with
  def moves(s)           = s.open          // open columns 0..6
  def applyMove(s,col,x) = s.play(col, x)
  def nextPlayer(s,cur)  = !cur
```

### Player Types

| Player | Strategy |
|--------|----------|
| `RandomPlayer` | Uniform random over open columns |
| `HeuristicPlayer` | `maxBy(heuristic)` over successors (one-ply greedy) |
| `AlphaBetaPlayer` | Minimax with alpha-beta pruning (generic) |
| `MCTSPlayer` | Monte Carlo Tree Search with tree reuse (generic) |

---

## Heuristic Convention

Both `TicTacToe` and Connect Four follow the negamax convention:

> `heuristic(s)` is positive when the player who **just moved** to reach `s`
> is doing well. `HeuristicPlayer.chooseMove` uses `maxBy(heuristic)` over
> successors — since each successor was reached by the current player moving,
> the maximum heuristic successor is the best move.

`AlphaBetaPlayer` accounts for this at leaf nodes via `state.leafValue(s, maximizing)`,
which by default negates the heuristic when the minimizing player just moved,
ensuring the returned value is always from `me`'s perspective regardless of
search depth.

Games that maintain an absolute heuristic (always from one side's perspective,
e.g. always positive for NS in bridge) override `leafValue` to return
`heuristic(s)` directly, and override `isMaximizing` to compute the next
player from the game state rather than assuming strict alternation.

---

## Demo Programs

`TicTacToeDemo` and `Connect4Demo` are `@main` programs that play home-and-away
matches between all player type pairs, printing the board and heuristic score
after each move. Each match is labelled HOME/AWAY; the result includes the
player name, e.g. `Result: X (MCTS(500)) wins!`.

The `playTicTacToeDemo` and `playConnect4Demo` functions return `Option[Boolean]`
(the `isGoal` result) rather than `Unit`, enabling unit testing via
`TicTacToeDemoSpec` and `Connect4DemoSpec`.

---

## API Documentation

Scaladoc is published to GitHub Pages via `sbt-ghpages`:

```
https://rchillyard.github.io/Gambit/latest/api/
```

To update after significant changes:
```
sbt ghpagesPushSite
```

---

## Future Work

- **Issue #14: TT flags** — implement exact/lower-bound/upper-bound flags in the
  transposition table so cached values are only reused when their bounds are
  compatible with the current alpha-beta window; re-enable caching for bridge
- **Actor-based parallel rollouts** (Akka/Pekko) for MCTS
- **Heuristic rollouts** for stronger MCTS play
- **MENACE self-play** — two instances with shared or separate registries
- **MENACE X/O symmetry** — `exchangeBoard` to halve matchbox count
- **MENACE persistence** — serialise registry for trained agent reuse
- **Chess** — `given chessGame: Game[ChessState, ChessMove, Boolean]`