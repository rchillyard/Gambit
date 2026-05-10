[![Codacy Badge](https://api.codacy.com/project/badge/Grade/2d89f95b27b246e3bd1c3c116ff24004)](https://www.codacy.com/app/scalaprof/DecisionTree?utm_source=github.com&utm_medium=referral&utm_content=rchillyard/DecisionTree&utm_campaign=Badge_Grade)
[![CircleCI](https://circleci.com/gh/rchillyard/DecisionTree.svg?style=svg)](https://circleci.com/gh/rchillyard/DecisionTree)

# DecisionTree

A Scala 3 framework for game-playing tree search and reinforcement learning.

## Overview

DecisionTree provides the infrastructure for evaluating game-playing strategies
using tree search. It is related to both minimax and Monte Carlo Tree Search (MCTS)
techniques, and currently includes a full implementation of MENACE
(Matchbox Educable Noughts And Crosses Engine) for TicTacToe.

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

### MENACE (`com.phasmidsoftware.decisiontree.examples.tictactoe`)

A reinforcement learning player based on Donald Michie's 1960 matchbox machine.
Each board position is represented by a `Matchbox` holding weighted beads for each
legal move. After each game, beads are added to winning matchboxes and removed from
losing ones. Over many games the machine learns to play well.

- `Matchbox` — weighted bead selection, reward, and penalise
- `Matchboxes` — registry of matchboxes with D4 symmetry reduction (the 8-element
  dihedral group collapses rotationally and reflectionally equivalent positions into
  a single matchbox, accelerating learning ~8×)
- `MenacePlayer` — consults `Matchboxes` to choose moves and back-propagates results
- `RandomPlayer` — selects moves uniformly at random (training baseline)
- `HeuristicPlayer` — greedily selects the highest-heuristic move (strong baseline)
- `GameRunner` — plays sequences of games between two players and reports statistics

## Design Documents

- `VisitorDesign.md` — design of the Visitor traversal engine
- `matchbox_design.md` — design of the MENACE implementation, including the D4
  symmetry canonicalization and the coordinate space invariants that must be
  maintained between `Matchboxes.selectMove`, `update`, and `canonicalMatchbox`

## Future Work

- Generalise `Player` and `GameRunner` beyond TicTacToe to a generic `[S, M]`
  (state, move) interface
- MENACE self-play (two independent or shared-registry agents)
- Extend to Chess and Go via full minimax / MCTS
- Extend to Contract Bridge via determinization-based MCTS (handling hidden
  information by sampling possible hand distributions)