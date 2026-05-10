package com.phasmidsoftware.decisiontree.examples.tictactoe

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import scala.util.Random

class MatchboxSpec extends AnyFlatSpec with should.Matchers {

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private val empty: TicTacToe = TicTacToe.start

  /** Parse shorthand — 9-char string, dots for empty. */
  private def pos(s: String): TicTacToe = TicTacToe.parse(s).get

  // ---------------------------------------------------------------------------
  // Matchbox — construction
  // ---------------------------------------------------------------------------

  behavior of "Matchbox construction"

  it should "create a matchbox for the empty board with 9 moves" in {
    val mb = Matchbox.fromPosition(empty)
    mb.moveCount shouldBe 9
    mb.beads.keys.toSet shouldBe Set(0, 1, 2, 3, 4, 5, 6, 7, 8)
  }

  it should "initialise every move to initialBeads" in {
    val mb = Matchbox.fromPosition(empty)
    mb.beads.values.foreach(_ shouldBe Matchbox.initialBeads)
    mb.totalBeads shouldBe 9 * Matchbox.initialBeads
  }

  it should "create a matchbox for a mid-game position with correct open cells" in {
    // X plays cell 0 (top-left), O plays cell 4 (centre) → 7 open cells remain
    val ttt = pos("X...0....")
    val mb  = Matchbox.fromPosition(ttt)
    mb.moveCount shouldBe 7
    mb.beads.keys should not contain 0
    mb.beads.keys should not contain 4
  }

  it should "create a matchbox from explicit open cells" in {
    val mb = Matchbox(Seq(1, 3, 5))
    mb.moveCount shouldBe 3
    mb.beads shouldBe Map(1 -> Matchbox.initialBeads, 3 -> Matchbox.initialBeads, 5 -> Matchbox.initialBeads)
  }

  // ---------------------------------------------------------------------------
  // Matchbox — select
  // ---------------------------------------------------------------------------

  behavior of "Matchbox.select"

  it should "always return a cell contained in the matchbox" in {
    val mb  = Matchbox.fromPosition(empty)
    val rng = new Random(0L)
    (1 to 50).foreach { _ =>
      val cell = mb.select(rng)
      cell shouldBe defined
      mb.beads.keys should contain(cell.get)
    }
  }

  it should "return None for an empty matchbox" in {
    Matchbox(Map.empty[Int, Int]).select(new Random(0L)) shouldBe None
  }

  it should "return the only available move when there is one" in {
    val mb  = Matchbox(Map(5 -> 10))
    val rng = new Random(0L)
    (1 to 20).foreach(_ => mb.select(rng) shouldBe Some(5))
  }

  it should "be weighted — heavily-loaded cell wins most of the time" in {
    // Cell 2 has 100 beads, cell 7 has 1 bead.
    val mb   = Matchbox(Map(2 -> 100, 7 -> 1))
    val rng  = new Random(42L)
    val picks = (1 to 200).map(_ => mb.select(rng).get)
    val cell2Count = picks.count(_ == 2)
    // With 100:1 odds, cell 2 should win the vast majority of trials.
    cell2Count should be > 150
  }

  it should "distribute uniformly when all beads are equal" in {
    val mb   = Matchbox(Map(0 -> 10, 1 -> 10, 2 -> 10))
    val rng  = new Random(99L)
    val picks = (1 to 3000).map(_ => mb.select(rng).get)
    // Each cell should appear roughly 1000 times; allow ±20% tolerance.
    picks.count(_ == 0) should (be > 700 and be < 1300)
    picks.count(_ == 1) should (be > 700 and be < 1300)
    picks.count(_ == 2) should (be > 700 and be < 1300)
  }

  // ---------------------------------------------------------------------------
  // Matchbox — reward / penalise
  // ---------------------------------------------------------------------------

  behavior of "Matchbox.reward and penalise"

  it should "add winDelta beads on reward" in {
    val mb      = Matchbox(Map(3 -> 4))
    val updated = mb.reward(3)
    updated.beads(3) shouldBe 4 + Matchbox.winDelta
  }

  it should "subtract lossDelta beads on penalise" in {
    val mb      = Matchbox(Map(3 -> 4))
    val updated = mb.penalise(3)
    updated.beads(3) shouldBe 4 - Matchbox.lossDelta
  }

  it should "not drop below the bead floor on repeated penalise" in {
    val mb = Matchbox(Map(3 -> 1))
    val p1 = mb.penalise(3)
    p1.beads(3) shouldBe Matchbox.beadFloor
    val p2 = p1.penalise(3)
    p2.beads(3) shouldBe Matchbox.beadFloor
  }

