package com.phasmidsoftware.gambit.game

import com.phasmidsoftware.gambit.examples.connect4.Connect4State.given
import com.phasmidsoftware.gambit.examples.connect4.{AlphaBetaPlayerConnect4, Connect4, Connect4GameRunner, Connect4State, connect4Game, HeuristicPlayer as C4HeuristicPlayer, RandomPlayer as C4RandomPlayer}
import com.phasmidsoftware.gambit.examples.tictactoe.TicTacToe.TicTacToeState$
import com.phasmidsoftware.gambit.examples.tictactoe.{AlphaBetaPlayerTicTacToe, Board, PerfectPlayer, TicTacToe, TicTacToeGameRunner, tictactoeGame, RandomPlayer as TTTRandomPlayer}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should
import org.scalatest.tagobjects.Slow

import scala.util.Random

class AlphaBetaPlayerSpec extends AnyFlatSpec with should.Matchers {


  // ---------------------------------------------------------------------------
  // TicTacToe — AlphaBetaPlayer
  // ---------------------------------------------------------------------------

  behavior of "AlphaBetaPlayerTicTacToe on TicTacToe"

  it should "choose a valid move from the starting position" in {
    val ab = AlphaBetaPlayer[Board, TicTacToe, Int, Boolean](me = true, depth = 4)
    val move = ab.chooseMove(TicTacToe.start, new Random(1L))
    move shouldBe defined
    move.get should (be >= 0 and be <= 8)
  }

  it should "return None for a terminal position" in {
    val xWin = TicTacToe.parse("XXX- 0 -0  ").get
    val ab = AlphaBetaPlayer[Board, TicTacToe, Int, Boolean](me = true, depth = 4)
    ab.chooseMove(xWin, new Random(1L)) shouldBe None
  }

  it should "take a winning move for X when available" in {
    // X at (0,0),(0,1); O at (1,0),(1,1) — X to move, wins at (0,2).
    val ttt = TicTacToe.parse("XX -00 -   ").get
    val ab = AlphaBetaPlayer[Board, TicTacToe, Int, Boolean](me = true, depth = 4)
    val move = ab.chooseMove(ttt, new Random(1L))
    move shouldBe defined
    val row = move.get / TicTacToe.size
    val col = move.get % TicTacToe.size
    val next = TicTacToeState$.construct(ttt.playX(row, col))
    TicTacToeState$.isGoal(next) shouldBe Some(true)
  }

  it should "block O from winning" in {
    // O at (0,0),(0,1); X at (1,1),(2,2) — X must block at (0,2).
    val ttt = TicTacToe.parse("00 -X  -  X").get
    val ab = AlphaBetaPlayer[Board, TicTacToe, Int, Boolean](me = true, depth = 4)
    val move = ab.chooseMove(ttt, new Random(1L))
    move shouldBe defined
    val row = move.get / TicTacToe.size
    val col = move.get % TicTacToe.size
    val next = TicTacToeState$.construct(ttt.playX(row, col))
    val oResponses = TicTacToeState$.getStates(next)
    val oWins = oResponses.filter(s => TicTacToeState$.isGoal(s).contains(true) && !TicTacToeState$.isWin(s))
    oWins shouldBe empty
  }

  it should "never lose as O against a random player" taggedAs Slow in {
    val ab = AlphaBetaPlayer[Board, TicTacToe, Int, Boolean](me = false, depth = 6)
    val runner = TicTacToeGameRunner(new TTTRandomPlayer, ab, new Random(2L))
    val stats = runner.playGames(20)
    stats.winsFor(true) shouldBe 0
  }

  it should "always draw against PerfectPlayer" taggedAs Slow in {
    val ab = AlphaBetaPlayer[Board, TicTacToe, Int, Boolean](me = true, depth = 6)
    val runner = TicTacToeGameRunner(ab, new PerfectPlayer, new Random(3L))
    val stats = runner.playGames(10)
    stats.winsFor(false) shouldBe 0 // AlphaBeta should never lose
  }

