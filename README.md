[![Codacy Badge](https://api.codacy.com/project/badge/Grade/2d89f95b27b246e3bd1c3c116ff24004)](https://www.codacy.com/app/scalaprof/DecisionTree?utm_source=github.com&utm_medium=referral&utm_content=rchillyard/DecisionTree&utm_campaign=Badge_Grade)
[![CircleCI](https://circleci.com/gh/rchillyard/DecisionTree.svg?style=svg)](https://circleci.com/gh/rchillyard/DecisionTree)

# DecisionTree

A Scala 3 framework for game-playing tree search and reinforcement learning.

## Overview

DecisionTree provides the infrastructure for evaluating game-playing strategies
using tree search. It is related to both minimax and Monte Carlo Tree Search (MCTS)
techniques, and currently includes a full implementation of MENACE
(Matchbox Educable Noughts And Crosses Engine) and a perfect minimax player for
TicTacToe.

The framework depends on [Visitor](https://github.com/rchillyard/Visitor) for
its graph and tree traversal engine.

## Structure

### Core (`com.phasmidsoftware.decisiontree.moves`)

- `State[P, S]` — typeclass for a game state; defines legal moves, validity,
  goal detection, and heuristic evaluation
- `Transition[P, S]` / `Move[P, S]` — a function from state `S` to a proto-state `P`
  from which the next state is constructed
- `Evaluator[S]` — evaluates a game tree starting from a given state

### TicTacToe (`com.phasmidsoftware.decisiontree.examples.tictactoe`)

- `TicTacToe` / `Board` — compact bitboard representation of a TicTacToe position,
  with rotation, transposition, and a rich heuristic hierarchy
- `TicTacToeOps` — low-level Java bit manipulation for board operations (rotate,
  transpose, exchange, play, render)
- `TicTacToeUtils` — shared utility methods (e.g. `cellFromDiff`)

### Players (`com.phasmidsoftware.decisiontree.examples.tictactoe`)

Four player types are provided, ranging from naive to optimal:

- `RandomPlayer` — selects moves uniformly at random (training baseline)
- `HeuristicPlayer` — greedily selects the highest-heuristic move
- `MenacePlayer` — reinforcement learning via the MENACE bead machine (see below)
- `PerfectPlayer` — full minimax evaluation via Visitor's post-order DFS;
  never loses, always draws against itself

`GameRunner` plays sequences of games between any two players and reports
win/loss/draw statistics via `GameStats`.

### MENACE (`com.phasmidsoftware.decisiontree.examples.tictactoe`)

A reinforcement learning player based on Donald Michie's 1960 matchbox machine.
Each board position is represented by a `Matchbox` holding weighted beads for each
legal move. After each game, beads are added to winning matchboxes and removed from
losing ones. Over many games the machine learns to play well.

- `Matchbox` — weighted bead selection, reward, and penalise
- `Matchboxes` — registry of matchboxes with D4 symmetry reduction (the 8-element
  dihedral group collapses rotationally and reflectionally equivalent positions into
  a single matchbox, accelerating learning ~8×)

### PerfectPlayer

`PerfectPlayer` builds a complete minimax score map over the TicTacToe game DAG
on first use, via a single post-order DFS using Visitor's `Traversal.dfs`. The
`VisitedSet` ensures each of the ~5,000 reachable positions is evaluated exactly
once. Scores are X-perspective (+1 = X wins, 0 = draw, -1 = O wins); internal
nodes aggregate children's scores by maximising (X to move) or minimising (O to
move).

## Design Documents

- `VisitorDesign.md` — design of the Visitor traversal engine
- `matchbox_design.md` — design of the MENACE implementation and PerfectPlayer,
  including D4 symmetry canonicalization, coordinate space invariants, and
  minimax evaluation

## Future Work

- Generalise `Player` and `GameRunner` beyond TicTacToe to a generic `[S, M]`
  (state, move) interface
- MENACE self-play (two independent or shared-registry agents)
- Extend to Chess and Go via full minimax with alpha-beta pruning / MCTS
- Extend to Contract Bridge via determinization-based MCTS (handling hidden
  information by sampling possible hand distributions)