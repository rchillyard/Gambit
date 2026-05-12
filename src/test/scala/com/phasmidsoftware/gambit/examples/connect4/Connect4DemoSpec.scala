package com.phasmidsoftware.gambit.examples.connect4

import com.phasmidsoftware.gambit.examples.connect4.Connect4State.given
import com.phasmidsoftware.gambit.game.MCTSPlayer
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should
import org.scalatest.tagobjects.Slow

import scala.util.Random

class Connect4DemoSpec extends AnyFlatSpec with should.Matchers {

  private val rng = new Random(42L)

  // ---------------------------------------------------------------------------
  // Result is always defined
  // ---------------------------------------------------------------------------

  behavior of "playConnect4Demo"

  it should "always return a defined result" in {
    val result = playConnect4Demo(new RandomPlayer, "Random", new RandomPlayer, "Random", rng)
    result shouldBe defined
  }

  it should "return Some(true) or Some(false) — never None" in {
    val result = playConnect4Demo(new HeuristicPlayer, "Heuristic", new RandomPlayer, "Random", rng)
    result should (equal(Some(true)) or equal(Some(false)))
  }

  // ---------------------------------------------------------------------------
  // Heuristic player
  // ---------------------------------------------------------------------------

  it should "return a defined result for Heuristic vs Heuristic" in {
    val result = playConnect4Demo(
      new HeuristicPlayer, "Heuristic",
      new HeuristicPlayer, "Heuristic",
      rng
    )
    result shouldBe defined
  }

  it should "return a defined result for Heuristic vs Random" in {
    val result = playConnect4Demo(
      new HeuristicPlayer, "Heuristic",
      new RandomPlayer, "Random",
      rng
    )
    result shouldBe defined
  }

  // ---------------------------------------------------------------------------
  // MCTS player
  // ---------------------------------------------------------------------------

  it should "return a defined result for MCTS vs Random" taggedAs Slow in {
    val mcts = MCTSPlayer[Connect4, Connect4, Int, Boolean](me = true, iterations = 200)
    val result = playConnect4Demo(mcts, "MCTS", new RandomPlayer, "Random", rng)
    result shouldBe defined
  }

  it should "return a defined result for MCTS vs Heuristic" taggedAs Slow in {
    val mctsX = MCTSPlayer[Connect4, Connect4, Int, Boolean](me = true, iterations = 200)
    val result = playConnect4Demo(mctsX, "MCTS", new HeuristicPlayer, "Heuristic", rng)
    result shouldBe defined
  }

  it should "return a defined result for MCTS vs MCTS" taggedAs Slow in {
    val mctsX = MCTSPlayer[Connect4, Connect4, Int, Boolean](me = true, iterations = 200)
    val mctsO = MCTSPlayer[Connect4, Connect4, Int, Boolean](me = false, iterations = 200)
    val result = playConnect4Demo(mctsX, "MCTS", mctsO, "MCTS", rng)
    result shouldBe defined
  }
}