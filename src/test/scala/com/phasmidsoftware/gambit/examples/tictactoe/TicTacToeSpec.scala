package com.phasmidsoftware.gambit.examples.tictactoe

import com.phasmidsoftware.gambit.examples.tictactoe.TicTacToe.parseString
import com.phasmidsoftware.gambit.examples.tictactoe.TicTacToeOps.*
import com.phasmidsoftware.gambit.game.State
import org.scalatest.PrivateMethodTester
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import scala.util.{Failure, Success, Try}

class TicTacToeSpec extends AnyFlatSpec with should.Matchers with PrivateMethodTester {

  private val startingPosition: TicTacToe = TicTacToe()

  behavior of "TicTacToeOps"
  it should "open" in {
    TicTacToeOps.open(0) shouldBe Array(0, 1, 2, 3, 4, 5, 6, 7, 8)
    TicTacToeOps.open(0x40000000) shouldBe Array(1, 2, 3, 4, 5, 6, 7, 8)
    TicTacToeOps.open(0x0C000000) shouldBe Array(0, 1, 2, 3, 4, 5, 6, 7, 8)
    TicTacToeOps.open(0x40400000) shouldBe Array(1, 2, 3, 5, 6, 7, 8)
  }
  it should "render" in {
    renderWithNewlines(0x40000000) shouldBe "X..\n...\n...\n"
  }
  it should "play" in {
    val board1 = playBoard(0, true, 0, 0)
    board1 shouldBe 0x40000000
    val board2 = playBoard(board1, false, 2, 0)
    renderWithNewlines(board2) shouldBe "X..\n...\n0..\n"
    board2 shouldBe 0x40080000
  }
  // CONSIDER skipping the toHexString part
  it should "hFlip to hex string" in {
    hFlip(0xC0000000).toHexString shouldBe "c0000"
    hFlip(0x30000000).toHexString shouldBe "30000"
    hFlip(0x0C000000).toHexString shouldBe "c000"
    hFlip(0x03000000).toHexString shouldBe "3000000"
    hFlip(0x00C00000).toHexString shouldBe "c00000"
    hFlip(0x00300000).toHexString shouldBe "300000"
    hFlip(0x000C0000).toHexString shouldBe "c0000000"
    hFlip(0x00030000).toHexString shouldBe "30000000"
    hFlip(0x0000C000).toHexString shouldBe "c000000"
  }
  // CONSIDER skipping the toHexString part
  it should "rotate to hex string" in {
    rotateBoard(0xC0000000).toHexString shouldBe "c000000"
    rotateBoard(0x30000000).toHexString shouldBe "300000"
    rotateBoard(0x0C000000).toHexString shouldBe "c000"
    rotateBoard(0x03000000).toHexString shouldBe "30000000"
    rotateBoard(0x00C00000).toHexString shouldBe "c00000"
    rotateBoard(0x00300000).toHexString shouldBe "30000"
    rotateBoard(0x000C0000).toHexString shouldBe "c0000000"
    rotateBoard(0x00030000).toHexString shouldBe "3000000"
    rotateBoard(0x0000C000).toHexString shouldBe "c0000"
  }
  it should "transpose to Int using hFlip and rotate" in {
    rotateBoard(hFlip(0xC0000000)) shouldBe 0xC0000000
    rotateBoard(hFlip(0x30000000)) shouldBe 0x03000000
    rotateBoard(hFlip(0x0C000000)) shouldBe 0x000C0000
    rotateBoard(hFlip(0x03000000)) shouldBe 0x30000000
    rotateBoard(hFlip(0x00C00000)) shouldBe 0xc00000
    rotateBoard(hFlip(0x00300000)) shouldBe 0x30000
    rotateBoard(hFlip(0x000C0000)) shouldBe 0xc000000
    rotateBoard(hFlip(0x00030000)) shouldBe 0x300000
    rotateBoard(hFlip(0x0000C000)) shouldBe 0xc000
    rotateBoard(hFlip(0x44000000)) shouldBe 0x40040000

  }
  it should "transpose to Int" in {
    transposeBoard(0xC0000000) shouldBe 0xC0000000
    transposeBoard(0x30000000) shouldBe 0x03000000
    transposeBoard(0x0C000000) shouldBe 0x000C0000
    transposeBoard(0x03000000) shouldBe 0x30000000
    transposeBoard(0x00C00000) shouldBe 0xc00000
    transposeBoard(0x00300000) shouldBe 0x30000
    transposeBoard(0x000C0000) shouldBe 0xc000000
    transposeBoard(0x00030000) shouldBe 0x300000
    transposeBoard(0x0000C000) shouldBe 0xc000
    transposeBoard(0x44000000) shouldBe 0x40040000
  }
  it should "transpose to hex string" in {
    transposeBoard(0xC0000000).toHexString shouldBe "c0000000"
    transposeBoard(0x30000000).toHexString shouldBe "3000000"
    transposeBoard(0x0C000000).toHexString shouldBe "c0000"
    transposeBoard(0x03000000).toHexString shouldBe "30000000"
    transposeBoard(0x00C00000).toHexString shouldBe "c00000"
    transposeBoard(0x00300000).toHexString shouldBe "30000"
    transposeBoard(0x000C0000).toHexString shouldBe "c000000"
    transposeBoard(0x00030000).toHexString shouldBe "300000"
    transposeBoard(0x0000C000).toHexString shouldBe "c000"
  }
  it should "transpose to Int 2" in {
    transposeBoard(0xF0000000) shouldBe 0xC3000000
    transposeBoard(0x03C00000) shouldBe 0x30c00000
  }
  it should "get rows" in {
    row(0xC0000000, 0) shouldBe 0x30
    row(0x03000000, 1) shouldBe 0x30
    row(0x00300000, 1) shouldBe 0x03
    row(0x00030000, 2) shouldBe 0x0C
  }
  it should "get diag" in {
    diagonal(0xC0000000) shouldBe 0x30
    diagonal(0xC00000) shouldBe 0xc
    diagonal(0xC000) shouldBe 3
  }
  it should "rowLine" in {
    rowLine(0x00) shouldBe 0
    rowLine(0x15) shouldBe 1
    rowLine(0x2A) shouldBe 2
  }
  it should "parseString" in {
    parseString("X..X..X..", None).board.value shouldBe 0x41040000
    parseString("X..X...X.", None).board.value shouldBe 0x41010000
    parseString("X..X....X", None).board.value shouldBe 0x41004000
    parseString("XXX......", None).board.value shouldBe 0x54000000
    parseString("X.X......", None).board.value shouldBe 0x44000000
    parseString("0.0......", None).board.value shouldBe 0x88000000
    parseString("XX.......", None).board.value shouldBe 0x50000000
    parseString("00.......", None).board.value shouldBe 0xA0000000
    parseString(".XX......", None).board.value shouldBe 0x14000000
    parseString(".00......", None).board.value shouldBe 0x28000000
  }
  it should "rowLinePending" in {
    rowLinePending(0x00) shouldBe 0
    rowLinePending(0x11) shouldBe 1
    rowLinePending(0x22) shouldBe 2
    rowLinePending(0x14) shouldBe 1
    rowLinePending(0x28) shouldBe 2
    rowLinePending(0x05) shouldBe 1
    rowLinePending(0x0A) shouldBe 2
  }

