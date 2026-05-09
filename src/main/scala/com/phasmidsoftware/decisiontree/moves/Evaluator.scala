package com.phasmidsoftware.decisiontree.moves

import com.phasmidsoftware.decisiontree.moves.Evaluator.{flog, wEvaluate}
import com.phasmidsoftware.flog.Flog
import com.phasmidsoftware.util.{DecisionTreeException, PriorityQueue}

import scala.annotation.tailrec

/**
 * Trait to evaluate a state S according to some criterion.
 *
 * @tparam S the underlying type of the state to be evaluated.
 */
trait Evaluator[S] {
  /**
   * Evaluate a game, starting with state based on s.
   * If the starting state results in the achievement of a goal state, that state will be returned.
   * If the result is None, then no goal state could be achieved.
   *
   * @param s the starting state.
   * @return an Option[S]: if Some(s) then s is a goal state.
   *         if None then no goal was achieved.
   */
  def evaluate(s: S): Option[S]

  /**
   * Method to determine a sequence of S which can follow s, the given S.
   *
   * @param s the given S.
   * @return a sequence of S.
   */
  def states(s: S): Seq[S]
}


/**
 * Abstract implementation of Evaluator[S] which depends on an implicit value of State[P, S].
 *
 * @tparam P the type of the proto-state, i.e. a parameter needed to construct a new S.
 * @tparam S the underlying type of the state to be evaluated.
 */
abstract class Evaluator_State[P, S](implicit pSs: State[P, S]) extends Evaluator[S] {
  /**
   * Method to determine a sequence of S which can follow s, the given S.
   *
   * @param s the given S.
   * @return a sequence of S.
   */
  def states(s: S): Seq[S] = pSs.getStates(s)

  /**
   * Method to determine if a given S represents a "goal" state.
   *
   * @param s the given S.
   * @return None if s is not a goal;
   *         Some(false) if it is impossible to reach a goal state from s (typically a "draw");
   *         Some(true) if s is a final goal.
   */
  def isGoal(s: S): Option[Boolean] = pSs.isGoal(s)
}

///**
// * Implementation of Evaluator_State[P, S] which employs two priority queues (one for each player).
// *
// * @param training true if training, rather than playing. In training mode, we can backtrack and examine other paths.
// *                 In playing mode, once we get to a state that has no possible moves, we declare a draw.
// * @tparam P the type of the proto-state, i.e. a parameter needed to construct a new S.
// * @tparam S the underlying type of the state to be evaluated.
// */
//class Evaluator_PQ[P, S](training: Boolean = false)(implicit pSs: State[P, S]) extends Evaluator_State[P, S] {
//
//  import flog._
//
//  /**
//   * Evaluate a two-person game, starting with state based on s.
//   *
//   * @param s the starting state.
//   * @return an Option[S]: if Some(s) then s is a goal state.
//   *         if None then no goal was achieved.
//   */
//  def evaluate(s: S): Option[S] = {
//    @tailrec
//    def inner(qp: PriorityQueue[S]): Option[S] = qp.delOption match {
//      case None => throw DecisionTreeException("Empty queue")
//      case Some((q, s)) =>
//        isGoal(wEvaluate !? s) match {
//          case Some(true) if pSs.isWin(s) =>
//            "Goal" !! Some(s)
//          case Some(false) if !training =>
//            flog.logger.info("Draw")
//            None // it's impossible to reach a goal, i.e. a draw.
//          case _ =>
//            inner(q.insertElements(states(s))) // only valid states are inserted into the queue.
//        }
//      case _ => None // s is not valid: must have no vacant spaces.
//    }
//
//    inner(PriorityQueue.maxPQ(s))
//  }
//}

object Evaluator {

  val flog: Flog = Flog[Evaluator.type]

  val wEvaluate: String = "Next element to evaluate from priority queue: "
}
