package com.phasmidsoftware.decisiontree.examples.connect4

import com.phasmidsoftware.decisiontree.examples.connect4.Connect4State.given
import com.phasmidsoftware.decisiontree.game.{MCTSPlayer, Player}

import scala.util.Random

/**
  * Demonstrates Connect4 games between player types, printing the board
  * state and heuristic score after each move.
  *
  * Each matchup is played twice — home (X) and away (O) — to account for
  * the first-mover advantage X enjoys in Connect Four.
  *
  * Matchups: MCTS vs Random, MCTS vs Heuristic, Heuristic vs Random.
  */
@main def Connect4Demo(): Unit =

  val rng = new Random(42L)

  type P = Player[Connect4, Int, Boolean]

  // Each entry is (name, makeAsX, makeAsO) — separate factories per role.
  val named: List[(String, () => P, () => P)] = List(
    ("MCTS(500)",
      () => MCTSPlayer[Connect4, Connect4, Int, Boolean](me = true, iterations = 500),
      () => MCTSPlayer[Connect4, Connect4, Int, Boolean](me = false, iterations = 500)),
    ("Heuristic",
      () => new HeuristicPlayer,
      () => new HeuristicPlayer),
    ("Random",
      () => new RandomPlayer,
      () => new RandomPlayer),
  )

  // Each distinct pair plays home and away.
  val pairs = for
    i <- named.indices
    j <- named.indices
    if i < j
  yield (named(i), named(j))

  pairs.foreach { case ((name1, makeX1, makeO1), (name2, makeX2, makeO2)) =>
    // Home: name1 as X, name2 as O.
    println("=" * 60)
    println(s"HOME:  $name1 (X) vs $name2 (O)")
    println("=" * 60)
    playConnect4Demo(makeX1(), name1, makeO2(), name2, rng): Unit
    println()

    // Away: name2 as X, name1 as O.
    println("=" * 60)
    println(s"AWAY:  $name2 (X) vs $name1 (O)")
    println("=" * 60)
    playConnect4Demo(makeX2(), name2, makeO1(), name1, rng): Unit
    println()
  }

/**
  * Play a single Connect4 game, printing each move with board and heuristic.
  */
def playConnect4Demo(
                      xPlayer: Player[Connect4, Int, Boolean],
                      xName: String,
                      oPlayer: Player[Connect4, Int, Boolean],
                      oName: String,
                      rng: Random
                    ): Option[Boolean] =
  var s = Connect4.start
  var moveNum = 0
  var xToMove = true

  println(s"Starting position:\n${renderConnect4WithHeuristic(s)}")

  while Connect4State.isGoal(s).isEmpty do
    val player = if xToMove then xPlayer else oPlayer
    val symbol = if xToMove then "X" else "O"
    player.chooseMove(s, rng) match
      case None =>
        println(s"$symbol has no move — game over.")
      case Some(col) =>
        moveNum += 1
        s = s.play(col, xToMove)
        println(s"Move $moveNum ($symbol plays column $col):")
        println(renderConnect4WithHeuristic(s))
    xToMove = !xToMove

  val result = Connect4State.isGoal(s)
  result match
    case Some(true) =>
      val (winner, name) = if s.player then ("X", xName) else ("O", oName)
      println(s"Result: $winner ($name) wins!")
    case Some(false) => println("Result: Draw")
    case None => println("Result: Unknown")
  result

/**
  * Render a Connect4 board with its heuristic score.
  * Adds column index header for readability.
  */
def renderConnect4WithHeuristic(s: Connect4): String =
  val header = "0123456\n"
  val board = s.render
  val heuristic = if Connect4State.isGoal(s).isDefined then "terminal"
  else f"heuristic=${Connect4State.heuristic(s)}%.1f"
  s"$header$board[$heuristic]\n"