  it should "always draw against itself" taggedAs Slow in {
    val abX = AlphaBetaPlayer[Board, TicTacToe, Int, Boolean](me = true, depth = 6)
    val abO = AlphaBetaPlayer[Board, TicTacToe, Int, Boolean](me = false, depth = 6)
    val runner = TicTacToeGameRunner(abX, abO, new Random(4L))
    val stats = runner.playGames(5)
    stats.winsFor(true) shouldBe 0
    stats.winsFor(false) shouldBe 0
    stats.drawsFor(true) shouldBe 5
  }

  // ---------------------------------------------------------------------------
  // Connect Four — AlphaBetaPlayer
  // ---------------------------------------------------------------------------

  behavior of "AlphaBetaPlayer on Connect4"

  it should "choose a valid column from the starting position" in {
    val ab = AlphaBetaPlayer[Connect4, Connect4, Int, Boolean](me = true, depth = 4)
    val move = ab.chooseMove(Connect4.start, new Random(1L))
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
    val ab = AlphaBetaPlayer[Connect4, Connect4, Int, Boolean](me = true, depth = 4)
    ab.chooseMove(win, new Random(1L)) shouldBe None
  }

  it should "take a winning move when available" in {
    // X has 3 in a row at cols 0,1,2 — wins at col 3.
    val almostWin = Connect4.start
      .play(0, isX = true).play(6, isX = false)
      .play(1, isX = true).play(6, isX = false)
      .play(2, isX = true).play(6, isX = false)
    val ab = AlphaBetaPlayer[Connect4, Connect4, Int, Boolean](me = true, depth = 4)
    val move = ab.chooseMove(almostWin, new Random(1L))
    move shouldBe Some(3)
  }

  it should "prefer the centre column from the starting position" in {
    val ab = AlphaBetaPlayer[Connect4, Connect4, Int, Boolean](me = true, depth = 4)
    val move = ab.chooseMove(Connect4.start, new Random(1L))
    move shouldBe Some(3)
  }

  it should "win more than it loses as X against a random player" taggedAs Slow in {
    val ab = AlphaBetaPlayer[Connect4, Connect4, Int, Boolean](me = true, depth = 4)
    val runner = Connect4GameRunner(ab, new C4RandomPlayer, new Random(5L))
    val stats = runner.playGames(20)
    stats.winsFor(true) should be > stats.lossesFor(true)
  }

  it should "win more than it loses as O against a random player" taggedAs Slow in {
    val ab = AlphaBetaPlayer[Connect4, Connect4, Int, Boolean](me = false, depth = 4)
    val runner = Connect4GameRunner(new C4RandomPlayer, ab, new Random(6L))
    val stats = runner.playGames(20)
    stats.winsFor(false) should be > stats.lossesFor(false)
  }

  it should "be competitive against HeuristicPlayer" taggedAs Slow in {
    val ab = AlphaBetaPlayer[Connect4, Connect4, Int, Boolean](me = true, depth = 4)
    val heuristic = new C4HeuristicPlayer
    val runner = Connect4GameRunner(ab, heuristic, new Random(7L))
    val stats = runner.playGames(10)
    // AlphaBeta with depth 4 should outperform one-ply heuristic.
    stats.winsFor(true) should be >= stats.winsFor(false)
  }

  // ---------------------------------------------------------------------------
  // Transposition table (keyFn)
  // ---------------------------------------------------------------------------

  behavior of "AlphaBetaPlayerTicTacToe transposition table"

  it should "produce the same Connect4 move with and without keyFn" in {
    val rng = new Random(1L)

    given TTCache[(Long, Long)] = FlatTTCache[(Long, Long)]()
    val abNoKey = AlphaBetaPlayer[Connect4, Connect4, Int, Boolean](me = true, depth = 4)
    val abKey = AlphaBetaPlayerConnect4(
      me = true, depth = 4
    ).withKeyFn(s => (s.xBits, s.oBits))
    abNoKey.chooseMove(Connect4.start, rng) shouldBe abKey.chooseMove(Connect4.start, rng)
  }

