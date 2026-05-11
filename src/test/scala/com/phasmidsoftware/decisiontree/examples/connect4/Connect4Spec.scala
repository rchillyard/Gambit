package com.phasmidsoftware.decisiontree.examples.connect4

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

class Connect4Spec extends AnyFlatSpec with should.Matchers {

  private val start = Connect4.start

  // ---------------------------------------------------------------------------
  // Basic structure
  // ---------------------------------------------------------------------------

  behavior of "Connect4 structure"

  it should "have the correct dimensions" in {
    Connect4.cols shouldBe 7
    Connect4.rows shouldBe 6
    Connect4.stride shouldBe 7
  }

  it should "start with an empty board" in {
    start.xBits shouldBe 0L
    start.oBits shouldBe 0L
    start.heights shouldBe Vector.fill(7)(0)
  }

  it should "have all columns open at start" in {
    start.open shouldBe Seq(0, 1, 2, 3, 4, 5, 6)
  }

  it should "report player false (O just moved) for empty board" in {
    // 0 pieces total: 0 % 2 == 0, so player = false (X is about to move).
    start.player shouldBe false
  }

  // ---------------------------------------------------------------------------
  // play
  // ---------------------------------------------------------------------------

  behavior of "Connect4.play"

  it should "place X in the bottom row of a column" in {
    val s = start.play(0, isX = true)
    // Bit 0 = col 0, row 0.
    (s.xBits & 1L) shouldBe 1L
    s.oBits shouldBe 0L
    s.heights(0) shouldBe 1
  }

  it should "stack pieces correctly in a column" in {
    val s = start
      .play(3, isX = true) // row 0
      .play(3, isX = false) // row 1
      .play(3, isX = true) // row 2
    s.heights(3) shouldBe 3
    val base = 3 * Connect4.stride
    (s.xBits >> base & 1L) shouldBe 1L // row 0: X
    (s.oBits >> (base + 1) & 1L) shouldBe 1L // row 1: O
    (s.xBits >> (base + 2) & 1L) shouldBe 1L // row 2: X
  }

  it should "not affect other columns when playing" in {
    val s = start.play(2, isX = true)
    s.heights(0) shouldBe 0
    s.heights(1) shouldBe 0
    s.heights(3) shouldBe 0
  }

  it should "track player correctly after moves" in {
    val s1 = start.play(0, isX = true)
    s1.player shouldBe true // X just moved
    val s2 = s1.play(1, isX = false)
    s2.player shouldBe false // O just moved
  }

  // ---------------------------------------------------------------------------
  // open columns
  // ---------------------------------------------------------------------------

  behavior of "Connect4.open"

  it should "remove a column from open when it is full" in {
    var s = start
    for _ <- 0 until Connect4.rows do s = s.play(0, isX = s.player)
    s.open should not contain 0
    s.open should have size Connect4.cols - 1
  }

  it should "report isFull when all columns are filled" in {
    var s = start
    for col <- 0 until Connect4.cols do
      for _ <- 0 until Connect4.rows do
        s = s.play(col, isX = s.player)
    s.isFull shouldBe true
    s.open shouldBe empty
  }

  // ---------------------------------------------------------------------------
  // winner detection
  // ---------------------------------------------------------------------------

  behavior of "Connect4.winner"

  it should "return None for an empty board" in {
    start.winner shouldBe None
  }

  it should "detect a horizontal win for X" in {
    // X fills the bottom row of columns 0-3.
    val s = start
      .play(0, isX = true).play(4, isX = false)
      .play(1, isX = true).play(4, isX = false)
      .play(2, isX = true).play(4, isX = false)
      .play(3, isX = true)
    s.winner shouldBe Some(true)
  }

  it should "detect a vertical win for X" in {
    // X stacks 4 in column 0, O plays elsewhere.
    val s = start
      .play(0, isX = true).play(1, isX = false)
      .play(0, isX = true).play(1, isX = false)
      .play(0, isX = true).play(1, isX = false)
      .play(0, isX = true)
    s.winner shouldBe Some(true)
  }

