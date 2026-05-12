package com.phasmidsoftware.gambit.game

import com.phasmidsoftware.gambit.examples.connect4.Connect4State.given
import com.phasmidsoftware.gambit.examples.connect4.{Connect4, Connect4State, HeuristicPlayer, RandomPlayer, connect4Game}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should
import org.scalatest.tagobjects.Slow

import scala.util.Random

class TournamentSpec extends AnyFlatSpec with should.Matchers {

  // Convenience alias.
  private type C4Tournament = Tournament[Connect4, Connect4, Int, Boolean]

  private def contestant(name: String, player: Player[Connect4, Int, Boolean]) =
    Contestant(name, player)

  // ---------------------------------------------------------------------------
  // Contestant
  // ---------------------------------------------------------------------------

  behavior of "Contestant"

  it should "hold a name and a player" in {
    val c = Contestant("Random", new RandomPlayer)
    c.name shouldBe "Random"
    c.player shouldBe a[RandomPlayer]
  }

  // ---------------------------------------------------------------------------
  // Tournament structure
  // ---------------------------------------------------------------------------

  behavior of "Tournament structure"

  it should "produce standings with one entry per contestant" in {
    val t = Tournament[Connect4, Connect4, Int, Boolean](
      contestants = Seq(
        contestant("A", new RandomPlayer),
        contestant("B", new RandomPlayer),
      ),
      gamesPerPairing = 2,
      random = new Random(1L)
    )
    t.standings should have size 2
  }

  it should "produce standings with the correct column names" in {
    val t = Tournament[Connect4, Connect4, Int, Boolean](
      contestants = Seq(
        contestant("A", new RandomPlayer),
        contestant("B", new RandomPlayer),
      ),
      gamesPerPairing = 2,
      random = new Random(2L)
    )
    val rows = t.standings
    // Each row is (name, played, wins, draws, losses, goalDiff, points).
    rows.foreach { case (name, p, w, d, l, gd, pts) =>
      p shouldBe w + d + l
      pts shouldBe w * 3 + d
      gd shouldBe w - l
    }
  }

  it should "give each contestant the correct number of games played" in {
    // 3 contestants, gamesPerPairing=2.
    // Each contestant plays against 2 opponents, in both roles -> 2*2*2 = 8 games each.
    val t = Tournament[Connect4, Connect4, Int, Boolean](
      contestants = Seq(
        contestant("A", new RandomPlayer),
        contestant("B", new RandomPlayer),
        contestant("C", new RandomPlayer),
      ),
      gamesPerPairing = 2,
      random = new Random(3L)
    )
    t.standings.foreach { case (_, played, _, _, _, _, _) =>
      played shouldBe (3 - 1) * 2 * 2 // 2 opponents * 2 directions * 2 games
    }
  }

  it should "have standings sorted by points descending" in {
    val t = Tournament[Connect4, Connect4, Int, Boolean](
      contestants = Seq(
        contestant("A", new RandomPlayer),
        contestant("B", new RandomPlayer),
        contestant("C", new RandomPlayer),
      ),
      gamesPerPairing = 4,
      random = new Random(4L)
    )
    val pts = t.standings.map(_._7)
    pts shouldBe pts.sorted.reverse
  }

  it should "have zero-sum totals: total wins == total losses across all contestants" in {
    val t = Tournament[Connect4, Connect4, Int, Boolean](
      contestants = Seq(
        contestant("A", new RandomPlayer),
        contestant("B", new RandomPlayer),
        contestant("C", new RandomPlayer),
      ),
      gamesPerPairing = 2,
      random = new Random(5L)
    )
    val rows = t.standings
    val totalWins = rows.map(_._3).sum
    val totalLosses = rows.map(_._5).sum
    totalWins shouldBe totalLosses
  }

  // ---------------------------------------------------------------------------
  // League table formatting
  // ---------------------------------------------------------------------------

  behavior of "Tournament.leagueTable"

  it should "contain a header row with column labels" in {
    val t = Tournament[Connect4, Connect4, Int, Boolean](
      contestants = Seq(
        contestant("Random", new RandomPlayer),
        contestant("Heuristic", new HeuristicPlayer),
      ),
      gamesPerPairing = 2,
      random = new Random(6L)
    )
    val table = t.leagueTable
    table should include("Player")
    table should include("Pts")
    table should include("W")
    table should include("D")
    table should include("L")
  }

  it should "contain each contestant's name" in {
    val t = Tournament[Connect4, Connect4, Int, Boolean](
      contestants = Seq(
        contestant("Random", new RandomPlayer),
        contestant("Heuristic", new HeuristicPlayer),
      ),
      gamesPerPairing = 2,
      random = new Random(7L)
    )
    val table = t.leagueTable
    table should include("Random")
    table should include("Heuristic")
  }

  it should "have one data row per contestant" in {
    val t = Tournament[Connect4, Connect4, Int, Boolean](
      contestants = Seq(
        contestant("A", new RandomPlayer),
        contestant("B", new RandomPlayer),
        contestant("C", new RandomPlayer),
      ),
      gamesPerPairing = 2,
      random = new Random(8L)
    )
    // Header + separator + 3 data rows = 5 lines minimum.
    val lines = t.leagueTable.split('\n').toSeq
    lines.count(l => l.startsWith("1.") || l.startsWith("2.") || l.startsWith("3.")) shouldBe 3
  }

  // ---------------------------------------------------------------------------
  // Competitive ordering (slow: plays real games)
  // ---------------------------------------------------------------------------

  behavior of "Tournament competitive ordering"

  it should "rank HeuristicPlayer above RandomPlayer" taggedAs Slow in {
    val t = Tournament[Connect4, Connect4, Int, Boolean](
      contestants = Seq(
        contestant("Random", new RandomPlayer),
        contestant("Heuristic", new HeuristicPlayer),
      ),
      gamesPerPairing = 10,
      random = new Random(10L)
    )
    val rows = t.standings
    val hRank = rows.indexWhere(_._1 == "Heuristic")
    val rRank = rows.indexWhere(_._1 == "Random")
    hRank should be < rRank
  }

  it should "rank AlphaBeta above RandomPlayer" taggedAs Slow in {
    val ab = AlphaBetaPlayer[Connect4, Connect4, Int, Boolean](me = true, depth = 4)
    val t = Tournament[Connect4, Connect4, Int, Boolean](
      contestants = Seq(
        contestant("Random", new RandomPlayer),
        contestant("AB(d=4)", ab),
      ),
      gamesPerPairing = 10,
      random = new Random(11L)
    )
    val rows = t.standings
    rows.head._1 shouldBe "AB(d=4)"
  }
}