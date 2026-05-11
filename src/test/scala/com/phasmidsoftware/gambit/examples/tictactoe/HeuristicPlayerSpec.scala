package com.phasmidsoftware.gambit.examples.tictactoe

import com.phasmidsoftware.gambit.examples.tictactoe.TicTacToe.TicTacToeState$
import com.phasmidsoftware.gambit.game.State
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import scala.util.Random

class HeuristicPlayerSpec extends AnyFlatSpec with should.Matchers {

  implicit val state: State[Board, TicTacToe] = TicTacToeState$

  private val rng = new Random(42L)

  // Shorthand — spaces for empty, - as row separator (stripped by parser).
  private def pos(s: String): TicTacToe = TicTacToe.parse(s).get

  // ---------------------------------------------------------------------------
  // Basic move selection
  // ---------------------------------------------------------------------------

  behavior of "HeuristicPlayer move selection"

  it should "return a move from the starting position" in {
    val player = new HeuristicPlayer
    val move = player.chooseMove(TicTacToe.start, rng)
    move shouldBe defined
    move.get should (be >= 0 and be <= 8)
  }

  it should "return None for a terminal position" in {
    // X wins along the top row.
    val xWin = pos("XXX- 0 -0  ")
    val player = new HeuristicPlayer
    player.chooseMove(xWin, rng) shouldBe None
  }

  it should "return a cell that is open on the board" in {
    val ttt = pos("X0 -X0 -   ")
    val player = new HeuristicPlayer
    val move = player.chooseMove(ttt, rng)
    move shouldBe defined
    val openCells = ttt.open.map { case (r, c) => r * TicTacToe.size + c }.toSet
    openCells should contain(move.get)
  }

  it should "take a winning move for X when available" in {
    // X at (0,0),(0,1); O at (1,0),(1,1) — X to move, wins at (0,2).
    val ttt = pos("XX -00 -   ")
    val player = new HeuristicPlayer
    val move = player.chooseMove(ttt, rng)
    move shouldBe defined
    val row = move.get / TicTacToe.size
    val col = move.get % TicTacToe.size
    val next = state.construct(ttt.playX(row, col))
    state.isGoal(next) shouldBe Some(true)
    state.isWin(next) shouldBe true
  }

  it should "block O from winning when O has two in a row" in {
    // O at (0,0),(0,1); X at (1,1),(2,2) — X to move, must block at (0,2).
    val ttt = pos("00 -X  -  X")
    val player = new HeuristicPlayer
    val move = player.chooseMove(ttt, rng)
    move shouldBe defined
    val row = move.get / TicTacToe.size
    val col = move.get % TicTacToe.size
    val next = state.construct(ttt.playX(row, col))
    // After X's move, O should not be able to win immediately.
    val oResponses = state.getStates(next)
    val oWins = oResponses.filter(s => state.isGoal(s).contains(true) && !state.isWin(s))
    oWins shouldBe empty
  }

  // ---------------------------------------------------------------------------
  // Performance vs RandomPlayer
  // ---------------------------------------------------------------------------

  behavior of "HeuristicPlayer vs RandomPlayer"

  it should "win more than it loses as X against a random player" in {
    val runner = TicTacToeGameRunner(new HeuristicPlayer, new RandomPlayer, new Random(1L))
    val stats = runner.playGames(200)
    // HeuristicPlayer should win more than it loses — not necessarily never losing.
    stats.winsFor(true) should be > stats.winsFor(false)
  }

  it should "win more than it loses as O against a random player" in {
    val runner = TicTacToeGameRunner(new RandomPlayer, new HeuristicPlayer, new Random(2L))
    val stats = runner.playGames(200)
    stats.winsFor(false) should be > stats.winsFor(true)
  }

  // ---------------------------------------------------------------------------
  // Performance vs PerfectPlayer
  // ---------------------------------------------------------------------------

  behavior of "HeuristicPlayer vs PerfectPlayer"

  it should "never beat PerfectPlayer as O" in {
    // PerfectPlayer as X should never lose.
    val runner = TicTacToeGameRunner(new PerfectPlayer, new HeuristicPlayer, new Random(3L))
    val stats = runner.playGames(100)
    stats.winsFor(false) shouldBe 0
  }

  it should "never beat PerfectPlayer as X" in {
    // PerfectPlayer as O should never lose.
    val runner = TicTacToeGameRunner(new HeuristicPlayer, new PerfectPlayer, new Random(4L))
    val stats = runner.playGames(100)
    stats.winsFor(true) shouldBe 0
  }

  // ---------------------------------------------------------------------------
  // Comparison with MenacePlayer
  // ---------------------------------------------------------------------------

  behavior of "HeuristicPlayer vs untrained MenacePlayer"

  it should "outperform an untrained MenacePlayer as X" in {
    val mbs = Matchboxes()
    val runner = TicTacToeGameRunner(new HeuristicPlayer, new MenacePlayer(mbs), new Random(5L))
    val stats = runner.playGames(200)
    // Heuristic should beat an untrained MENACE more often than it loses.
    stats.winsFor(true) should be > stats.winsFor(false)
  }
}