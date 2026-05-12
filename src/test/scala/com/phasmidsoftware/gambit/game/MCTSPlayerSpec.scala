package com.phasmidsoftware.gambit.game

import com.phasmidsoftware.gambit.examples.connect4.Connect4State.given
import com.phasmidsoftware.gambit.examples.connect4.{Connect4, Connect4GameRunner, Connect4State, connect4Game, HeuristicPlayer as C4HeuristicPlayer, RandomPlayer as C4RandomPlayer}
import com.phasmidsoftware.gambit.examples.tictactoe.TicTacToe.TicTacToeState$
import com.phasmidsoftware.gambit.examples.tictactoe.{Board, TicTacToe, TicTacToeGameRunner, tictactoeGame, RandomPlayer as TTTRandomPlayer}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should
import org.scalatest.tagobjects.Slow

import scala.util.Random

class MCTSPlayerSpec extends AnyFlatSpec with should.Matchers {

  // ---------------------------------------------------------------------------
  // TicTacToe MCTS
  // ---------------------------------------------------------------------------

  behavior of "MCTSPlayer on TicTacToe"

  it should "choose a valid move from the starting position" in {
    val mcts = MCTSPlayer[Board, TicTacToe, Int, Boolean](
      me = true, iterations = 200
    )
    val move = mcts.chooseMove(TicTacToe.start, new Random(1L))
    move shouldBe defined
    move.get should (be >= 0 and be <= 8)
  }

  it should "return None for a terminal TicTacToe position" in {
    val xWin = TicTacToe.parse("XXX- 0 -0  ").get
    val mcts = MCTSPlayer[Board, TicTacToe, Int, Boolean](me = true, iterations = 100)
    mcts.chooseMove(xWin, new Random(1L)) shouldBe None
  }

  it should "never lose as X against a random player" in {
    val mcts   = MCTSPlayer[Board, TicTacToe, Int, Boolean](me = true, iterations = 300)
    val runner = TicTacToeGameRunner(mcts, new TTTRandomPlayer, new Random(1L))
    val stats  = runner.playGames(50)
    stats.winsFor(false) shouldBe 0
  }

  it should "never lose as O against a random player" in {
    val mcts   = MCTSPlayer[Board, TicTacToe, Int, Boolean](me = false, iterations = 300)
    val runner = TicTacToeGameRunner(new TTTRandomPlayer, mcts, new Random(2L))
    val stats  = runner.playGames(50)
    stats.winsFor(true) shouldBe 0
  }

  it should "draw most games against itself" in {
    val mctsX  = MCTSPlayer[Board, TicTacToe, Int, Boolean](me = true,  iterations = 500)
    val mctsO  = MCTSPlayer[Board, TicTacToe, Int, Boolean](me = false, iterations = 500)
    val runner = TicTacToeGameRunner(mctsX, mctsO, new Random(3L))
    val stats  = runner.playGames(20)
    // With 500 iterations, MCTS should draw the vast majority —
    // perfect play (which always draws) requires more iterations.
    stats.drawsFor(true) should be > 15
  }

  it should "improve with more iterations — fewer losses against random" in {
    val rng = new Random(4L)

    // Low iterations baseline.
    val weakMcts  = MCTSPlayer[Board, TicTacToe, Int, Boolean](me = true, iterations = 10)
    val weakRunner = TicTacToeGameRunner(weakMcts, new TTTRandomPlayer, rng)
    val weakStats  = weakRunner.playGames(50)

    // High iterations.
    val strongMcts  = MCTSPlayer[Board, TicTacToe, Int, Boolean](me = true, iterations = 500)
    val strongRunner = TicTacToeGameRunner(strongMcts, new TTTRandomPlayer, rng)
    val strongStats  = strongRunner.playGames(50)

    // Strong should lose no more than weak.
    strongStats.winsFor(false) should be <= weakStats.winsFor(false)
  }

  // ---------------------------------------------------------------------------
  // Connect4 MCTS
  // ---------------------------------------------------------------------------

  behavior of "MCTSPlayer on Connect4"

  it should "choose a valid column from the starting position" in {
    val mcts = MCTSPlayer[Connect4, Connect4, Int, Boolean](
      me = true, iterations = 200
    )
    val move = mcts.chooseMove(Connect4.start, new Random(1L))
    move shouldBe defined
    move.get should (be >= 0 and be <= Connect4.cols - 1)
    Connect4.start.open should contain(move.get)
  }

