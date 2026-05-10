package com.phasmidsoftware.decisiontree.moves

import com.phasmidsoftware.decisiontree.examples.tictactoe.{Board, TicTacToe}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

/**
 * NOTE: this Spec file depends on TicTacToe.
 */
class EvaluatorSpec extends AnyFlatSpec with should.Matchers {

  behavior of "Evaluator on TicTacToe"

  private val bTs: State[Board, TicTacToe] = implicitly[State[Board, TicTacToe]]


  it should "compare" in {
    val t: TicTacToe = bTs.construct(Board(1, 0x800000) -> TicTacToe())
    bTs.compare(t, t) shouldBe 0
  }

}
