package com.phasmidsoftware.gambit.game

import scala.annotation.tailrec
import scala.util.Random

/**
  * A generic game runner that plays a sequence of games between any number of
  * players, for any game representable as a State[P, S] and Game[S, M, Pl].
  *
  * Type parameters:
  * P  — proto-state type (used by `State.construct`)
  * S  — state type
  * M  — move type
  * Pl — player identity type (e.g., Boolean for two-player, Seat for bridge)
  *
  * @param playerMap a map from player identity to Player instance.
  * @param random    a Random instance for reproducibility.
  * @param state     implicit State[P, S] for goal detection.
  * @param game      implicit Game[S, M, Pl] for game mechanics.
  */
class GameRunner[P, S, M, Pl](
                               playerMap: Map[Pl, Player[S, M, Pl]],
                               random: Random = new Random(42L)
                             )(using state: State[P, S], game: Game[S, M, Pl]):

  /**
    * Play a single game from the starting state.
    *
    * @return a GameResult mapping each player to their score.
    */
  def playGame(): GameResult[Pl] =
    @tailrec
    def loop(s: S, current: Pl): GameResult[Pl] =
      state.isGoal(s) match
        case Some(true) => game.winner(s, current)
        case Some(false) => GameResult.draw(game.players)
        case None =>
          playerMap.get(current) match
            case None => GameResult.draw(game.players)
            case Some(player) =>
              player.chooseMove(s, random) match
                case None => GameResult.draw(game.players)
                case Some(move) =>
                  val next = game.applyMove(s, move, current)
                  loop(next, game.nextPlayer(next, current))

    val result = loop(game.start, game.startingPlayer)
    playerMap.foreach { case (pl, player) => player.gameOver(result, pl) }
    result

  /**
    * Play n games and return aggregated statistics.
    *
    * @param n the number of games to play.
    * @return a MatchResult summarising all games.
    */
  def playGames(n: Int): MatchResult[Pl] =
    MatchResult((1 to n).map(_ => playGame()))