package com.phasmidsoftware.gambit.examples.connect4

import com.phasmidsoftware.gambit.examples.connect4.Connect4State.given
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import scala.util.Random

class Connect4GameSpec extends AnyFlatSpec with should.Matchers {

  private val start = Connect4.start
  private val rng = new Random(42L)

  // ---------------------------------------------------------------------------
  // RandomPlayer
  // ---------------------------------------------------------------------------

  behavior of "RandomPlayer"

  it should "choose a valid column from the starting position" in {
    val player = new RandomPlayer
    val move = player.chooseMove(start, rng)
    move shouldBe defined
    move.get should (be >= 0 and be <= Connect4.cols - 1)
    start.open should contain(move.get)
  }

  it should "return None for a terminal position" in {
    // X wins horizontally.
    val win = start
      .play(0, isX = true).play(4, isX = false)
      .play(1, isX = true).play(4, isX = false)
      .play(2, isX = true).play(4, isX = false)
      .play(3, isX = true)
    Connect4State.isGoal(win) shouldBe Some(true)
    new RandomPlayer().chooseMove(win, rng) shouldBe None
  }

  it should "only choose from open columns" in {
    // Fill column 0 completely.
    var s = start
    for _ <- 0 until Connect4.rows do s = s.play(0, isX = s.player)
    s.open should not contain 0
    val player = new RandomPlayer
    (1 to 50).foreach { _ =>
      val move = player.chooseMove(s, rng)
      move shouldBe defined
      move.get should not be 0
    }
  }

  // ---------------------------------------------------------------------------
  // HeuristicPlayer
  // ---------------------------------------------------------------------------

  behavior of "HeuristicPlayer"

  it should "choose a valid column from the starting position" in {
    val player = new HeuristicPlayer
    val move = player.chooseMove(start, rng)
    move shouldBe defined
    start.open should contain(move.get)
  }

  it should "return None for a terminal position" in {
    val win = start
      .play(0, isX = true).play(4, isX = false)
      .play(1, isX = true).play(4, isX = false)
      .play(2, isX = true).play(4, isX = false)
      .play(3, isX = true)
    new HeuristicPlayer().chooseMove(win, rng) shouldBe None
  }

  it should "take a winning move when available" in {
    // X has 3 in a row at cols 0,1,2 row 0 — can win at col 3.
    val almostWin = start
      .play(0, isX = true).play(6, isX = false)
      .play(1, isX = true).play(6, isX = false)
      .play(2, isX = true).play(6, isX = false)
    val player = new HeuristicPlayer
    val move = player.chooseMove(almostWin, rng)
    move shouldBe Some(3)
  }

  it should "prefer the centre column from the starting position" in {
    // Center column (3) is the strongest opening move in Connect Four.
    val player = new HeuristicPlayer
    val move = player.chooseMove(start, rng)
    move shouldBe Some(3)
  }

  // ---------------------------------------------------------------------------
  // Connect4GameRunner
  // ---------------------------------------------------------------------------

  behavior of "Connect4GameRunner"

  it should "play a single game without throwing" in {
    val runner = Connect4GameRunner(new RandomPlayer, new RandomPlayer, new Random(1L))
    noException should be thrownBy runner.playGame()
  }

  it should "return a valid GameResult" in {
    val runner = Connect4GameRunner(new RandomPlayer, new RandomPlayer, new Random(2L))
    val result = runner.playGame()
    result.keys should contain(true)
    result.keys should contain(false)
    // Zero-sum: scores sum to 0 (win/loss) or both 0 (draw).
    val sum = result.values.sum
    sum should (be(0) or be(0))
    result(true) should (be(-1) or be(0) or be(1))
    result(false) should (be(-1) or be(0) or be(1))
  }

  it should "accumulate correct totals over multiple games" in {
    val runner = Connect4GameRunner(new RandomPlayer, new RandomPlayer, new Random(3L))
    val stats = runner.playGames(100)
    stats.total shouldBe 100
    stats.winsFor(true) + stats.winsFor(false) + stats.drawsFor(true) shouldBe 100
  }

  it should "never produce a result where both players win" in {
    val runner = Connect4GameRunner(new RandomPlayer, new RandomPlayer, new Random(4L))
    val stats = runner.playGames(50)
    stats.results.foreach { result =>
      (result(true) == 1 && result(false) == 1) shouldBe false
    }
  }

  // ---------------------------------------------------------------------------
  // HeuristicPlayer vs RandomPlayer
  // ---------------------------------------------------------------------------

  behavior of "HeuristicPlayer vs RandomPlayer"

  it should "win more than it loses as X against a random player" in {
    val runner = Connect4GameRunner(new HeuristicPlayer, new RandomPlayer, new Random(5L))
    val stats = runner.playGames(50)
    stats.winsFor(true) should be > stats.lossesFor(true)
  }

  it should "win more than it loses as O against a random player" in {
    val runner = Connect4GameRunner(new RandomPlayer, new HeuristicPlayer, new Random(6L))
    val stats = runner.playGames(50)
    stats.winsFor(false) should be > stats.lossesFor(false)
  }

  // ---------------------------------------------------------------------------
  // HeuristicPlayer vs HeuristicPlayer
  // ---------------------------------------------------------------------------

  behavior of "HeuristicPlayer vs HeuristicPlayer"

  it should "play a full game without throwing" in {
    val runner = Connect4GameRunner(new HeuristicPlayer, new HeuristicPlayer, new Random(7L))
    noException should be thrownBy runner.playGame()
  }

  it should "always produce a valid result" in {
    val runner = Connect4GameRunner(new HeuristicPlayer, new HeuristicPlayer, new Random(8L))
    val stats = runner.playGames(10)
    stats.total shouldBe 10
    stats.winsFor(true) + stats.winsFor(false) + stats.drawsFor(true) shouldBe 10
  }
}