  behavior of "TicTacToe"
  it should "row" in {
    val ty: Try[TicTacToe] = TicTacToe.parse("X   X   X")
    val rowMethod: PrivateMethod[Row] = PrivateMethod[Row](Symbol("rowTupled"))
    val invokeRow: Int => TicTacToe => Int = x => t => t invokePrivate rowMethod(x)
    ty.map(invokeRow(0)) should matchPattern { case Success(16) => }
    ty.map(invokeRow(1)) should matchPattern { case Success(4) => }
    ty.map(invokeRow(2)) should matchPattern { case Success(1) => }
  }

  it should "open" in {
    val ttt = startingPosition
    ttt.open.size shouldBe TicTacToe.size * TicTacToe.size
  }

  it should "play true, 0, 0" in {
    val t0 = startingPosition
    val b1: (Board, TicTacToe) = t0.play(xOrO = true)(0, 0)
    val bTs = implicitly[State[Board, TicTacToe]]
    val t1 = bTs.construct(b1)
    t1.open.size shouldBe TicTacToe.size * TicTacToe.size - 1
    t1.render() shouldBe "X..-...-...- (4.0)"
  }

  it should "play false, 1, 0" in {
    val t0 = startingPosition
    val b1 = t0.play(xOrO = false)(1, 0)
    val bTs = implicitly[State[Board, TicTacToe]]
    val t1 = bTs.construct(b1)
    t1.open.size shouldBe TicTacToe.size * TicTacToe.size - 1
    t1.render(true) shouldBe "\n...\n0..\n...\n (0.0)"
  }