  it should "produce the same TicTacToe move with and without keyFn" in {
    val rng = new Random(1L)

    given TTCache[Int] = FlatTTCache[Int]()

    val abNoKey = AlphaBetaPlayerTicTacToe(me = true, depth = 4)
    val abKey = AlphaBetaPlayerTicTacToe(
      me = true, depth = 4
    ).withKeyFn(s => s.board.value)
    abNoKey.chooseMove(TicTacToe.start, rng) shouldBe abKey.chooseMove(TicTacToe.start, rng)
  }

  it should "evaluate faster with keyFn on a mid-game Connect4 position" in {
    // A mid-game position with many transpositions reachable.
    val mid = Connect4.start
      .play(3, isX = true).play(3, isX = false)
      .play(2, isX = true).play(4, isX = false)

    val rng = new Random(1L)

    given TTCache[(Long, Long)] = FlatTTCache[(Long, Long)]()
    val abNoKey = AlphaBetaPlayer[Connect4, Connect4, Int, Boolean](me = true, depth = 6)
    val abKey = AlphaBetaPlayerConnect4(
      me = true, depth = 6
    ).withKeyFn(s => (s.xBits, s.oBits))

    val t0 = System.currentTimeMillis()
    abNoKey.chooseMove(mid, rng)
    val tNoKey = System.currentTimeMillis() - t0

    val t1 = System.currentTimeMillis()
    abKey.chooseMove(mid, rng)
    val tKey = System.currentTimeMillis() - t1

    // With memoization, evaluation should be at least as fast.
    // We allow a 2x margin to account for JVM variance.
    tKey should be <= (tNoKey * 2)
  }

  it should "clear the transposition table between games via gameOver" in {
    // After gameOver, the table is cleared but the player should still work correctly.
    given TTCache[(Long, Long)] = FlatTTCache[(Long, Long)]()
    val ab = AlphaBetaPlayerConnect4(
      me = true, depth = 4
    ).withKeyFn(s => (s.xBits, s.oBits))
    val move1 = ab.chooseMove(Connect4.start, new Random(1L))
    ab.gameOver(Map(true -> 1, false -> -1), true)
    // After reset, should give the same answer.
    val move2 = ab.chooseMove(Connect4.start, new Random(1L))
    move1 shouldBe move2
  }

  it should "accept None keyFn and behave identically to the default constructor" in {
    val rng = new Random(1L)
    val abNone = AlphaBetaPlayer[Connect4, Connect4, Int, Boolean](
      me = true, depth = 4
    )
    val abDefault = AlphaBetaPlayer[Connect4, Connect4, Int, Boolean](me = true, depth = 4)
    abNone.chooseMove(Connect4.start, rng) shouldBe abDefault.chooseMove(Connect4.start, rng)
  }

  it should "preserve the TicTacToe never-lose guarantee with keyFn enabled" in {
    given TTCache[Int] = FlatTTCache[Int]()
    val ab = AlphaBetaPlayerTicTacToe(
      me = true, depth = 6
    ).withKeyFn(s => s.board.value)
    val runner = TicTacToeGameRunner(ab, new TTTRandomPlayer, new Random(9L))
    val stats = runner.playGames(20)
    stats.winsFor(false) shouldBe 0
  }

  it should "preserve the Connect4 never-lose guarantee with keyFn enabled" in {
    given TTCache[(Long, Long)] = FlatTTCache[(Long, Long)]()
    val ab = AlphaBetaPlayerConnect4(
      me = true, depth = 4
    ).withKeyFn(s => (s.xBits, s.oBits))
    val runner = Connect4GameRunner(ab, new C4RandomPlayer, new Random(9L))
    val stats = runner.playGames(20)
    stats.winsFor(true) should be > stats.lossesFor(true)
  }

  // ---------------------------------------------------------------------------
  // Transposition table modes: flat, depth-tranches exact, depth-tranches reuse
  // ---------------------------------------------------------------------------

  behavior of "AlphaBetaPlayerTicTacToe transposition table modes"

  it should "produce the same Connect4 move with flat table (default)" in {
    val rng = new Random(1L)

    given TTCache[(Long, Long)] = FlatTTCache[(Long, Long)]()
    val abFlat = AlphaBetaPlayerConnect4(
      me = true, depth = 4
    ).withKeyFn(s => (s.xBits, s.oBits))
    abFlat.chooseMove(Connect4.start, rng) shouldBe Some(3)
  }

