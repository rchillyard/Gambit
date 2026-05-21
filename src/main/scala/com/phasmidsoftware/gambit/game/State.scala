package com.phasmidsoftware.gambit.game

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
trait State[P, S] extends Ordering[S]:

  /**
    * A significant sequence value that distinguishes this state from others.
    * Typically the number of moves made so far (0 for the starting state).
    * Used to determine whose turn it is via `isFirstPlayerToMove`.
    *
    * @param s the state.
    */
  def sequence(s: S): Int

  /**
    * Abstract method to construct an S from a P and an S.
    *
    * @param proto a (P, S) tuple.
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
    * In other games, for example, contract bridge, the goal is to achieve some measurable state,
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
    * Computes the terminal value of a given state `s` based on the heuristic evaluation.
    * The value is negated if the `maximizing` parameter is true, representing an adversarial game scenario.
    *
    * @param s          the current state of type `S`.
    * @param maximizing a Boolean indicating if the current player is the maximizing player.
    * @return a Double representing the evaluated value of the state, negated for the maximizing player.
    */
  def leafValue(s: S, maximizing: Boolean): Double =
    if maximizing then -heuristic(s) else heuristic(s)

  /**
    * Determines if the current player is the maximizing player.
    *
    * @param s                 the current state of type `S`.
    * @param currentMaximizing a Boolean indicating if the current player is
    *                          currently considered the maximizing player.
    *
    * @return a Boolean value negating the input `currentMaximizing`,
    *         indicating the next player's maximizing status.
    */
  def isMaximizing(s: S, currentMaximizing: Boolean): Boolean = !currentMaximizing

  /**
    * Concrete method to get the possible states to follow the given state s.
    * The resulting sequence is in no particular order.
    *
    * @param s an S.
    * @return a sequence of S instances which are the possible states to follow s.
    */
  def getStates(s: S): Seq[S] =
    for (z <- moves(s); w = z(s); q = construct(w) if isValid(q)) yield q

  /**
    * Returns true if it is the first player's turn to move from state `s`.
    *
    * To be completely unambiguous: the first player is the one who makes the
    * very first move of the game. `isFirstPlayerToMove(s)` returns true when
    * it is that player's turn again — e.g., for a two-player game,
    * when an even number of moves have been made (sequence 0, 2, 4, ...).
    *
    * It returns false when it is the second (or any subsequent) player's turn
    * — i.e., when an odd number of moves have been made (sequence 1, 3, 5, ...).
    *
    * Example (TicTacToe):
    * empty board (sequence=0) → true  (X moves first)
    * after X plays (sequence=1) → false (O moves next)
    * after O plays (sequence=2) → true  (X moves next)
    *
    * For games with more than two players, override this method as needed.
    *
    * @param s the current state.
    * @return true if the first player is to move, false otherwise.
    */
  def isFirstPlayerToMove(s: S): Boolean = sequence(s) % 2 == 0

  /**
    * Method to determine the ordering of two States.
    * It is based on the heuristic.
    *
    * @param s1 first S.
    * @param s2 second S.
    * @return <0 if s1 < s2, >0 if s1 > s2, else 0.
    */
  def compare(s1: S, s2: S): Int = sequence(s1).compare(sequence(s2)) match
    case 0 => heuristic(s1).compare(heuristic(s2))
    case cf => cf

  /**
    * Method to render a State as a String.
    *
    * @param s the State to render.
    * @return a String representation of s.
    */
  def render(s: S): String

/**
  * A function that transitions from a state S to a prototype state.
  *
  * @tparam P a proto-state, from which a state S can be constructed.
  * @tparam S the type of the input.
  */
trait Transition[P, S] extends (S => (P, S))

/**
  * A case class that implements Transition[S].
  *
  * @param f    a function S => P.
  * @param desc the human-legible description of f.
  * @tparam P a proto-state, from which a state S can be constructed.
  * @tparam S the type of the input parameter and of the result.
  */
case class Move[P, S](f: S => P, desc: String) extends Transition[P, S]:
  override def apply(s: S): (P, S) = f(s) -> s

  override def toString: String = desc
