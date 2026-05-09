package com.phasmidsoftware.decisiontree.examples

package object tictactoe {

    /**
     * If there is a "cell" (a matching square in the 3x3 grid), then:
     * Some(true) for player X, and Some(false) for player 0.
     * else None (no match).
     */
    type Cell = Option[Boolean]

    /**
     * Representation of a row (or column or diagonal) of a TicTacToe state.
     * Only the six low bits are significant.
     *
     * CONSIDER using Row(x: Int) extends AnyVal
     */
    type Row = Int
//
//    case class Row(x: Int) extends AnyVal {
//        def &(y: Int): Row = Row(x & y)
//
//        override def toString: String = x.toHexString
//    }

    /**
     * Representation of a Row with a bit mask representing the difference between two states.
     */
    type RowWithMask = (Row, Int)

    /**
     * Implicit class Outcome which allows for two Option[Int] values to be combined.
     *
     * NOTE: currently this class is not used.
     *
     * @param x an Option[Int] on the left of the &
     */
    implicit class Outcome(x: Option[Int]) {

        /**
         * Combine x and y in such a way that x & None = None & x = x.
         * Some(-1) yield x without evaluating y.
         * But Some(x) & Some(y) give None if x != y otherwise Some(x).
         *
         * @param y another Option[Int] (on the right of the &)
         * @return an Option[Int]
         */
        def &(y: => Option[Int]): Option[Int] = x match {
            case None => y
            case Some(-1) => x
            case Some(p) => y match {
                case None => x
                case Some(-1) => y // NOTE: do we need this case?
                case Some(q) => if (p == q) Some(p) else Some(-1)
            }
        }
  }
}