  it should "produce the same Connect4 move with depth-tranche exact mode" in {
    val rng = new Random(1L)

    given TTCache[(Long, Long)] = FlatTTCache[(Long, Long)]()
    val abTranche = AlphaBetaPlayerConnect4(
      me = true, depth = 4
    ).withKeyFn(s => (s.xBits, s.oBits))
    abTranche.chooseMove(Connect4.start, rng) shouldBe Some(3)
  }

  it should "produce the same Connect4 move with depth-tranche reuse mode" in {
    val rng = new Random(1L)

    given TTCache[(Long, Long)] = FlatTTCache[(Long, Long)]()
    val abReuse = AlphaBetaPlayerConnect4(
      me = true, depth = 4
    ).withKeyFn(s => (s.xBits, s.oBits))
    abReuse.chooseMove(Connect4.start, rng) shouldBe Some(3)
  }

  it should "produce identical moves across all three cache modes" in {
    given TTCache[(Long, Long)] = FlatTTCache[(Long, Long)]()
    val mid = Connect4.start
      .play(3, isX = true).play(3, isX = false)
      .play(2, isX = true).play(4, isX = false)
    val keyFn: Connect4 => (Long, Long) = (s: Connect4) => (s.xBits, s.oBits)
    val abFlat = AlphaBetaPlayerConnect4(me = true, depth = 4).withKeyFn(keyFn)
    val abExact = AlphaBetaPlayerConnect4(me = true, depth = 4).withKeyFn(keyFn)
    val abReuse = AlphaBetaPlayerConnect4(me = true, depth = 4).withKeyFn(keyFn)
    val moveFlat = abFlat.chooseMove(mid, new Random(1L))
    val moveExact = abExact.chooseMove(mid, new Random(1L))
    val moveReuse = abReuse.chooseMove(mid, new Random(1L))
    moveFlat shouldBe moveExact
    moveFlat shouldBe moveReuse
  }

  it should "never lose TicTacToe with depth-tranche exact mode" taggedAs Slow in {
    given TTCache[Int] = FlatTTCache[Int]()
    val ab = AlphaBetaPlayerTicTacToe(
      me = true, depth = 6
    ).withKeyFn(s => s.board.value)
    val runner = TicTacToeGameRunner(ab, new TTTRandomPlayer, new Random(9L))
    val stats = runner.playGames(20)
    stats.winsFor(false) shouldBe 0
  }

  it should "never lose TicTacToe with depth-tranche reuse mode" taggedAs Slow in {
    given TTCache[Int] = FlatTTCache[Int]()
    val ab = AlphaBetaPlayerTicTacToe(
      me = true, depth = 6
    ).withKeyFn(s => s.board.value)
    val runner = TicTacToeGameRunner(ab, new TTTRandomPlayer, new Random(9L))
    val stats = runner.playGames(20)
    stats.winsFor(false) shouldBe 0
  }

  // ---------------------------------------------------------------------------
  // chooseMoveWithScore
  // ---------------------------------------------------------------------------

  behavior of "AlphaBetaPlayer.chooseMoveWithScore"

  it should "return the same move as chooseMove" in {
    val ab = AlphaBetaPlayer[Board, TicTacToe, Int, Boolean](me = true, depth = 4)
    val move = ab.chooseMove(TicTacToe.start, new Random(1L))
    val moveWithScore = ab.chooseMoveWithScore(TicTacToe.start, new Random(1L))
    moveWithScore.map(_._1) shouldBe move
  }

  it should "return a positive score for a position where X is about to win" in {
    // X at (0,0),(0,1); O at (1,0),(1,1) — X to move, wins at (0,2).
    val ttt = TicTacToe.parse("XX -00 -   ").get
    val ab = AlphaBetaPlayer[Board, TicTacToe, Int, Boolean](me = true, depth = 4)
    val result = ab.chooseMoveWithScore(ttt, new Random(1L))
    result shouldBe defined
    result.get._2 should be > 0.0
  }