  it should "play true, 1, 1" in {
    val t0 = startingPosition
    val (b1: Board, t: TicTacToe) = t0.play(xOrO = true)(0, 0)
    b1 shouldBe Board(1, 0x40000000)
    t shouldBe t0
  }

  it should "playX 0, 0 and play0 1, 0" in {
    val t0 = startingPosition
    val b1 = t0.playX(0, 0)
    val bTs = implicitly[State[Board, TicTacToe]]
    val t1 = bTs.construct(b1)
    val b2 = t1.play0(1, 0)
    val t2 = bTs.construct(b2)
    t2.open.size shouldBe TicTacToe.size * TicTacToe.size - 2
    t2.render() shouldBe "X..-0..-...- (0.0)"
  }

  it should "parse" in {
    val target = TicTacToe.parse("         ")
    target should matchPattern { case Success(TicTacToe.start) => }
    val start = target.get
    start shouldBe TicTacToe(Board(0, 0), 0)
    val topLeftCorner = start.playX(1, 0)
    println(topLeftCorner)
    val ty1 = TicTacToe.parse("X        ")
    ty1 should matchPattern { case Success(_) => }
    println(ty1)
    //        ty1.get shouldBe TicTacToe(Board(1,0),0)
    ty1.get shouldBe TicTacToe(startingPosition.playX(0, 0))
    val ty2 = TicTacToe.parse("X  0     ")
    ty2 should matchPattern { case Success(_) => }
    //        ty2.get shouldBe startingPosition.playX(0, 0).play0(1, 0)
  }

  it should "not parse" in {
    TicTacToe.parse("") should matchPattern { case Failure(_) => }
    TicTacToe.parse("        ") should matchPattern { case Failure(_) => }
    TicTacToe.parse("          ") should matchPattern { case Failure(_) => }
  }

  it should "win row" in {
    TicTacToe.parse("XXX      ").get.win shouldBe Some(true)
    TicTacToe.parse("   XXX   ").get.win shouldBe Some(true)
    TicTacToe.parse("      XXX").get.win shouldBe Some(true)
    TicTacToe.parse("0     XXX").get.win shouldBe Some(true)
    TicTacToe.parse("000   X  ").get.win shouldBe Some(false)
  }

  it should "win col" in {
    TicTacToe.parse("X  X  X  ").get.win shouldBe Some(true)
    TicTacToe.parse(" X 0X  X ").get.win shouldBe Some(true)
    TicTacToe.parse("0 X  X  X").get.win shouldBe Some(true)
    TicTacToe.parse("X0.\n.0.\nX0X").get.win shouldBe Some(false)
    TicTacToe.parse("X0.\n...\nX0.").get.win shouldBe None
    TicTacToe.parse("...\n.0.\nX0X").get.win shouldBe None
  }