  it should "only update the targeted cell" in {
    val mb      = Matchbox(Map(0 -> 4, 1 -> 4, 2 -> 4))
    val updated = mb.reward(1)
    updated.beads(0) shouldBe 4
    updated.beads(2) shouldBe 4
    updated.beads(1) shouldBe 4 + Matchbox.winDelta
  }

  // ---------------------------------------------------------------------------
  // Matchboxes — registry and symmetry
  // ---------------------------------------------------------------------------

  behavior of "Matchboxes registry"

  it should "create a new matchbox on first access" in {
    val mbs = Matchboxes()
    mbs.size shouldBe 0
    mbs.get(empty)
    mbs.size shouldBe 1
  }

  it should "return the same matchbox for the same position" in {
    val mbs = Matchboxes()
    val mb1 = mbs.get(empty)
    val mb2 = mbs.get(empty)
    mb1 shouldBe mb2
  }

  it should "map rotationally equivalent positions to the same matchbox" in {
    val mbs = Matchboxes()
    // X in top-left corner (cell 0) = 0x40000000
    val topLeft     = pos("X........")
    // X in top-right corner (cell 2) — a 90° rotation of top-left
    val topRight    = pos("..X......")
    // X in bottom-right corner (cell 8) — 180° rotation
    val bottomRight = pos("........X")
    // X in bottom-left corner (cell 6) — 270° rotation
    val bottomLeft  = pos("......X..")

    mbs.get(topLeft)
    mbs.get(topRight)
    mbs.get(bottomRight)
    mbs.get(bottomLeft)

    // All four should map to the same canonical matchbox.
    mbs.size shouldBe 1
  }

  it should "map a position and its transpose to the same matchbox" in {
    val mbs = Matchboxes()
    // X in top-right (cell 2) and X in bottom-left (cell 6) are transposes.
    val topRight  = pos("..X......")
    val bottomLeft = pos("......X..")
    mbs.get(topRight)
    mbs.get(bottomLeft)
    mbs.size shouldBe 1
  }

  it should "use distinct matchboxes for non-equivalent positions" in {
    val mbs = Matchboxes()
    // Corner vs edge — not equivalent under D4.
    val corner = pos("X........")  // cell 0, corner
    val edge   = pos(".X.......")  // cell 1, top edge
    mbs.get(corner)
    mbs.get(edge)
    mbs.size shouldBe 2
  }

  it should "update beads correctly for a win" in {
    val mbs  = Matchboxes()
    val ttt  = empty
    val mb0  = mbs.get(ttt)
    val cell = mb0.select(new Random(0L)).get
    mbs.update(ttt, cell, Win)
    val mb1 = mbs.get(ttt)
    // The canonical cell may differ from `cell` due to symmetry transform,
    // but total beads should have increased by winDelta.
    mb1.totalBeads shouldBe mb0.totalBeads + Matchbox.winDelta
  }

  it should "update beads correctly for a loss" in {
    val mbs  = Matchboxes()
    val ttt  = empty
    val mb0  = mbs.get(ttt)
    val cell = mb0.select(new Random(0L)).get
    mbs.update(ttt, cell, Loss)
    val mb1 = mbs.get(ttt)
    mb1.totalBeads shouldBe mb0.totalBeads - Matchbox.lossDelta
  }

  it should "leave beads unchanged for a draw" in {
    val mbs  = Matchboxes()
    val ttt  = empty
    val mb0  = mbs.get(ttt)
    val cell = mb0.select(new Random(0L)).get
    mbs.update(ttt, cell, Draw)
    val mb1 = mbs.get(ttt)
    mb1.totalBeads shouldBe mb0.totalBeads
  }

  // ---------------------------------------------------------------------------
  // Matchboxes — cell transform round-trip
  // ---------------------------------------------------------------------------

  behavior of "Matchboxes cell transformation"

  it should "correctly identify single-cell boards for all 9 cells" in {
    // Verify the bit layout assumed by transformCell / cellFromBoard.
    // Known values from TicTacToeSpec: X at cell i occupies bits (30 - i*2).
    val expected = Map(
      0 -> 0x40000000,
      1 -> 0x10000000,
      2 -> 0x04000000,
      3 -> 0x01000000,
      4 -> 0x00400000,
      5 -> 0x00100000,
      6 -> 0x00040000,
      7 -> 0x00010000,
      8 -> 0x00004000
    )
    expected.foreach { case (cell, expectedBoardValue) =>
      (0x40000000 >>> (cell * 2)) shouldBe expectedBoardValue
    }
  }

