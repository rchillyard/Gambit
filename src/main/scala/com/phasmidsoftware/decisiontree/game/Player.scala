package com.phasmidsoftware.decisiontree.game

import scala.util.Random

/**
  * A Player in a game of type S, making moves of type M, identified as player Pl.
  *
  * @tparam S  the state type.
  * @tparam M  the move type.
  * @tparam Pl the player identity type.
  */
trait Player[S, M, Pl]:
  /**
    * Choose a move from the given state.
    * Returns None if no move is available (terminal position).
    *
    * @param s      the current state.
    * @param random a Random instance.
    * @return Some(move) or None.
    */
  def chooseMove(s: S, random: Random): Option[M]

  /**
    * Called at the end of a game with the full result and this player's identity.
    * Default implementation is a no-op. Override to implement learning or logging.
    *
    * @param result the game result (all players' scores).
    * @param me     this player's identity, used to extract the relevant score.
    */
  def gameOver(result: GameResult[Pl], me: Pl): Unit = ()

/**
  * A `GameResult` captures the outcome of one complete game for all players.
  * Each player maps to a score, typically -1 (loss), 0 (draw), or +1 (win)
  * for two-player zero-sum games. For trick-taking games like bridge, the
  * scores might represent tricks won per side.
  *
  * A `GameResult` is typically a subset of a `MatchResult` and is a `Map` of `Pl -> Int`.
  */
type GameResult[Pl] = Map[Pl, Int]

/**
  * Companion for GameResult providing factory methods.
  */
object GameResult:
  /**
    * Two-player win: winner scores +1, loser scores -1.
    */
  def win[Pl](winner: Pl, loser: Pl): GameResult[Pl] =
    Map(winner -> 1, loser -> -1)

  /**
    * Draw among the given players: all score 0.
    */
  def draw[Pl](players: Seq[Pl]): GameResult[Pl] =
    players.map(_ -> 0).toMap

  /**
    * Extract a single player's score from a result, defaulting to 0.
    */
  def score[Pl](result: GameResult[Pl], player: Pl): Int =
    result.getOrElse(player, 0)

/**
  * A MatchResult aggregates GameResults over a series of games.
  * Provides win/loss/draw counts and total games for a given player.
  *
  * @param results the sequence of individual game results.
  * @tparam Pl the player identity type.
  */
case class MatchResult[Pl](results: Seq[GameResult[Pl]]):
  /** Number of games won by `pl` (score == +1). */
  def winsFor(pl: Pl): Int = results.count(_.get(pl).contains(1))

  /** Number of games lost by `pl` (score == -1). */
  def lossesFor(pl: Pl): Int = results.count(_.get(pl).contains(-1))

  /** Number of draws for `pl` (score == 0). */
  def drawsFor(pl: Pl): Int = results.count(_.get(pl).contains(0))

  /** Total games played. */
  def total: Int = results.size

  /**
    * Summary string for a two-player game between p1 and p2.
    */
  def summary(p1: Pl, p2: Pl): String =
    f"Games: $total  " +
      f"$p1 wins: ${winsFor(p1)} (${renderPercent(winsFor(p1), total)})  " +
      f"$p2 wins: ${winsFor(p2)} (${renderPercent(winsFor(p2), total)})  " +
      f"Draws: ${drawsFor(p1)} (${renderPercent(drawsFor(p1), total)})"

  private def percent(n: Int, total: Int): Double = 100.0 * n / total

  private def renderPercent(n: Int, total: Int): String = f"${percent(n, total)}%.1f%%"