  it should "win diag" in {
    TicTacToe.parse("XXX     0").get.win shouldBe Some(true)
    TicTacToe.parse("   XXX0  ").get.win shouldBe Some(true)
    TicTacToe.parse(" 0    XXX").get.win shouldBe Some(true)
  }

  it should "potentialWin" in {
    TicTacToe.from(2, 0x44000000, None).potentialWin shouldBe Some(true)
    TicTacToe.from(2, 0x40040000, None).potentialWin shouldBe Some(true)
    TicTacToe.from(2, 0x40400000, None).potentialWin shouldBe Some(true)
    TicTacToe.parse(".00     0").get.potentialWin shouldBe Some(false)
  }

  it should "fork" in {
    TicTacToe.parse("..X.0XX00", Some(0x8000)).get.fork shouldBe Some(false)
    TicTacToe.parse("..0.X0.XX", Some(0x4000)).get.fork shouldBe Some(true)
    TicTacToe.parse("..0.X0X.X", Some(0x4000)).get.fork shouldBe Some(true)
    TicTacToe.parse("..0XX0..X", Some(0x4000)).get.fork shouldBe None
  }

  it should "block" in {
    TicTacToe(TicTacToe.parse("0 0     0").get.play(xOrO = true)(0, 1)).block shouldBe Some(true)
    TicTacToe(TicTacToe.parse("X X     0").get.play(xOrO = false)(0, 1)).block shouldBe Some(false)
    TicTacToe(TicTacToe.parse("X...0..0X").get.play(xOrO = true)(0, 1)).block shouldBe Some(true)
    TicTacToe(TicTacToe.parse("X...0..0X").get.play(xOrO = true)(0, 2)).block shouldBe None
  }

  it should "heuristic 1" in {
    val z = implicitly[State[Board, TicTacToe]]
    z.heuristic(TicTacToe.parse(" 0 XXX 0 ").get) shouldBe 7 // X win
    z.heuristic(TicTacToe.parse("0   0 XXX").get) shouldBe 7 // X win
    z.heuristic(TicTacToe.parse("000XX X  ").get) shouldBe 7 // 0 win
    z.heuristic(TicTacToe.parse("X X   0  ").get) shouldBe 4 // X pending
    z.heuristic(TicTacToe.parse("   X X  0").get) shouldBe 4 // X pending
    z.heuristic(TicTacToe.parse("0     X X").get) shouldBe 4 // X pending
  }

  it should "corner" in {
    TicTacToe.parse("X........").get.corner shouldBe true
    TicTacToe.parse(".X.......").get.corner shouldBe false
    TicTacToe.parse("..X......").get.corner shouldBe true
    TicTacToe.parse("...X.....").get.corner shouldBe false
    TicTacToe.parse("....X....").get.corner shouldBe false
    TicTacToe.parse(".....X...").get.corner shouldBe false
    TicTacToe.parse("......X..").get.corner shouldBe true
    TicTacToe.parse(".......X.").get.corner shouldBe false
    TicTacToe.parse("........X").get.corner shouldBe true
  }

  it should "exchange" in {
    val x = TicTacToe.parse("XXXXXXXXX").get.board.value
    val y = TicTacToe.parse("000000000").get.board.value
    exchangeBoard(x) shouldBe y
  }

  it should "exchange2" in {
    val x = TicTacToe.parse("X.......O").get.board.value
    val y = TicTacToe.parse("0.......X").get.board.value
    exchangeBoard(x) shouldBe y
  }

  it should "oppositeCorner" in {
    TicTacToe.parse("X.......O", Some(0x00008000)).get.oppositeCorner(true) shouldBe true
    TicTacToe.parse(".X......0", Some(0x00008000)).get.oppositeCorner(true) shouldBe false
    TicTacToe.parse("..X...0..", Some(0x00080000)).get.oppositeCorner(true) shouldBe true
  }