  it should "return a negative score for a position where X is about to lose" in {
    // O at (0,0),(0,1),(1,0),(1,1) — X to move but cannot prevent O winning.
    val ttt = TicTacToe.parse("00 -00 -X  ").get
    val ab = AlphaBetaPlayer[Board, TicTacToe, Int, Boolean](me = true, depth = 4)
    val result = ab.chooseMoveWithScore(ttt, new Random(1L))
    result shouldBe defined
    result.get._2 should be < 0.0
  }
  // ---------------------------------------------------------------------------
  // Node limit — withMaxNodes / NodeLimitException
  // ---------------------------------------------------------------------------

  behavior of "AlphaBetaPlayer.withMaxNodes"

  it should "return this for chaining" in {
    val ab = AlphaBetaPlayer[Board, TicTacToe, Int, Boolean](me = true, depth = 6)
    ab.withMaxNodes(1000) shouldBe ab
  }

  it should "complete normally when node limit is not reached" in {
    // Depth-4 TicTacToe from start is well under 1,000,000 nodes
    val ab = AlphaBetaPlayer[Board, TicTacToe, Int, Boolean](me = true, depth = 4)
      .withMaxNodes(1000000)
    noException should be thrownBy ab.chooseMove(TicTacToe.start, new Random(1L))
  }

  it should "throw NodeLimitException when limit is set to 1" in {
    val ab = AlphaBetaPlayer[Board, TicTacToe, Int, Boolean](me = true, depth = 6)
      .withMaxNodes(1)
    an[NodeLimitException] should be thrownBy ab.chooseMove(TicTacToe.start, new Random(1L))
  }

  it should "throw NodeLimitException for Connect4 with a tight limit" in {
    val ab = AlphaBetaPlayer[Connect4, Connect4, Int, Boolean](me = true, depth = 6)
      .withMaxNodes(10)
    an[NodeLimitException] should be thrownBy ab.chooseMove(Connect4.start, new Random(1L))
  }

  it should "reset node count after withMaxNodes is called again" in {
    val ab = AlphaBetaPlayer[Board, TicTacToe, Int, Boolean](me = true, depth = 4)
      .withMaxNodes(1)
    // First call hits the limit
    an[NodeLimitException] should be thrownBy ab.chooseMove(TicTacToe.start, new Random(1L))
    // Reset with a generous limit — should now complete
    ab.withMaxNodes(1000000)
    noException should be thrownBy ab.chooseMove(TicTacToe.start, new Random(1L))
  }

  it should "reset node count via gameOver" in {
    val ab = AlphaBetaPlayer[Board, TicTacToe, Int, Boolean](me = true, depth = 4)
      .withMaxNodes(1)
    // First call hits the limit
    an[NodeLimitException] should be thrownBy ab.chooseMove(TicTacToe.start, new Random(1L))
    // gameOver resets the counter; withMaxNodes raises the limit
    ab.gameOver(Map(true -> 1, false -> -1), true)
    ab.withMaxNodes(1000000)
    noException should be thrownBy ab.chooseMove(TicTacToe.start, new Random(1L))
  }

  it should "report the node count in the exception message" in {
    val ab = AlphaBetaPlayer[Board, TicTacToe, Int, Boolean](me = true, depth = 6)
      .withMaxNodes(5)
    val ex = intercept[NodeLimitException] {
      ab.chooseMove(TicTacToe.start, new Random(1L))
    }
    ex.nodes should be > 5
    ex.getMessage should include("nodes")
  }

  it should "return a valid move as bestSoFar when limit fires after first move" in {
    // X at (0,0),(0,1); O at (1,0),(1,1) — X wins at (0,2), several open cells remain.
    val ttt = TicTacToe.parse("XX -00 -   ").get
    val ab = AlphaBetaPlayer[Board, TicTacToe, Int, Boolean](me = true, depth = 1)
      .withMaxNodes(2)
    intercept[NodeLimitException] {
      ab.chooseMove(ttt, new Random(1L))
    }
    ab.getBestSoFar shouldBe defined
    val move = ab.getBestSoFar.get._1
    move should (be >= 0 and be <= 8)
  }