  it should "return None for a terminal Connect4 position" in {
    val win = Connect4.start
      .play(0, isX = true).play(4, isX = false)
      .play(1, isX = true).play(4, isX = false)
      .play(2, isX = true).play(4, isX = false)
      .play(3, isX = true)
    Connect4State.isGoal(win) shouldBe Some(true)
    val mcts = MCTSPlayer[Connect4, Connect4, Int, Boolean](me = true, iterations = 100)
    mcts.chooseMove(win, new Random(1L)) shouldBe None
  }

  it should "win more than it loses as X against a random player" in {
    val mcts   = MCTSPlayer[Connect4, Connect4, Int, Boolean](me = true, iterations = 200)
    val runner = Connect4GameRunner(mcts, new C4RandomPlayer, new Random(5L))
    val stats  = runner.playGames(20)
    stats.winsFor(true) should be > stats.lossesFor(true)
  }

  it should "win more than it loses as O against a random player" in {
    val mcts   = MCTSPlayer[Connect4, Connect4, Int, Boolean](me = false, iterations = 200)
    val runner = Connect4GameRunner(new C4RandomPlayer, mcts, new Random(6L))
    val stats  = runner.playGames(20)
    stats.winsFor(false) should be > stats.lossesFor(false)
  }

  it should "be competitive against HeuristicPlayer" in {
    val mcts      = MCTSPlayer[Connect4, Connect4, Int, Boolean](me = true, iterations = 500)
    val heuristic = new C4HeuristicPlayer
    val runner    = Connect4GameRunner(mcts, heuristic, new Random(7L))
    val stats     = runner.playGames(10)
    // MCTS with 500 iterations should not be completely dominated.
    stats.winsFor(true) + stats.drawsFor(true) should be > 0
  }
  // ---------------------------------------------------------------------------
  // Tree reuse
  // ---------------------------------------------------------------------------

  behavior of "MCTSPlayer tree reuse"

  it should "return consistent moves across calls on the same player instance" in {
    // The same MCTSPlayer instance is reused across multiple chooseMove calls
    // within a game. Tree reuse must not corrupt state between calls.
    val mcts = MCTSPlayer[Connect4, Connect4, Int, Boolean](me = true, iterations = 100)
    val rng = new Random(42L)
    var s = Connect4.start
    // Play 6 moves (3 each), reusing the same mcts instance throughout.
    for i <- 0 until 6 do
      val isX = i % 2 == 0
      if isX then
        val move = mcts.chooseMove(s, rng)
        move shouldBe defined
        s.open should contain(move.get)
        s = s.play(move.get, isX = true)
      else
        // Opponent plays randomly.
        s = s.play(s.open(rng.nextInt(s.open.size)), isX = false)
    // After 6 moves the board should have 6 pieces.
    (java.lang.Long.bitCount(s.xBits) + java.lang.Long.bitCount(s.oBits)) shouldBe 6
  }

  it should "still choose a valid move after a cache miss (opponent plays unexplored line)" in {
    // Force a cache miss by giving the opponent a fresh random player whose
    // move may not have been explored in the previous search.
    val mcts = MCTSPlayer[Connect4, Connect4, Int, Boolean](me = true, iterations = 50)
    val rng = new Random(99L)
    var s = Connect4.start
    // Move 1: MCTS picks for X.
    val m1 = mcts.chooseMove(s, rng)
    m1 shouldBe defined
    s = s.play(m1.get, isX = true)
    // Opponent plays col 0 regardless (likely unexplored at depth 2).
    s = s.play(0, isX = false)
    // Move 2: MCTS must still return a valid move after the potential cache miss.
    val m2 = mcts.chooseMove(s, rng)
    m2 shouldBe defined
    s.open should contain(m2.get)
  }

  it should "produce a valid game with tree reuse enabled" taggedAs Slow in {
    // Play a complete game using the same MCTS instance; verify it terminates
    // and produces a valid result (no exception, board makes sense).
    val mcts = MCTSPlayer[Connect4, Connect4, Int, Boolean](me = true, iterations = 50)
    val runner = Connect4GameRunner(mcts, new C4RandomPlayer, new Random(11L))
    val result = runner.playGame()
    result.keys should contain(true)
    result.keys should contain(false)
    result(true) + result(false) shouldBe 0 // zero-sum
  }
}