  it should "oppHasCenter" in {
    TicTacToe.parse("X...0....", Some(0x40000000)).get.oppHasCenter shouldBe true
  }

  it should "weHaveOppositeCorner" in {
    val t: TicTacToe = TicTacToe(TicTacToe(TicTacToe.parse("X        ").get.play(xOrO = false)(1, 1)).play(xOrO = true)(2, 2))
    t.weHaveOppositeCorner shouldBe true
  }

  it should "currentMove" in {
    val t: TicTacToe = TicTacToe(TicTacToe(TicTacToe.parse("X        ").get.play(xOrO = false)(1, 1)).play(xOrO = true)(2, 2))
    t.currentMove.value shouldBe 0x4000
  }

  it should "maybeOpponentMove" in {
    val t: TicTacToe = TicTacToe(TicTacToe(TicTacToe.parse("X        ").get.play(xOrO = false)(1, 1)).play(xOrO = true)(2, 2))
    t.maybeOpponentMove shouldBe Some(Board(1, 0x800000))
  }

  it should "maybePreviousMove" in {
    val t: TicTacToe = TicTacToe(TicTacToe(TicTacToe.parse("X        ").get.play(xOrO = false)(1, 1)).play(xOrO = true)(2, 2))
    t.maybePreviousMove shouldBe Some(Board(1, 0x40000000))
  }

  it should "heuristic for best game" in {
    val z = implicitly[State[Board, TicTacToe]]
    val (moveX0, _) = startingPosition.play(xOrO = true)(2, 2)
    moveX0 shouldBe Board(1, 0x4000)
    z.heuristic(TicTacToe(moveX0)) shouldBe 1 // X corner
    val (moveO0, _) = TicTacToe(moveX0).play(xOrO = false)(0, 0)
    moveO0 shouldBe Board(2, 0x80004000)
    z.heuristic(TicTacToe(moveO0)) shouldBe 2 // 0 opposite corner
    TicTacToe(moveO0).board
    z.heuristic(TicTacToe(TicTacToe.parse("0     X0.").get.play(xOrO = true)(2, 2))) shouldBe 2 // X opposite corner
    z.heuristic(TicTacToe(TicTacToe.parse("0X0   .  ").get.play(xOrO = true)(2, 0))) shouldBe 2 // X opposite corner
    z.heuristic(TicTacToe(startingPosition.play(xOrO = true)(1, 1))) shouldBe 3 // X center
  }

  it should "heuristic where difference may matter" in {
    val z = implicitly[State[Board, TicTacToe]]
    z.heuristic(TicTacToe(startingPosition.play(xOrO = true)(2, 2))) shouldBe 1 // X corner
    z.heuristic(TicTacToe(TicTacToe.parse(".       X").get.play(xOrO = false)(0, 0))) shouldBe 2 // 0 opposite corner
    z.heuristic(TicTacToe(TicTacToe.parse("0     X0.").get.play(xOrO = true)(2, 2))) shouldBe 2 // X opposite corner
    z.heuristic(TicTacToe(TicTacToe.parse("0X0   .  ").get.play(xOrO = true)(2, 0))) shouldBe 2 // X opposite corner
    z.heuristic(TicTacToe(startingPosition.play(xOrO = true)(1, 1))) shouldBe 3 // X center
  }

