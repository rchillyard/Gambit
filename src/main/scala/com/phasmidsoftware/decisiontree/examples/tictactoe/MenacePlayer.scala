package com.phasmidsoftware.decisiontree.examples.tictactoe

import com.phasmidsoftware.decisiontree.game.{Game, GameResult, GameRunner, Player, State}

import scala.util.Random

/**
  * A MENACE player backed by a shared Matchboxes registry.
  * Records the sequence of (position, move) pairs during a game so it can
  * back-propagate the result afterwards.
  *
  * All cell indices in history are in original board orientation, matching
  * what selectMove returns and what update expects.
  *
  * Pl = Boolean: true = X (first player), false = O (second player).
  *
  * @param matchboxes the shared registry.
  */
class MenacePlayer(val matchboxes: Matchboxes) extends Player[TicTacToe, Int, Boolean] {

  // History of (position, chosen cell) in original orientation, for back-propagation.
  private var history: List[(TicTacToe, Int)] = Nil

  override def chooseMove(ttt: TicTacToe, random: Random): Option[Int] = {
    val move = matchboxes.selectMove(ttt, random)
    move.foreach(cell => history = (ttt, cell) :: history)
    move
  }

  override def gameOver(result: GameResult[Boolean], me: Boolean): Unit = {
    val beadResult = GameResult.score(result, me) match {
      case 1 => Win
      case -1 => Loss
      case _ => Draw
    }
    history.foreach { case (ttt, cell) =>
      matchboxes.update(ttt, cell, beadResult)
    }
    history = Nil
  }
}

/**
  * A player that selects moves uniformly at random.
  * Useful as a baseline opponent for MENACE to learn against.
  */
class RandomPlayer extends Player[TicTacToe, Int, Boolean] {
  override def chooseMove(ttt: TicTacToe, random: Random): Option[Int] = {
    val open = ttt.open
    if (open.isEmpty) None
    else {
      val (r, c) = open(random.nextInt(open.size))
      Some(r * TicTacToe.size + c)
    }
  }
}

/**
  * A player that uses the existing heuristic (TicTacToeState$.heuristic) to
  * greedily pick the best available move. Useful as a strong baseline.
  */
class HeuristicPlayer(implicit state: State[Board, TicTacToe])
  extends Player[TicTacToe, Int, Boolean] {

  override def chooseMove(ttt: TicTacToe, random: Random): Option[Int] = {
    if (state.isGoal(ttt).isDefined) None
    else {
      val successors = state.getStates(ttt)
      if (successors.isEmpty) None
      else {
        val best = successors.maxBy(state.heuristic)
        Some(TicTacToeUtils.cellFromDiff(best.board.value ^ ttt.board.value))
      }
    }
  }
}

/**
  * The Game typeclass instance for TicTacToe.
  *
  * S  = TicTacToe
  * M  = Int (flat cell index, 0..8 row-major)
  * Pl = Boolean (true = X, moves first; false = O, moves second)
  */
given tictactoeGame(using state: State[Board, TicTacToe]): Game[TicTacToe, Int, Boolean] with
  def start: TicTacToe = TicTacToe.start

  def startingPlayer: Boolean = true

  def players: Seq[Boolean] = Seq(true, false)

  def applyMove(ttt: TicTacToe, cell: Int, isX: Boolean): TicTacToe =
    val row = cell / TicTacToe.size
    val col = cell % TicTacToe.size
    state.construct(if isX then ttt.playX(row, col) else ttt.play0(row, col))

  def nextPlayer(ttt: TicTacToe, current: Boolean): Boolean = !current

/**
  * Factory for a TicTacToe GameRunner.
  * Requires an implicit State[Board, TicTacToe] in scope.
  */
object TicTacToeGameRunner:
  def apply(
             xPlayer: Player[TicTacToe, Int, Boolean],
             oPlayer: Player[TicTacToe, Int, Boolean],
             random: Random = new Random(42L)
           )(using State[Board, TicTacToe]): GameRunner[Board, TicTacToe, Int, Boolean] =
    new GameRunner[Board, TicTacToe, Int, Boolean](
      playerMap = Map(true -> xPlayer, false -> oPlayer),
      random = random
    )