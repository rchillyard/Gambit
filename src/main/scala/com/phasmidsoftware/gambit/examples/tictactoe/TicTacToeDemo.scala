package com.phasmidsoftware.gambit.examples.tictactoe

import com.phasmidsoftware.gambit.examples.tictactoe.TicTacToe.TicTacToeState$
import com.phasmidsoftware.gambit.game.{MCTSPlayer, Player}

import scala.util.Random

/**
  * Demonstrates TicTacToe games between all four player types.
  * Each distinct pair plays twice — home (X) and away (O) — to account
  * for the first-mover advantage X enjoys in TicTacToe.
  */
@main def TicTacToeDemo(): Unit =

  val rng = new Random(42L)

  type P = Player[TicTacToe, Int, Boolean]

  // Each entry is (name, makeAsX, makeAsO) — factories for each role.
  val named: List[(String, () => P, () => P)] = List(
    ("Random",
      () => new RandomPlayer,
      () => new RandomPlayer),
    ("Heuristic",
      () => new HeuristicPlayer,
      () => new HeuristicPlayer),
    ("MCTS(500)",
      () => MCTSPlayer[Board, TicTacToe, Int, Boolean](me = true, iterations = 500),
      () => MCTSPlayer[Board, TicTacToe, Int, Boolean](me = false, iterations = 500)),
    ("Perfect",
      () => new PerfectPlayer,
      () => new PerfectPlayer),
  )

  // Each distinct pair plays home and away.
  val pairs = for
    i <- named.indices
    j <- named.indices
    if i < j
  yield (named(i), named(j))

  pairs.foreach { case ((name1, makeX1, makeO1), (name2, makeX2, makeO2)) =>
    // Home: name1 as X, name2 as O.
    println("=" * 50)
    println(s"HOME:  $name1 (X) vs $name2 (O)")
    println("=" * 50)
    playTicTacToeDemo(makeX1(), name1, makeO2(), name2, rng): Unit
    println()

    // Away: name2 as X, name1 as O.
    println("=" * 50)
    println(s"AWAY:  $name2 (X) vs $name1 (O)")
    println("=" * 50)
    playTicTacToeDemo(makeX2(), name2, makeO1(), name1, rng): Unit
    println()
  }

/**
  * Play a single TicTacToe game, printing each move with board and heuristic.
  */
def playTicTacToeDemo(
                       xPlayer: Player[TicTacToe, Int, Boolean],
                       xName: String,
                       oPlayer: Player[TicTacToe, Int, Boolean],
                       oName: String,
                       rng: Random
                     ): Option[Boolean] =
  import TicTacToeState$ as state

  var ttt = TicTacToe.start
  var moveNum = 0
  var xToMove = true

  println(s"Starting position:\n${renderTTTWithHeuristic(ttt)}")

  while state.isGoal(ttt).isEmpty do
    val player = if xToMove then xPlayer else oPlayer
    val symbol = if xToMove then "X" else "O"
    player.chooseMove(ttt, rng) match
      case None =>
        println(s"$symbol has no move — game over.")
      case Some(cell) =>
        moveNum += 1
        val row = cell / TicTacToe.size
        val col = cell % TicTacToe.size
        val proto = if xToMove then ttt.playX(row, col) else ttt.play0(row, col)
        ttt = state.construct(proto)
        println(s"Move $moveNum ($symbol plays cell $cell — row $row, col $col):")
        println(renderTTTWithHeuristic(ttt))
    xToMove = !xToMove

  val result = state.isGoal(ttt)
  result match
    case Some(true) =>
      val (winner, name) = if ttt.player then ("X", xName) else ("O", oName)
      println(s"Result: $winner ($name) wins!")
    case Some(false) => println("Result: Draw")
    case None => println("Result: Unknown")
  result

/**
  * Render a TicTacToe board with its heuristic score.
  */
def renderTTTWithHeuristic(ttt: TicTacToe): String =
  import TicTacToeState$ as state
  val board = TicTacToeOps.renderWithNewlines(ttt.board.value)
  val heuristic = if state.isGoal(ttt).isDefined then "terminal"
  else f"heuristic=${state.heuristic(ttt)}%.1f"
  s"$board[$heuristic]\n"