  //    behavior of "PriorityQueue"
  //
  //    import TicTacToe.TicTacToeState$
  //
  //    private val bTs = implicitly[State[Board, TicTacToe]]
  //
  //    it should "get best X play from start" in {
  //        val ss = bTs.getStates(TicTacToe.parse(".........").get)
  //        val (_, t) = PriorityQueue.maxPQ(ss).del
  //      t shouldBe TicTacToe.parse("X........").get
  //      t.board.render shouldBe "1: X..-...-...-"
  //      bTs.heuristic(t) shouldBe 4
  //    }
  //
  //    it should "get best 0 play from ....X...." in {
  //        val ss = bTs.getStates(TicTacToe.parse("....X....").get)
  //        val (_, t) = PriorityQueue.maxPQ(ss).del
  //        t shouldBe TicTacToe.parse("..0.X....").get
  //        bTs.heuristic(t) shouldBe 1
  //    }
  //
  //    it should "get best X play from ..0.X...." in {
  //        val ss = bTs.getStates(TicTacToe.parse("..0.X....").get)
  //        val (_, t) = PriorityQueue.maxPQ(ss).del
  //        t shouldBe TicTacToe.parse("..0.X...X").get
  //        bTs.heuristic(t) shouldBe 4
  //    }
  //
  //    it should "get best 0 play from ..0.X...X" in {
  //        val ss = bTs.getStates(TicTacToe.parse("..0.X...X").get)
  //        val (_, t) = PriorityQueue.maxPQ(ss).del
  //        t shouldBe TicTacToe.parse("0.0.X...X").get
  //        bTs.heuristic(t) shouldBe 6
  //    }
  //
  //    it should "get best X play from 0.0.X...X" in {
  //        val ss = bTs.getStates(TicTacToe.parse("0.0.X...X").get)
  //        val (_, t) = PriorityQueue.maxPQ(ss).del
  //        t shouldBe TicTacToe.parse("0X0.X...X").get
  //        bTs.heuristic(t) shouldBe 6
  //    }
  //
  //    it should "get best 0 play from 0X0.X...X" in {
  //        val ss = bTs.getStates(TicTacToe.parse("0X0.X...X").get)
  //        val expected = TicTacToe.parse("0X0.X..0X").get
  //        val (_, t) = PriorityQueue.maxPQ(ss).del
  //        t shouldBe expected
  //        bTs.heuristic(t) shouldBe 6
  //    }
  //
  //    it should "get best X play from 0X0.X..0X" in {
  //        val ss = bTs.getStates(TicTacToe.parse("0X0.X..0X").get)
  //        val expected = TicTacToe.parse("0X0XX..0X").get
  //        val (_, t) = PriorityQueue.maxPQ(ss).del
  //        println(t.render())
  //        t shouldBe expected
  //        bTs.heuristic(t) shouldBe 4
  //    }
  //
  //    it should "get best 0 play from 0X0XX..0X" in {
  //        val ss = bTs.getStates(TicTacToe.parse("0X0XX..0X").get)
  //        val expected = TicTacToe.parse("0X0XX0.0X").get
  //        val (_, t) = PriorityQueue.maxPQ(ss).del
  //        println(t.render())
  //        t shouldBe expected
  //        bTs.heuristic(t) shouldBe 6
  //    }
  //
  //    it should "get best X play from 0X0XX0.0X" in {
  //        val ss = bTs.getStates(TicTacToe.parse("0X0XX0.0X").get)
  //        val expected = TicTacToe.parse("0X0XX0X0X").get
  //        val (q, t) = PriorityQueue.maxPQ(ss).del
  //        q.isEmpty shouldBe true
  //        t shouldBe expected
  //        bTs.heuristic(t) shouldBe 2
  //    }
  //
  //    it should "get best X play from X00.X..X0" in {
  //        val ss = bTs.getStates(TicTacToe.parse("X00.X..X0").get)
  //        val expected = TicTacToe.parse("X00.XX.X0").get
  //        val (_, t) = PriorityQueue.maxPQ(ss).del
  //        t shouldBe expected
  //        bTs.heuristic(t) shouldBe 6
  //    }
  //
  //    it should "get best X play from X00X...X0" in {
  //        val ss = bTs.getStates(TicTacToe.parse("X00X...X0").get)
  //        val expected = TicTacToe.parse("X00X..XX0").get
  //        val (_, t) = PriorityQueue.maxPQ(ss).del
  //        t shouldBe expected
  //        bTs.heuristic(t) shouldBe 7
  //    }
  //
}
