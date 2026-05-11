package com.phasmidsoftware.decisiontree.examples.tictactoe

import com.phasmidsoftware.decisiontree.game.MCTSPlayer
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import scala.util.Random

class TicTacToeDemoSpec extends AnyFlatSpec with should.Matchers {

  // Use a fresh Random per test to avoid state leakage between tests.
  private def rng(seed: Long = 42L) = new Random(seed)

  // ---------------------------------------------------------------------------
  // Result is always defined
  // ---------------------------------------------------------------------------

  behavior of "playTicTacToeDemo"

  it should "always return a defined result" in {
    val result = playTicTacToeDemo(new RandomPlayer, "Random", new RandomPlayer, "Random", rng())
    result shouldBe defined
  }

  it should "return Some(true) or Some(false) — never None" in {
    val result = playTicTacToeDemo(new HeuristicPlayer, "Heuristic", new RandomPlayer, "Random", rng(1L))
    result should (equal(Some(true)) or equal(Some(false)))
  }

  // ---------------------------------------------------------------------------
  // Perfect player never loses
  // ---------------------------------------------------------------------------

  it should "never return a loss for PerfectPlayer as X" in {
    val result = playTicTacToeDemo(new PerfectPlayer, "Perfect", new RandomPlayer, "Random", rng(2L))
    result should not equal Some(false)
  }

  it should "never return a loss for PerfectPlayer as O" in {
    val result = playTicTacToeDemo(new RandomPlayer, "Random", new PerfectPlayer, "Perfect", rng(3L))
    result should not equal Some(true)
  }

  it should "return a draw when PerfectPlayer plays PerfectPlayer" in {
    val result = playTicTacToeDemo(new PerfectPlayer, "Perfect", new PerfectPlayer, "Perfect", rng(4L))
    result shouldBe Some(false)
  }

  // ---------------------------------------------------------------------------
  // MCTS player
  // ---------------------------------------------------------------------------

  it should "return a defined result for MCTS vs Random" in {
    val mctsX = MCTSPlayer[Board, TicTacToe, Int, Boolean](me = true, iterations = 300)
    val result = playTicTacToeDemo(mctsX, "MCTS", new RandomPlayer, "Random", rng(5L))
    result shouldBe defined
  }

  it should "return a defined result for MCTS vs MCTS" in {
    val mctsX = MCTSPlayer[Board, TicTacToe, Int, Boolean](me = true, iterations = 300)
    val mctsO = MCTSPlayer[Board, TicTacToe, Int, Boolean](me = false, iterations = 300)
    val result = playTicTacToeDemo(mctsX, "MCTS", mctsO, "MCTS", rng(6L))
    result shouldBe defined
  }

  // ---------------------------------------------------------------------------
  // Heuristic player
  // ---------------------------------------------------------------------------

  it should "return a defined result for Heuristic vs Heuristic" in {
    val result = playTicTacToeDemo(
      new HeuristicPlayer, "Heuristic",
      new HeuristicPlayer, "Heuristic",
      rng(7L)
    )
    result shouldBe defined
  }
}