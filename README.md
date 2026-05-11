![Sonatype Central](https://maven-badges.sml.io/sonatype-central/com.phasmidsoftware/gambit_3/badge.svg?color=blue)
[![Codacy Badge](https://app.codacy.com/project/badge/Grade/2d89f95b27b246e3bd1c3c116ff24004)](https://app.codacy.com/gh/rchillyard/Gambit/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)
[![CircleCI](https://dl.circleci.com/status-badge/img/gh/rchillyard/Gambit/tree/main.svg?style=shield)](https://dl.circleci.com/status-badge/redirect/gh/rchillyard/Gambit/tree/main)
![GitHub Top Languages](https://img.shields.io/github/languages/top/rchillyard/Gambit)
![GitHub](https://img.shields.io/github/license/rchillyard/Gambit)
![GitHub last commit](https://img.shields.io/github/last-commit/rchillyard/Gambit)
![GitHub issues](https://img.shields.io/github/issues-raw/rchillyard/Gambit)
![GitHub issues by-label](https://img.shields.io/github/issues/rchillyard/Gambit/bug)

# Gambit

A Scala 3 framework for game-playing tree search and reinforcement learning.

## Overview

Gambit provides a generic, typeclass-driven infrastructure for game-playing
strategies. It supports minimax, Monte Carlo Tree Search (MCTS), and reinforcement
learning (MENACE), with fully worked implementations for TicTacToe and Connect Four.

The framework depends on [Visitor](https://github.com/rchillyard/Visitor) for
its graph and tree traversal engine.

## Usage

Gambit is published to Maven Central. Add the following to your `build.sbt`:

```scala
libraryDependencies += "com.phasmidsoftware" %% "gambit" % "1.0.7"
```

Requires Scala 3. Depends on [Visitor](https://github.com/rchillyard/Visitor) 1.6.0,
which is pulled in automatically as a transitive dependency.

## Structure

### Generic Framework (`com.phasmidsoftware.gambit.game`)

- `State[P, S]` — typeclass for a game state: legal moves, goal detection,
  heuristic evaluation, and `isFirstPlayerToMove`
- `Game[S, M, Pl]` — typeclass for game mechanics: `start`, `moves`, `applyMove`,
  `nextPlayer`, `currentPlayer`, `winner`
- `Player[S, M, Pl]` — trait for player strategy: `chooseMove`, `gameOver`
- `GameRunner[P, S, M, Pl]` — generic game execution driven by `State` and `Game` givens
- `GameResult[Pl]` / `MatchResult[Pl]` — per-game and per-match result types
- `MCTSPlayer[P, S, M, Pl]` — generic Monte Carlo Tree Search player with UCB1

### TicTacToe (`com.phasmidsoftware.gambit.examples.tictactoe`)

- `TicTacToe` / `Board` — compact bitboard representation with rotation,
  transposition, and a rich heuristic hierarchy
- `TicTacToeOps` — low-level Java bit manipulation (rotate, transpose, play, render)
- Five player types: `RandomPlayer`, `HeuristicPlayer`, `MenacePlayer`,
  `PerfectPlayer`, and `MCTSPlayer`
- `given tictactoeGame` — wires TicTacToe into the generic `Game` typeclass
- `TicTacToeDemo` — `@main` program playing home/away matches between all player types

### MENACE (`com.phasmidsoftware.gambit.examples.tictactoe`)

A reinforcement learning player based on Donald Michie's 1960 matchbox machine.
Each board position is a `Matchbox` of weighted beads per legal move; beads are
added after wins and removed after losses.

- `Matchboxes` — registry with D4 symmetry reduction (~8× learning speedup)
- `MenacePlayer` — records move history and back-propagates results

### PerfectPlayer (`com.phasmidsoftware.gambit.examples.tictactoe`)

Builds a complete minimax score map over the TicTacToe game DAG on first use via
Visitor's `Traversal.dfs(DfsOrder.Post)`. Each of the ~5,000 reachable positions
is evaluated exactly once (DAG, not tree). Never loses; always draws against itself.

### Connect Four (`com.phasmidsoftware.gambit.examples.connect4`)

- `Connect4` — column-major bitboard with gravity, sentinel-bit win detection
- `Connect4State` — `State[Connect4, Connect4]` with window-scoring heuristic
- Three player types: `RandomPlayer`, `HeuristicPlayer`, `MCTSPlayer`
- `given connect4Game` — wires Connect Four into the generic `Game` typeclass
- `Connect4Demo` — `@main` program playing home/away matches between player types

## Demo Programs

Run from sbt with `sbt run` and choose `TicTacToeDemo` or `Connect4Demo`. Each
program plays home-and-away matches between all player type pairs, printing the
board state and heuristic score after each move, e.g.:

```
HOME:  Perfect (X) vs MCTS(500) (O)
==================================================
Move 1 (X plays cell 4 — row 1, col 1):
...
.X.
...
[heuristic=7.0]
...
Result: X (Perfect) wins!
```

## Design Documents

- `VisitorDesign.md` — design of the Visitor traversal engine
- `GamePlayingDesign.md` — design of the game-playing framework: typeclasses,
  MCTS, MENACE, Connect Four, heuristic conventions, and future directions

## Future Work

- MCTS tree reuse between moves; actor-based parallel rollouts (Akka/Pekko)
- MENACE self-play, X/O symmetry, and persistence
- Alpha-beta pruning for stronger Connect Four play
- Chess — `given chessGame: Game[ChessState, ChessMove, Boolean]`
- Bridge — `given bridgeGame: Game[BridgeState, Card, Seat]`; four-player;
  MCTS with determinization for hidden information