package com.phasmidsoftware.decisiontree.game

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

class StateSpec extends AnyFlatSpec with should.Matchers {

  behavior of "Move"

  //  it should "toString 0" in {
  //    val move: Move[Chess] = Move.identity[Chess]
  //    move.toString shouldBe ""
  //  }

  //  it should "toString PxP" in {
  //    val move1: Transition[Chess] = Move.identity[Chess].andThen(Move[Chess](s => Chess(s.board), "PxP"))
  //    move1.toString shouldBe "PxP()"
  //    val move2: Transition[Chess] = move1.andThen(Move[Chess](s => Chess(s.board), "PxP"))
  //    move2.toString shouldBe "PxP(PxP())"
  //  }

  behavior of "LazyState"

  //  it should "apply" in {
  //    val start = Chess("")
  //    val move: LazyState[Chess] = LazyState(start, Move.identity)
  //    move.apply() shouldBe start
  //  }

  behavior of "ChessState"
  //  it should "work" in {
  //    val z = implicitly[State[Chess]]
  //    val chess = Chess("")
  //    z.isValid(chess) shouldBe true
  //    z.moves(chess) shouldBe Nil
  //    z.isGoal(chess) shouldBe None
  //    z.heuristic(chess) shouldBe 0
  //  }
}