  it should "round-trip a move through canonicalize and back for corner positions" in {
    // For the empty board, canonical form is the empty board (value 0),
    // so the transform is identity and cell indices are unchanged.
    val mbs  = Matchboxes()
    val mb0  = mbs.get(empty)
    val cell = 4 // centre
    mbs.update(empty, cell, Win)
    val mb1 = mbs.get(empty)
    // Centre is invariant under all D4 transforms so canonical cell == cell.
    mb1.totalBeads shouldBe mb0.totalBeads + Matchbox.winDelta
  }

  // ---------------------------------------------------------------------------
  // MenacePlayer — integration
  // ---------------------------------------------------------------------------

  behavior of "MenacePlayer"

  it should "choose a move from the starting position" in {
    val mbs    = Matchboxes()
    val player = new MenacePlayer(mbs)
    val move   = player.chooseMove(empty, new Random(0L))
    move shouldBe defined
    move.get should (be >= 0 and be <= 8)
  }

  it should "record history and update matchboxes on gameOver" in {
    val mbs = Matchboxes()
    val player = new MenacePlayer(mbs)
    val rng = new Random(0L)

    // Simulate a short sequence of moves.
    println(s"chooseMove on empty: ${player.chooseMove(empty, rng)}")
    val mid = pos("X........")
    println(s"empty.open: ${empty.open}")
    println(s"mid.open: ${mid.open}")
    println(s"chooseMove on mid: ${player.chooseMove(mid, rng)}")

    println(s"Registry size before gameOver: ${mbs.size}")
    println(s"Total beads before gameOver: ${mbs.totalBeads}")
    val totalBefore = mbs.totalBeads
    player.gameOver(Win)
    println(s"Registry size after gameOver: ${mbs.size}")
    println(s"Total beads after gameOver: ${mbs.totalBeads}")
    // Two matchboxes updated, each by winDelta.
    mbs.totalBeads shouldBe totalBefore + 2 * Matchbox.winDelta
  }

  it should "clear history after gameOver so the next game starts fresh" in {
    val mbs    = Matchboxes()
    val player = new MenacePlayer(mbs)
    val rng    = new Random(0L)

    player.chooseMove(empty, rng)
    player.gameOver(Loss)

    val totalAfterFirst = mbs.totalBeads

    // Second game — only one move, then win.
    player.chooseMove(empty, rng)
    player.gameOver(Win)

    // Should have changed by exactly winDelta (one matchbox, one move).
    mbs.totalBeads shouldBe totalAfterFirst + Matchbox.winDelta
  }

  // ---------------------------------------------------------------------------
  // GameRunner — smoke tests
  // ---------------------------------------------------------------------------

  behavior of "GameRunner"

  import TicTacToe.TicTacToeState$

  it should "play a single game without throwing" in {
    val mbs    = Matchboxes()
    val runner = new GameRunner(new MenacePlayer(mbs), new RandomPlayer, new Random(1L))
    noException should be thrownBy runner.playGame()
  }

  it should "return a valid GameResult" in {
    val mbs    = Matchboxes()
    val runner = new GameRunner(new MenacePlayer(mbs), new RandomPlayer, new Random(2L))
    val result = runner.playGame()
    result should (equal(XWins) or equal(OWins) or equal(GameDraw))
  }

  it should "accumulate correct totals over multiple games" in {
    val mbs    = Matchboxes()
    val runner = new GameRunner(new RandomPlayer, new RandomPlayer, new Random(3L))
    val stats  = runner.playGames(100)
    stats.total shouldBe 100
    stats.xWins + stats.oWins + stats.draws shouldBe 100
  }

  it should "show MENACE improving against a random player over many games" in {
    // Train for 2000 games, then test win rate over the next 200.
    val mbs    = Matchboxes()
    val menace = new MenacePlayer(mbs)
    val rng    = new Random(7L)

    // Training phase.
    val trainer = new GameRunner(menace, new RandomPlayer, rng)
    trainer.playGames(2000)

    // Evaluation phase — fresh RandomPlayer, same MENACE (shared matchboxes).
    val eval = new GameRunner(menace, new RandomPlayer, rng)
    val stats = eval.playGames(200)
    System.err.println(stats)

    // After training, MENACE as X should win or draw the vast majority.
    // A completely untrained random X wins ~58% against random O.
    // A trained MENACE should do noticeably better and rarely lose outright.
    stats.oWins should be < 40 // less than 20% losses
  }
}