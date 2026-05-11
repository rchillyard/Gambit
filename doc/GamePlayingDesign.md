# Gambit — Game Playing Design Document

## Overview

This document captures the design decisions behind the game-playing framework
in Gambit, covering the generic typeclasses in `game`, the TicTacToe
and Connect Four implementations, and the player types including MCTS.

---

## File Structure

### `com.phasmidsoftware.gambit.game` (generic framework)

| File | Contents |
|------|----------|
| `State.scala` | `State[P, S]`, `Transition[P, S]`, `Move[P, S]` — game state typeclass |
| `Game.scala` | `Game[S, M, Pl]` — game mechanics typeclass |
| `Player.scala` | `Player[S, M, Pl]`; `GameResult[Pl]`, `MatchResult[Pl]` |
| `GameRunner.scala` | `GameRunner[P, S, M, Pl]` — generic game execution |
| `MCTSPlayer.scala` | `MCTSPlayer[P, S, M, Pl]`, `MCTSNode[S, M, Pl]` — Monte Carlo Tree Search |

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
| `Connect4Demo.scala` | `@main Connect4Demo` — home/away matchup demo |

---

## Generic Framework

### Typeclass Hierarchy

```
State[P, S]            — game state: legal moves, goal detection, heuristic,
                         isFirstPlayerToMove
Game[S, M, Pl]         — game mechanics: start, moves, applyMove, nextPlayer,
                         currentPlayer, winner
Player[S, M, Pl]       — player strategy: chooseMove, gameOver
GameRunner[P, S, M, Pl] — execution: driven by State and Game givens
MCTSPlayer[P, S, M, Pl] — generic MCTS player
```

### State[P, S]

The existing Visitor-derived typeclass, extended with:

- `isFirstPlayerToMove(s: S): Boolean` — `sequence(s) % 2 == 0`; true when
  it is the first player's turn to move. Unambiguous: it is the player who
  will make the *next* move from `s`, NOT the player who made the last move.

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
  player on odd. Override for games with more than two players (e.g. bridge).
- `winner(s, current): GameResult[Pl]` — default zero-sum two-player winner;
  override for multiplayer or non-zero-sum games

### Player[S, M, Pl]

A trait (not a typeclass) because players have instance identity and mutable
state. Two methods:

- `chooseMove(s, random): Option[M]` — returns `None` for terminal positions
- `gameOver(result: GameResult[Pl], me: Pl): Unit` — default no-op

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

**`me` parameter** — the player identity this instance represents; used in
backpropagation to correctly credit wins. Must be set to `true` (X) when
playing as X and `false` (O) when playing as O.

**`currentPlayer`** — uses `game.currentPlayer(s)(using state)` to determine
who moves at the root, correctly handling mid-game positions.

### Future Upgrades

- **Tree reuse** — retain the subtree rooted at the chosen move between calls,
  avoiding redundant re-exploration of already-visited states
- **Actor-based parallelism** — move the mutable tree into an Akka/Pekko actor;
  multiple rollout worker actors submit simulation results concurrently (root
  parallelization), giving near-linear speedup with core count
- **Heuristic rollouts** — replace pure random simulation with heuristic-guided
  playout for stronger play

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
| `MCTSPlayer` | Monte Carlo Tree Search |

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
| `HeuristicPlayer` | `maxBy(heuristic)` over successors |
| `MCTSPlayer` | Monte Carlo Tree Search |

---

## Heuristic Convention

Both TicTacToe and Connect Four follow the same convention:

> `heuristic(s)` is positive when the player who **just moved** to reach `s`
> is doing well. `HeuristicPlayer.chooseMove` uses `maxBy(heuristic)` over
> successors — since each successor was reached by the current player moving,
> the maximum heuristic successor is the best move.

This was the source of several bugs during development when the convention
was inconsistently applied between `State.heuristic` and `Player.chooseMove`.

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

## Future Work

- **MCTS tree reuse** between moves
- **Actor-based parallel rollouts** (Akka/Pekko)
- **MENACE self-play** — two instances with shared or separate registries
- **MENACE X/O symmetry** — `exchangeBoard` to halve matchbox count
- **MENACE persistence** — serialise registry for trained agent reuse
- **Alpha-beta pruning** — for stronger Connect Four play (full minimax
  is intractable for the 7×6 board)
- **Chess** — `given chessGame: Game[ChessState, ChessMove, Boolean]`
- **Bridge** — `given bridgeGame: Game[BridgeState, Card, Seat]`;
  four-player; `winner` returns tricks per side; MCTS with determinization
  for hidden information (sample possible hand distributions, run MCTS
  on each as perfect-information, aggregate)