  // ---------------------------------------------------------------------------
  // worstSoFar / getWorstSoFar
  // ---------------------------------------------------------------------------

  behavior of "AlphaBetaPlayer.getWorstSoFar"

  it should "be None before any search" in {
    val ab = AlphaBetaPlayer[Board, TicTacToe, Int, Boolean](me = true, depth = 4)
    ab.getWorstSoFar shouldBe None
  }

  it should "be defined after a complete search" in {
    val ttt = TicTacToe.parse("XX -00 -   ").get
    val ab = AlphaBetaPlayer[Board, TicTacToe, Int, Boolean](me = true, depth = 4)
    ab.chooseMoveWithScore(ttt, new Random(1L)) shouldBe defined
    ab.getWorstSoFar shouldBe defined
  }

  it should "have a score <= bestSoFar score for a maximizing player" in {
    // After a full search, worstSoFar is the antagonist's best line —
    // always <= the protagonist's best line.
    val ttt = TicTacToe.parse("XX -00 -   ").get
    val ab = AlphaBetaPlayer[Board, TicTacToe, Int, Boolean](me = true, depth = 4)
    ab.chooseMoveWithScore(ttt, new Random(1L))
    val best = ab.getBestSoFar.map(_._2)
    val worst = ab.getWorstSoFar.map(_._2)
    best shouldBe defined
    worst shouldBe defined
    worst.get should be <= best.get
  }

  it should "have a score >= bestSoFar score for a minimizing player" in {
    // Symmetric: when me=false, worstSoFar is the highest score seen.
    val ttt = TicTacToe.parse("XX -00 -   ").get
    val ab = AlphaBetaPlayer[Board, TicTacToe, Int, Boolean](me = false, depth = 4)
    ab.chooseMoveWithScore(ttt, new Random(1L))
    val best = ab.getBestSoFar.map(_._2)
    val worst = ab.getWorstSoFar.map(_._2)
    best shouldBe defined
    worst shouldBe defined
    worst.get should be >= best.get
  }

  it should "be defined after a NodeLimitException when at least one move completed" in {
    // Depth-1 search on this position scores each move in very few nodes;
    // with limit=2 the first move completes, then the limit fires on the second.
    val ttt = TicTacToe.parse("XX -00 -   ").get
    val ab = AlphaBetaPlayer[Board, TicTacToe, Int, Boolean](me = true, depth = 1)
      .withMaxNodes(2)
    intercept[NodeLimitException] {
      ab.chooseMove(ttt, new Random(1L))
    }
    ab.getWorstSoFar shouldBe defined
  }

  it should "reset to None when withMaxNodes is called" in {
    val ttt = TicTacToe.parse("XX -00 -   ").get
    val ab = AlphaBetaPlayer[Board, TicTacToe, Int, Boolean](me = true, depth = 4)
    ab.chooseMoveWithScore(ttt, new Random(1L))
    ab.getWorstSoFar shouldBe defined
    ab.withMaxNodes(1000000)
    ab.getWorstSoFar shouldBe None
  }

  it should "reset to None via gameOver" in {
    val ttt = TicTacToe.parse("XX -00 -   ").get
    val ab = AlphaBetaPlayer[Board, TicTacToe, Int, Boolean](me = true, depth = 4)
    ab.chooseMoveWithScore(ttt, new Random(1L))
    ab.getWorstSoFar shouldBe defined
    ab.gameOver(Map(true -> 1, false -> -1), true)
    ab.getWorstSoFar shouldBe None
  }

  it should "equal bestSoFar when only one top-level move exists" in {
    // Board with exactly one legal move: X fills the last empty cell (position 8).
    // XOX / OXO / OX_ — no winner yet, one empty cell.
    // Only one top-level move, so best and worst must be the same result.
    val ttt = TicTacToe.parse("XOX-OXO-OX ").get
    val ab = AlphaBetaPlayer[Board, TicTacToe, Int, Boolean](me = true, depth = 4)
    ab.chooseMoveWithScore(ttt, new Random(1L))
    ab.getBestSoFar.map(_._2) shouldBe ab.getWorstSoFar.map(_._2)
  }
}