  it should "detect a horizontal win for O" in {
    // O fills columns 1-4 at row 0, X plays column 0.
    val s = start
      .play(0, isX = true).play(1, isX = false)
      .play(0, isX = true).play(2, isX = false)
      .play(0, isX = true).play(3, isX = false)
      .play(6, isX = true).play(4, isX = false)
    s.winner shouldBe Some(false)
  }

  it should "detect a vertical win for O" in {
    val s = start
      .play(0, isX = true).play(1, isX = false)
      .play(0, isX = true).play(1, isX = false)
      .play(0, isX = true).play(1, isX = false)
      .play(2, isX = true).play(1, isX = false)
    s.winner shouldBe Some(false)
  }

  it should "detect a diagonal win (/) for X" in {
    // X wins on the / diagonal: (0,0),(1,1),(2,2),(3,3).
    val s = start
      .play(0, isX = true) // (0,0)
      .play(1, isX = false).play(1, isX = true) // (1,1)
      .play(2, isX = false).play(2, isX = false).play(2, isX = true) // (2,2)
      .play(3, isX = false).play(3, isX = false).play(3, isX = false).play(3, isX = true) // (3,3)
    s.winner shouldBe Some(true)
  }

  it should "detect a diagonal win (\\ ) for X" in {
    // X wins on the \ diagonal: (3,0),(2,1),(1,2),(0,3).
    val s = start
      .play(3, isX = true) // (3,0)
      .play(2, isX = false).play(2, isX = true) // (2,1)
      .play(1, isX = false).play(1, isX = false).play(1, isX = true) // (1,2)
      .play(0, isX = false).play(0, isX = false).play(0, isX = false).play(0, isX = true) // (0,3)
    s.winner shouldBe Some(true)
  }

  it should "not detect a win when there are only 3 in a row" in {
    val s = start
      .play(0, isX = true).play(4, isX = false)
      .play(1, isX = true).play(4, isX = false)
      .play(2, isX = true).play(4, isX = false)
    s.winner shouldBe None
  }

  it should "not wrap horizontal win detection across columns" in {
    // Verify the sentinel bits prevent wrap-around.
    // Fill right end of one row and left end of next — should not be a win.
    val s = start
      .play(4, isX = true).play(0, isX = false)
      .play(5, isX = true).play(0, isX = false)
      .play(6, isX = true).play(0, isX = false)
      // Now play bottom of col 0 with X — would wrap without sentinel.
      .play(1, isX = false) // O plays to keep parity
    s.winner shouldBe None
  }

  // ---------------------------------------------------------------------------
  // render
  // ---------------------------------------------------------------------------

  behavior of "Connect4.render"

  it should "render an empty board correctly" in {
    val expected =
      ".......\n" +
        ".......\n" +
        ".......\n" +
        ".......\n" +
        ".......\n" +
        ".......\n"
    start.render shouldBe expected
  }

  it should "render X at bottom-left correctly" in {
    val s = start.play(0, isX = true)
    val lines = s.render.split('\n')
    lines(5) shouldBe "X......" // bottom row
    lines(0) shouldBe "......." // top row
  }

  it should "render a stacked column correctly" in {
    val s = start
      .play(3, isX = true)
      .play(3, isX = false)
    val lines = s.render.split('\n')
    lines(5)(3) shouldBe 'X' // bottom
    lines(4)(3) shouldBe '0' // above
    lines(3)(3) shouldBe '.' // empty above that
  }

  // ---------------------------------------------------------------------------
  // parse
  // ---------------------------------------------------------------------------

  behavior of "Connect4.parse"

  it should "parse an empty board" in {
    val s = Connect4.parse("." * 42)
    s shouldBe start
  }

  it should "round-trip play/render/parse" in {
    val s = start
      .play(0, isX = true)
      .play(1, isX = false)
      .play(3, isX = true)
    val rendered = s.render
    val parsed = Connect4.parse(rendered)
    parsed.xBits shouldBe s.xBits
    parsed.oBits shouldBe s.oBits
    parsed.heights shouldBe s.heights
  }

  it should "fail on wrong length input" in {
    an[IllegalArgumentException] should be thrownBy Connect4.parse("X" * 10)
  }
}