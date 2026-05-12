package com.phasmidsoftware.gambit.game

import com.phasmidsoftware.gambit.examples.connect4.Connect4State.given
import com.phasmidsoftware.gambit.examples.connect4.{Connect4, Connect4GameRunner, Connect4State, connect4Game, HeuristicPlayer as C4HeuristicPlayer}
import com.phasmidsoftware.gambit.examples.tictactoe.TicTacToe.TicTacToeState$
import com.phasmidsoftware.gambit.examples.tictactoe.{Board, TicTacToe, TicTacToeGameRunner, tictactoeGame, RandomPlayer as TTTRandomPlayer}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should
import org.scalatest.tagobjects.Slow

import scala.util.Random

class AlphaBetaPlayerFuncSpec extends AnyFlatSpec with should.Matchers {

  behavior of "AlphaBetaPlayer on TicTacToe"

  it should "never lose as X against a random player" taggedAs Slow in {
    val ab = AlphaBetaPlayer[Board, TicTacToe, Int, Boolean](me = true, depth = 6)
    val runner = TicTacToeGameRunner(ab, new TTTRandomPlayer, new Random(1L))
    val stats = runner.playGames(20)
    stats.winsFor(false) shouldBe 0
  }

  behavior of "AlphaBetaPlayer on Connect4"

  it should "be stronger than HeuristicPlayer with greater depth" taggedAs Slow in {
    val ab = AlphaBetaPlayer[Connect4, Connect4, Int, Boolean](me = true, depth = 6)
    val heuristic = new C4HeuristicPlayer
    val runner = Connect4GameRunner(ab, heuristic, new Random(8L))
    val stats = runner.playGames(10)
    stats.winsFor(true) should be > stats.lossesFor(true)
  }
}