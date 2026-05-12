package com.phasmidsoftware.gambit.game

import scala.util.Random

/**
  * A named contestant in a tournament.
  *
  * Wraps a `Player` with a display name so the league table can identify
  * each entry independently of the Boolean player-identity used internally
  * by two-player `GameRunner`.
  *
  * @param name   display name for the league table.
  * @param player the underlying player.
  * @tparam S  the state type.
  * @tparam M  the move type.
  * @tparam Pl the player identity type.
  */
case class Contestant[S, M, Pl](name: String, player: Player[S, M, Pl])

/**
  * A round-robin tournament for two-player zero-sum games.
  *
  * Every pair of contestants plays `gamesPerPairing` games with each
  * contestant taking the first-player role in half the games, so neither
  * side has a systematic first-move advantage in the overall standings.
  *
  * == Scoring ==
  * Standard football (soccer) 3-1-0 scoring:
  * Win  = 3 points
  * Draw = 1 point
  * Loss = 0 points
  *
  * Ties in the table are broken by goal difference (wins minus losses),
  * then by total wins.
  *
  * == Usage ==
  * {{{
  *   val t = Tournament(contestants, gamesPerPairing = 10, random = new Random(42L))
  *   println(t.leagueTable)
  * }}}
  *
  * @param contestants     the players to enter in the tournament.
  * @param gamesPerPairing number of games each ordered pair plays (A-as-first
  *                        vs B-as-second). Each unordered pair therefore plays
  *                        2 * gamesPerPairing games in total.
  *
  * @param random          random source passed to each GameRunner.
  * @param state           implicit State[P, S].
  * @param game            implicit Game[S, M, Pl].
  * @tparam P  the proto-state type.
  * @tparam S  the state type.
  * @tparam M  the move type.
  * @tparam Pl the player identity type.
  */
class Tournament[P, S, M, Pl](
                               contestants: Seq[Contestant[S, M, Pl]],
                               gamesPerPairing: Int = 10,
                               random: Random = new Random(42L)
                             )(using state: State[P, S], game: Game[S, M, Pl]):

  /**
    * Run the full round-robin and return a formatted league table string.
    *
    * Each row shows: rank, name, games played, wins, draws, losses, points.
    * Rows are sorted by points desc, then goal difference desc, then wins desc.
    */
  def leagueTable: String =
    val rows = standings
    val nameWidth = (contestants.map(_.name.length) :+ "Player".length).max
    val header = formatRow("Player", "P", "W", "D", "L", "GD", "Pts", nameWidth)
    val separator = "-" * header.length
    val body = rows.zipWithIndex.map { case ((name, p, w, d, l, gd, pts), i) =>
      s"${i + 1}. " + formatRow(name, p.toString, w.toString, d.toString, l.toString,
        (if gd >= 0 then "+" else "") + gd, pts.toString, nameWidth)
    }
    (Seq(header, separator) ++ body).mkString("\n")

  /**
    * Run all pairings and return the sorted standings.
    * Each entry is (name, played, wins, draws, losses, goalDiff, points).
    */
  def standings: Seq[(String, Int, Int, Int, Int, Int, Int)] =
    val totals = scala.collection.mutable.Map(
      contestants.map(_.name -> TournamentRecord()) *
    )
    // Every ordered pair (i, j) with i != j: contestant i plays as first player.
    for
      i <- contestants.indices
      j <- contestants.indices
      if i != j
    do
      val first = contestants(i)
      val second = contestants(j)
      val runner = new GameRunner[P, S, M, Pl](
        playerMap = Map(
          game.startingPlayer -> first.player,
          game.players.find(_ != game.startingPlayer).get -> second.player
        ),
        random = random
      )
      val results = runner.playGames(gamesPerPairing)
      // Accumulate from first player's perspective (identity = startingPlayer = true).
      val fp = game.startingPlayer
      val sp = game.players.find(_ != game.startingPlayer).get
      totals(first.name) = totals(first.name).add(results.winsFor(fp), results.drawsFor(fp), results.lossesFor(fp))
      totals(second.name) = totals(second.name).add(results.winsFor(sp), results.drawsFor(sp), results.lossesFor(sp))

    totals.toSeq
      .map { case (name, r) => (name, r.played, r.wins, r.draws, r.losses, r.wins - r.losses, r.points) }
      .sortBy { case (_, _, w, _, l, gd, pts) => (-pts, -(w - l), -w) }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private def formatRow(name: String, p: String, w: String, d: String,
                        l: String, gd: String, pts: String, nameWidth: Int): String =
    s"%-${nameWidth}s  %4s  %4s  %4s  %4s  %4s  %4s".format(name, p, w, d, l, gd, pts)

  /**
    * Mutable accumulator for one contestant's running totals.
    */
  private case class TournamentRecord(
                                       played: Int = 0, wins: Int = 0, draws: Int = 0, losses: Int = 0
                                     ):
    def points: Int = wins * 3 + draws

    def add(w: Int, d: Int, l: Int): TournamentRecord =
      TournamentRecord(played + w + d + l, wins + w, draws + d, losses + l)