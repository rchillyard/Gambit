package com.phasmidsoftware.decisiontree.examples.tictactoe

import com.phasmidsoftware.decisiontree.moves.State
import com.phasmidsoftware.visitor.core.*

import scala.collection.mutable
import scala.util.Random

/**
  * A perfect TicTacToe player built by running a full minimax evaluation
  * over the game DAG using Visitor's post-order DFS.
  *
  * Construction (once, on first use):
  *   1. A mutable score map `Map[TicTacToe, Int]` is allocated.
  *   2. A `given Evaluable[TicTacToe, Int]` closes over the map; for terminal
  *      positions it scores directly; for internal positions it reads children's
  *      scores from the map (already populated in post-order).
  *   3. `Traversal.dfs` is called with `DfsOrder.Post` from `TicTacToe.start`.
  *      The default `given VisitedSet[TicTacToe]` ensures each position is
  *      evaluated exactly once (DAG, not tree).
  *   4. The completed score map drives `chooseMove`.
  *
  * Minimax convention:
  *   - X maximises (score +1 = X wins, 0 = draw, -1 = O wins).
  *   - O minimises.
  *   - `TicTacToe.player` is true when it was X's turn to produce this board
  *     (i.e. X just moved), so when choosing the next move we look at whose
  *     turn it *is*, which is `!ttt.player` for the node just settled.
  *
  * Scores in the map are from X's perspective throughout.
  */
class PerfectPlayer(implicit state: State[Board, TicTacToe]) extends Player {

  // Populated once, lazily, by running the full minimax DFS.
  private lazy val scores: Map[TicTacToe, Int] = buildScores()

  override def chooseMove(ttt: TicTacToe, random: Random): Option[Int] = {
    if (state.isGoal(ttt).isDefined) None // terminal position — no move available
    else {
      val successors = state.getStates(ttt)
      if (successors.isEmpty) None
      else {
        // ttt.player is true if X just moved to reach ttt, so it is now O's turn.
        // X maximises; O minimises.
        val xToMove = !ttt.player
        val best = if (xToMove)
          successors.maxBy(s => scores.getOrElse(s, 0))
        else
          successors.minBy(s => scores.getOrElse(s, 0))
        Some(TicTacToeUtils.cellFromDiff(best.board.value ^ ttt.board.value))
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Minimax build phase
  // ---------------------------------------------------------------------------

  private def buildScores(): Map[TicTacToe, Int] = {
    val scoreMap = mutable.Map.empty[TicTacToe, Int]

    // Evaluable closes over scoreMap.
    // Called at post-order visit time — all children already scored.
    given Evaluable[TicTacToe, Int] with
      def evaluate(ttt: TicTacToe): Option[Int] =
        val score = state.isGoal(ttt) match
          case Some(true) =>
            // The player who just moved won.
            // ttt.player is true if X just moved.
            if ttt.player then 1 else -1
          case Some(false) =>
            0 // draw
          case None =>
            // Internal node: aggregate children's scores.
            // It is now the turn of !ttt.player.
            val childScores = state.getStates(ttt).flatMap(s => scoreMap.get(s))
            if childScores.isEmpty then 0
            else if !ttt.player then childScores.max // X to move: maximise
            else childScores.min // O to move: minimise
        scoreMap(ttt) = score
        Some(score)

    given GraphNeighbours[TicTacToe] with
      def neighbours(ttt: TicTacToe): Iterator[TicTacToe] =
        state.getStates(ttt).iterator

    import com.phasmidsoftware.visitor.core.given
    given VisitedSet[TicTacToe] = summon[VisitedSet[TicTacToe]]

    val visitor = JournaledVisitor.withListJournal[TicTacToe, Int]
    Traversal.dfs(TicTacToe.start, visitor, DfsOrder.Post)

    scoreMap.toMap
  }

}