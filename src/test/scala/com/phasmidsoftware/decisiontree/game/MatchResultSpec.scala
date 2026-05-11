package com.phasmidsoftware.decisiontree.game

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

class MatchResultSpec extends AnyFlatSpec with should.Matchers {

  // Helper to build a MatchResult from win/draw/loss counts.
  // p1 wins: score(p1)=1, score(p2)=-1
  // p2 wins: score(p1)=-1, score(p2)=1
  // draws:   score(p1)=0, score(p2)=0
  private def makeResult(p1Wins: Int, p2Wins: Int, draws: Int): MatchResult[Boolean] =
    val results =
      List.fill(p1Wins)(Map(true -> 1,  false -> -1)) ++
        List.fill(p2Wins)(Map(true -> -1, false -> 1))  ++
        List.fill(draws) (Map(true -> 0,  false -> 0))
    MatchResult(results)

  behavior of "MatchResult.summary"

  it should "format a simple result correctly" in {
    // 3 p1 wins, 1 p2 win, 1 draw — 5 games total.
    val mr = makeResult(p1Wins = 3, p2Wins = 1, draws = 1)
    val s  = mr.summary(true, false)
    s shouldBe "Games: 5  true wins: 3 (60.0%)  false wins: 1 (20.0%)  Draws: 1 (20.0%)"
  }

  it should "format an all-draws result correctly" in {
    val mr = makeResult(p1Wins = 0, p2Wins = 0, draws = 4)
    val s  = mr.summary(true, false)
    s shouldBe "Games: 4  true wins: 0 (0.0%)  false wins: 0 (0.0%)  Draws: 4 (100.0%)"
  }

  it should "format an all-p1-wins result correctly" in {
    val mr = makeResult(p1Wins = 2, p2Wins = 0, draws = 0)
    val s  = mr.summary(true, false)
    s shouldBe "Games: 2  true wins: 2 (100.0%)  false wins: 0 (0.0%)  Draws: 0 (0.0%)"
  }

  it should "work with non-Boolean player types" in {
    // Use String player identities.
    val results = List(
      Map("X" -> 1,  "O" -> -1),
      Map("X" -> 1,  "O" -> -1),
      Map("X" -> -1, "O" -> 1),
      Map("X" -> 0,  "O" -> 0),
    )
    val mr = MatchResult(results)
    val s  = mr.summary("X", "O")
    s shouldBe "Games: 4  X wins: 2 (50.0%)  O wins: 1 (25.0%)  Draws: 1 (25.0%)"
  }

  it should "report correct totals via winsFor, lossesFor, drawsFor" in {
    val mr = makeResult(p1Wins = 3, p2Wins = 2, draws = 1)
    mr.total          shouldBe 6
    mr.winsFor(true)  shouldBe 3
    mr.winsFor(false) shouldBe 2
    mr.drawsFor(true) shouldBe 1
    mr.lossesFor(true)  shouldBe 2
    mr.lossesFor(false) shouldBe 3
  }
}