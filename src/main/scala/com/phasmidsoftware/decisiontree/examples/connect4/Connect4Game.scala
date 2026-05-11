package com.phasmidsoftware.decisiontree.examples.connect4

import com.phasmidsoftware.decisiontree.game.{Game, GameRunner, Player, State}

import scala.util.Random

/**
  * The Game typeclass instance for Connect Four.
  *
  * S  = Connect4
  * M  = Int (column index, 0..6)
  * Pl = Boolean (true = X, moves first; false = O, moves second)
  */
given connect4Game(using State[Connect4, Connect4]): Game[Connect4, Int, Boolean] with
  def start: Connect4 = Connect4.start

  def startingPlayer: Boolean = true

  def players: Seq[Boolean] = Seq(true, false)

  def moves(s: Connect4): Seq[Int] = s.open

  def applyMove(s: Connect4, col: Int, isX: Boolean): Connect4 =
    s.play(col, isX)

  def nextPlayer(s: Connect4, current: Boolean): Boolean = !current

/**
  * A player that selects moves uniformly at random.
  */
class RandomPlayer extends Player[Connect4, Int, Boolean]:
  def chooseMove(s: Connect4, random: Random): Option[Int] =
    if Connect4State.isGoal(s).isDefined then None
    else
      val open = s.open
      if open.isEmpty then None
      else Some(open(random.nextInt(open.size)))

/**
  * A player that greedily selects the highest-heuristic successor.
  */
class HeuristicPlayer extends Player[Connect4, Int, Boolean]:
  def chooseMove(s: Connect4, random: Random): Option[Int] =
    if Connect4State.isGoal(s).isDefined then None
    else
      val successors = Connect4State.getStates(s)
      if successors.isEmpty then None
      else
        // heuristic(succ) is from the perspective of the player who just moved
        // into succ — which is the current player. So maxBy gives the best move.
        val best = successors.maxBy(Connect4State.heuristic)
        // Recover the column played by finding which column's height changed.
        val col = s.heights.zip(best.heights).indexWhere { case (h1, h2) => h2 > h1 }
        if col < 0 then None else Some(col)

/**
  * Factory for a Connect Four GameRunner.
  */
object Connect4GameRunner:
  def apply(
             xPlayer: Player[Connect4, Int, Boolean],
             oPlayer: Player[Connect4, Int, Boolean],
             random: Random = new Random(42L)
           )(using State[Connect4, Connect4], Game[Connect4, Int, Boolean]): GameRunner[Connect4, Connect4, Int, Boolean] =
    new GameRunner[Connect4, Connect4, Int, Boolean](
      playerMap = Map(true -> xPlayer, false -> oPlayer),
      random = random
    )