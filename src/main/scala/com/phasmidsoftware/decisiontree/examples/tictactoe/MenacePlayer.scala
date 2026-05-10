package com.phasmidsoftware.decisiontree.examples.tictactoe

import com.phasmidsoftware.decisiontree.moves.State

import scala.util.Random

/**
  * A Player is anything that can choose a move from a TicTacToe position.
  */
trait Player {
  /**
    * Choose a move (flat cell index 0..8, row-major, original orientation)
    * from the given position.
    * Returns None if no move is available (should not happen in normal play).
    *
    * @param ttt    the current position.
    * @param random a Random instance.
    * @return Some(cellIndex) or None.
    */
  def chooseMove(ttt: TicTacToe, random: Random): Option[Int]

  /** Called at the end of a game with the result from this player's perspective. */
  def gameOver(result: MatchResult): Unit = ()
}

/**
  * A MENACE player backed by a shared Matchboxes registry.
  * Records the sequence of (position, move) pairs during a game so it can
  * back-propagate the result afterwards.
  *
  * All cell indices in history are in original board orientation, matching
  * what selectMove returns and what update expects.
  *
  * @param matchboxes the shared registry.
  */
class MenacePlayer(val matchboxes: Matchboxes) extends Player {

  // History of (position, chosen cell) in original orientation, for back-propagation.
  private var history: List[(TicTacToe, Int)] = Nil

  override def chooseMove(ttt: TicTacToe, random: Random): Option[Int] = {
    val move = matchboxes.selectMove(ttt, random)
    move.foreach(cell => history = (ttt, cell) :: history)
    move
  }

  override def gameOver(result: MatchResult): Unit = {
    history.foreach { case (ttt, cell) =>
      matchboxes.update(ttt, cell, result)
    }
    history = Nil
  }
}

/**
  * A player that selects moves uniformly at random.
  * Useful as a baseline opponent for MENACE to learn against.
  */
class RandomPlayer extends Player {
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
class HeuristicPlayer(implicit state: State[Board, TicTacToe]) extends Player {
  override def chooseMove(ttt: TicTacToe, random: Random): Option[Int] = {
    if (state.isGoal(ttt).isDefined) None // terminal position
    else {
      val successors = state.getStates(ttt)
      if (successors.isEmpty) None
      else {
        val best = successors.maxBy(state.heuristic)
        val diff = best.board.value ^ ttt.board.value
        Some(TicTacToeUtils.cellFromDiff(diff))
      }
    }
  }
}

/**
  * Runs a sequence of games between two Players and accumulates statistics.
  *
  * @param xPlayer the player who moves first (X).
  * @param oPlayer the player who moves second (O).
  * @param random  a Random instance for reproducibility.
  */
class GameRunner(
                  xPlayer: Player,
                  oPlayer: Player,
                  random: Random = new Random(42L)
                )(implicit state: State[Board, TicTacToe]) {

  /**
    * Play a single game from the starting position.
    *
    * @return the GameResult: XWins, OWins, or GameDraw.
    */
  def playGame(): GameResult = {
    @scala.annotation.tailrec
    def loop(ttt: TicTacToe, xToMove: Boolean): GameResult = {
      state.isGoal(ttt) match {
        case Some(true) =>
          // The player who just moved won — xToMove is the next player.
          if (xToMove) OWins else XWins
        case Some(false) =>
          GameDraw
        case None =>
          val player = if (xToMove) xPlayer else oPlayer
          player.chooseMove(ttt, random) match {
            case None => GameDraw
            case Some(cell) =>
              val row = cell / TicTacToe.size
              val col = cell % TicTacToe.size
              val proto =
                if (xToMove) ttt.playX(row, col) else ttt.play0(row, col)
              val next = state.construct(proto)
              loop(next, !xToMove)
          }
      }
    }

    val result = loop(TicTacToe.start, xToMove = true)
    xPlayer.gameOver(if (result == XWins) Win else if (result == OWins) Loss else Draw)
    oPlayer.gameOver(if (result == OWins) Win else if (result == XWins) Loss else Draw)
    result
  }

  /**
    * Play n games and return aggregated statistics.
    *
    * @param n the number of games to play.
    * @return a GameStats summary.
    */
  def playGames(n: Int): GameStats = {
    val results = (1 to n).map(_ => playGame())
    GameStats(
      xWins = results.count(_ == XWins),
      oWins = results.count(_ == OWins),
      draws = results.count(_ == GameDraw),
      total = n
    )
  }
}

/** The result of a single game. */
sealed trait GameResult
case object XWins    extends GameResult
case object OWins    extends GameResult
case object GameDraw extends GameResult

/**
  * Aggregated statistics over multiple games.
  */
case class GameStats(xWins: Int, oWins: Int, draws: Int, total: Int) {
  def xWinPct: Double = 100.0 * xWins / total
  def oWinPct: Double = 100.0 * oWins / total
  def drawPct: Double = 100.0 * draws / total

  override def toString: String =
    f"Games: $total  X wins: $xWins (${xWinPct}%.1f%%)  O wins: $oWins (${oWinPct}%.1f%%)  Draws: $draws (${drawPct}%.1f%%)"
}