package com.phasmidsoftware.decisiontree.game

/**
 * Type class for a State.
 * A State is a position in a game or other situation which requires heuristically-directed tree search.
 * For example, a State might describe a board position in Tic-tac-toe or Chess.
 *
 * NOTE that State depends on Transition.
 * CONSIDER eliminating Transition such that all logic is defined by State.
 *
 * @tparam P a proto-state, that's to say a type such that a P, S tuple can be converted into a new S.
 * @tparam S the underlying type on which the state is based.
 */
trait State[P, S] extends Ordering[S] {

  /**
   * a significant sequence value that distinguishes this state from others and which can be derived from a P.
   *
   * @param p parameter from which we may derive the sequence.
   */
  def sequence(s: S): Int

  /**
   * Abstract method to construct an S from a P and an S.
   *
   * @param proto a (P, Q).
   * @return an S.
   */
  def construct(proto: (P, S)): S

  /**
   * Abstract method to determine if an S is valid.
   *
   * @param s an S.
   * @return a Boolean.
   */
  def isValid(s: S): Boolean

  /**
   * Method to determine if s is a winning state.
   * NOTE: it makes no sense to invoke isWin unless the result of isGoal is Some(true).
   *
   * @param s an S
   * @return true if s is a win, else false.
   */
  def isWin(s: S): Boolean

  /**
   * Abstract method to determine if state s is a goal state.
   * In some games, the goal is to win.
   * In other games, for example contract bridge, the goal is to achieve some measurable state,
   * such as a certain number of tricks.
   *
   * @param s an S.
   * @return an Option of Boolean: if None then this state is not a goal state.
   *         If Some(true), then s achieves a goal.
   *         If Some(false), then such a goal is impossible to achieve.
   */
  def isGoal(s: S): Option[Boolean]

  /**
   * Abstract method to determine an estimate of an S's efficacy in reaching a goal.
   *
   * @param s an S.
   * @return a Double
   *         (in a domain appropriate to the type S where a higher value is always believed to be closer to a goal).
   */
  def heuristic(s: S): Double

  /**
   * Abstract method to determine the possible moves from the given S.
   *
   * @param s an S.
   * @return a sequence of Transition[S]
   */
  def moves(s: S): Seq[Transition[P, S]]

  /**
   * Concrete method to get the possible states to follow the given state s.
   * The resulting sequence is in no particular order.
   *
   * @param s an S.
   * @return a sequence of S instances which are the possible states to follow s.
   */
  def getStates(s: S): Seq[S] = for (z <- moves(s); w = z(s); q = construct(w) if isValid(q)) yield q

  /**
   * Method to determine the ordering of two States.
   * It is based on the heuristic.
   *
   * @param s1 first S.
   * @param s2 second S.
   * @return <0 if s1 < s2, >0 if s1 > s2, else 0.
   */
  def compare(s1: S, s2: S): Int = sequence(s1).compare(sequence(s2)) match {
    case 0 => heuristic(s1).compare(heuristic(s2))
    case cf => cf
  }

  /**
   * Method to render a State as a String.
   *
   * @param s the State to render.
   * @return a String representation of s.
   */
  def render(s: S): String
}

/**
 * A function which transitions from a state S to a prototype state.
 *
 * @tparam P a proto-state, from which a state S can be constructed.
 * @tparam S the type of the input..
 */
trait Transition[P, S] extends (S => (P, S))

/**
 * A case class which implements Transition[S].
 *
 * @param f    a function S => S.
 * @param desc the human-legible description of f.
 * @tparam P a proto-state, from which a state S can be constructed.
 * @tparam S the type of the input parameter and of the result.
 */
case class Move[P, S](f: S => P, desc: String) extends Transition[P, S] {

  /**
   * Apply this Move to s.
   *
   * @param s a state.
   * @return a new proto-state.
   */
  override def apply(s: S): (P, S) = f(s) -> s

  override def toString: String = desc
}
