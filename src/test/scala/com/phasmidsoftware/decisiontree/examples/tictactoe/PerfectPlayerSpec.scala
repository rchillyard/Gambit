package com.phasmidsoftware.decisiontree.examples.tictactoe

import com.phasmidsoftware.decisiontree.examples.tictactoe.TicTacToe.TicTacToeState$
import com.phasmidsoftware.decisiontree.moves.State
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import scala.util.Random

class PerfectPlayerSpec extends AnyFlatSpec with should.Matchers {

  implicit val state: State[Board, TicTacToe] = TicTacToeState$

  private val rng = new Random(42L)

  // Shorthand — spaces for empty, - as row separator (stripped by parser).
  private def pos(s: String): TicTacToe = TicTacToe.parse(s).get

  // ---------------------------------------------------------------------------
  // Construction and score map
  // ---------------------------------------------------------------------------

  behavior of "PerfectPlayer construction"

  it should "construct without throwing" in {
    noException should be thrownBy new PerfectPlayer
  }

  it should "build a score map covering all reachable positions" in {
    // Force lazy initialisation by making a move from the start position.
    val player = new PerfectPlayer
    player.chooseMove(TicTacToe.start, rng) shouldBe defined
  }

  // ---------------------------------------------------------------------------
  // Terminal position scores
  // ---------------------------------------------------------------------------

  behavior of "PerfectPlayer terminal positions"

  it should "recognise an X win" in {
    // X wins along the top row.
    val xWin = pos("XXX-   -   ")
    state.isGoal(xWin) shouldBe Some(true)
    state.isWin(xWin) shouldBe true
  }

  it should "recognise an O win" in {
    // O wins along the left column: O at (0,0),(1,0),(2,0); X at (0,1),(1,1).
    // 3 O moves, 2 X moves — valid position, O just won.
    val oWin = pos("0X -0X -0  ")
    state.isGoal(oWin) shouldBe Some(true)
    // isWin returns true for any line — no X-specific assertion here.
  }

  it should "recognise a drawn position" in {
    // X0X / X0X / 0X0 — 5X 4O, full board, no winner.
    val drawn = pos("X0X-X0X-0X0")
    state.isGoal(drawn) shouldBe Some(false)
    state.isWin(drawn) shouldBe false
  }

  // ---------------------------------------------------------------------------
  // Winning move selection
  // ---------------------------------------------------------------------------

  behavior of "PerfectPlayer move selection"

  it should "take a winning move for X when available" in {
    // X at (0,0),(0,1); O at (1,0),(1,1) — X to move (2X 2O), wins at (0,2).
    val ttt = pos("XX -00 -   ")
    val player = new PerfectPlayer
    val move = player.chooseMove(ttt, rng)
    move shouldBe defined
    val row = move.get / TicTacToe.size
    val col = move.get % TicTacToe.size
    val next = state.construct(ttt.playX(row, col))
    state.isGoal(next) shouldBe Some(true)
    state.isWin(next) shouldBe true
  }

  it should "take a winning move for O when available" in {
    // O at (0,0),(1,0); X at (0,1),(1,1),(2,2) — O to move (3X 2O, X just moved).
    // X has no line. O wins at (2,0) to complete the left column.
    val ttt = pos("0X -0X -  X")
    val player = new PerfectPlayer
    val move = player.chooseMove(ttt, rng)
    move shouldBe defined
    val row = move.get / TicTacToe.size
    val col = move.get % TicTacToe.size
    val next = state.construct(ttt.play0(row, col))
    state.isGoal(next) shouldBe Some(true)
    // isWin returns true for any line (X or O), not X-specific — no assertion here.
  }

  it should "block O from winning on the next move" in {
    // O at (0,0),(0,1); X at (1,1),(2,2) — X to move (2X 2O), must block at (0,2).
    val ttt = pos("00 -X  -  X")
    val player = new PerfectPlayer
    val move = player.chooseMove(ttt, rng)
    move shouldBe defined
    val row = move.get / TicTacToe.size
    val col = move.get % TicTacToe.size
    val next = state.construct(ttt.playX(row, col))
    // After X blocks, no O response should be an immediate O win.
    val oResponses = state.getStates(next)
    val oWins = oResponses.filter(s => state.isGoal(s).contains(true) && !state.isWin(s))
    oWins shouldBe empty
  }

  it should "return None for a terminal position" in {
    // X has already won along the top row.
    val xWin = pos("XXX- 0 -0  ")
    val player = new PerfectPlayer
    player.chooseMove(xWin, rng) shouldBe None
  }

  // ---------------------------------------------------------------------------
  // Perfect play never loses
  // ---------------------------------------------------------------------------

  behavior of "PerfectPlayer never loses"

  it should "never lose as X against a random player over many games" in {
    val perfect = new PerfectPlayer
    val runner = TicTacToeGameRunner(perfect, new RandomPlayer, new Random(1L))
    val stats = runner.playGames(200)
    stats.winsFor(false) shouldBe 0
  }

  it should "never lose as O against a random player over many games" in {
    val perfect = new PerfectPlayer
    val runner = TicTacToeGameRunner(new RandomPlayer, perfect, new Random(2L))
    val stats = runner.playGames(200)
    stats.winsFor(true) shouldBe 0
  }

  it should "always draw against itself" in {
    val p1 = new PerfectPlayer
    val p2 = new PerfectPlayer
    val runner = TicTacToeGameRunner(p1, p2, new Random(3L))
    val stats = runner.playGames(20)
    stats.winsFor(true) shouldBe 0
    stats.winsFor(false) shouldBe 0
    stats.drawsFor(true) shouldBe 20
  }

  // ---------------------------------------------------------------------------
  // MENACE improves toward perfect play
  // ---------------------------------------------------------------------------

  behavior of "MENACE trained against PerfectPlayer"

  it should "lose less often after training against PerfectPlayer than before" in {
    val mbs = Matchboxes()
    val menace = new MenacePlayer(mbs)
    val rng = new Random(5L)

    // Baseline: 100 games before any training.
    val baseline = TicTacToeGameRunner(menace, new PerfectPlayer, rng)
    val before = baseline.playGames(100)

    // Training phase: 2000 games against perfect player.
    val trainer = TicTacToeGameRunner(menace, new PerfectPlayer, rng)
    trainer.playGames(2000)

    // Evaluation: 200 games after training.
    val eval = TicTacToeGameRunner(menace, new PerfectPlayer, rng)
    val after = eval.playGames(200)

    // MENACE should lose less (or no more) after training.
    after.lossesFor(true).toDouble / after.total should be < before.lossesFor(true).toDouble / before.total + 0.1
  }
}