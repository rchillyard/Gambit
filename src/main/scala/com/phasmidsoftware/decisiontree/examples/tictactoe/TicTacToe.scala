package com.phasmidsoftware.decisiontree.examples.tictactoe

import com.phasmidsoftware.decisiontree.examples.tictactoe.TicTacToe.{Prototype, rowsWithMask, size}
import com.phasmidsoftware.decisiontree.examples.tictactoe.TicTacToeOps.*
import com.phasmidsoftware.decisiontree.game.{Move, State, Transition}
import com.phasmidsoftware.flog.{Flog, Loggable}
import com.phasmidsoftware.util.Aggregators.{hasOne, hasTwo}
import com.phasmidsoftware.util.{DecisionTreeException, Shuffle}

import scala.util.{Failure, Success, Try}

/**
 * Case class to represent a TicTacToe state (layout).
 *
 * @param board      the current state.
 * @param maybePrior the prior state.
 */
case class TicTacToe(board: Board, maybePrior: Option[TicTacToe] = None) {

  /**
   * Defines a Matching type which takes a Row and returns a Cell.
   */
  type Matching = RowWithMask => Cell

  /**
   * Method to determine which player was responsible for creating this state.
   * The empty state will yield false (0 player).
   * A goal state will yield the winner.
   *
   * @return true for the first player (X), false for the second player (0)
   */
  lazy val player: Boolean =
    (size * size - open.size) % 2 == 1

  /**
   * Method to determine whether there is a line of marks for one player.
   * The line may be horizontal (a row), vertical (a column) or diagonal.
   *
   * @return Some(b) if there is a line of similar marks in this TicTacToe otherwise None.
   *         The Boolean b is true for player X, and false for player 0.
   */
  lazy val win: Cell =
    isWin(rowsR0) orElse isWin(rowsL0) orElse isWin(diagonals)

  /**
   * Method to determine whether there is a block by one player against his opponent..
   * The line may be horizontal (a row), vertical (a column) or diagonal.
   *
   * @return Some(b) if there is a line of 0X0 or similar otherwise None.
   *         The Boolean b is true for player X, and false for player 0.
   */
  lazy val block: Cell =
    isBlock(rowsR0) orElse isBlock(rowsL0) orElse isBlock(diagonals)

  /**
   * Method to determine whether there are two potential wins.
   * The lines may be horizontal (a row), vertical (a column) or diagonal and missing one element.
   *
   * @return true if there is a line of two similar marks with a space between in this TicTacToe.
   *         The Boolean b is true for player X, and false for player 0.
   */
  lazy val fork: Cell =
    hasTwo(for (r <- List(rowsR0, rowsL0, diagonals)) yield isPendingWin(r))(_.isDefined) match {
      case Some(x -> _) => x
      case None => None
    }

  /**
   * Method to determine whether there is a potential win.
   * The line may be horizontal (a row), vertical (a column) or diagonal and missing one element.
   *
   * @return true if there is a line of two similar marks with a space between in this TicTacToe.
   *         The Boolean b is true for player X, and false for player 0.
   */
  lazy val potentialWin: Cell =
    hasOne(for (r <- List(rowsR0, rowsL0, diagonals)) yield isPendingWin(r))(_.isDefined).flatten

  /**
   * Method to create a new TicTacToe from this TicTacToe.
   *
   * @param xOrO true if X is to play, false otherwise.
   * @param row  the row at which the mark should be made.
   * @param col  the column at which the mark should be made.
   * @return a new Board with the appropriate cell (i.e. square) marked.
   */
  def play(xOrO: Boolean)(row: Int, col: Int): Prototype =
    board.play(board.sequence + 1, xOrO, row, col) -> this

  /**
   * Method to create a string of Xs and 0s corresponding to this TicTacToe position.
   * Also includes the heuristic for the position.
   *
   * CONSIDER adding a parameter to allow dropping the heuristic.
   *
   * @return a String which is a rendition of the current state.
   */
  def render(newlines: Boolean = false): String =
    if (newlines) s"\n${TicTacToeOps.renderWithNewlines(board.value)} ($heuristic)" else s"${TicTacToeOps.render(board.value)} ($heuristic)"

  /**
   * The history of a TicTacToe position, as a String.
   *
   * NOTE: it seems that this is a general method, not specific to TicTacToe
   */
  lazy val history: List[String] = maybePrior match {
    case None => List("")
    case Some(x) => x.history :+ render(true) // XXX true makes the history easier to read.
  }

//  /**
//   * The count of open cells for this TicTacToe. If you need the actual open cells as well as the count, use <code>open</code> instead.
//   *
//   * TESTME
//   *
//   * @return the number of vacancies.
//   */
//  lazy val vacancies: Int = TicTacToeOps.vacancies(board.value)

  /**
   * The list of open cells for this TicTacToe.
   *
   * @return a sequence of (Int, Int) tuples corresponding to the row, column indices.
   */
  lazy val open: Seq[(Int, Int)] = {
    val zs: Array[Int] = TicTacToeOps.open(board.value)
    val q = for (z <- zs) yield z / size -> z % size
    q.toList // CONSIDER returning q as is.
  }

  /**
   * Function to make a play for the X player at a cell.
   */
  def playX: (Int, Int) => Prototype =
    play(xOrO = true)(_, _)

  /**
   * Function to make a play for the 0 player at a cell.
   */
  def play0: (Int, Int) => Prototype =
    play(xOrO = false)(_, _)

  /**
   * HashCode method based on the board only (not prior).
   *
   * @return a hashCode.
   */
  override def hashCode(): Int = board.hashCode()

  /**
   * Equals method based on the board only.
   *
   * @return Boolean.
   */
  override def equals(obj: Any): Boolean = obj match {
    case TicTacToe(b, _) => board == b
    case _ => false
  }

  /**
   * Method to determine if this layout is a draw, i.e. no side can ever win.
   *
   * TODO figure out a better way to determine a draw.
   *
   * @return Some(false) if it's a draw, else None.
   */
  def draw: Option[Boolean] =
    if (open.isEmpty) Some(false) else None

  private val flog = Flog[TicTacToe]

  import flog.*

  private def messageString =
    s" player=$player given prior=$maybePrior choose ${board.render} ($board) for score of "

  private lazy val heuristic: Double =
    if (board.isEmpty) 0
    else win match {
      case Some(x) if x == player =>
        s"Win:" + messageString !? 7
      case None =>
        assessBlock
      case Some(_) =>
        throw DecisionTreeException("logic error: opponent win")
    }

  private lazy val assessBlock: Double = block match {
    case Some(y) if y == player =>
      s"Block by" + messageString !? 6
    case None =>
      assessFork
    case Some(_) =>
      throw DecisionTreeException("logic error: opponent block")
  }

  private lazy val assessFork: Double = fork match {
    case Some(x) if x == player => s"Fork by" + messageString !? 5
    case _ => assessStrategy
    //    case Some(_) => throw DecisionTreeException("Logic error: opponent fork")
  }

  private lazy val assessStrategy: Double =
    if (corner && oppHasCenter && weHaveOppositeCorner)
      "Strategic move: corner/centerOpp/ourOpp" + messageString !? 4.5
    else assessPotentialWin

  private lazy val assessPotentialWin = potentialWin match {
    case Some(x) if x == player => s"Potential win by" + messageString !? 4
    case _ => assessTactics
  }

  private lazy val assessTactics = {
    val tacticalMove = "Tactical move: "
    if (firstAndTopLeftCorner)
      tacticalMove + "firstAndTopLeftCorner" + messageString !? 4
    else if (center)
      tacticalMove + "center" + messageString !? 3
    else if (oppositeCorner(true))
      tacticalMove + "opposite corner" + messageString !? 2
    else if (corner)
      tacticalMove + "corner" + messageString !? 1
    else
      tacticalMove + "default" + messageString !? 0
  }

  private lazy val center = currentMove.center

  lazy val currentMove: Board =
    maybePrior map (p => board ^ p.board) getOrElse board

  lazy val maybeOpponentMove: Option[Board] =
    maybePrior map (_.currentMove)

  lazy val maybePreviousMove: Option[Board] =
    maybePrior flatMap (_.maybeOpponentMove)

  /**
   * @param opponent if true then the opposite corner must be the opponent.
   * @return true if this TicTacToe is a corner position and if the opponent (if true, else self) occupies the opposite corner.
   */
  def oppositeCorner(opponent: Boolean): Boolean = {
    val maskTopLeft = 0xC0000000
    val patternTopLeft = if (opponent) maskTopLeft else 0
    val f = maskMatch(maskTopLeft, patternTopLeft)(_, _)
    corner && (f(r0, r2) || f(r1, r3))
  }

  /**
   * @param b1 a Board.
   * @param b2 another Board.
   * @return true if the XOR of the two boards masked by mask is equal to pattern.
   */
  def maskMatch(mask: Int, pattern: Int)(b1: Board, b2: Board): Boolean =
    ((b1.value ^ b2.value) & mask) == pattern

  lazy val corner: Boolean = currentMove.corner

  lazy val oppHasCenter: Boolean =
    maybePrior exists (_.center)

  lazy val weHaveOppositeCorner: Boolean =
    maybePrior flatMap (_.maybePrior) exists (_.oppositeCorner(!player))

  private lazy val firstAndTopLeftCorner = board.value == 0x40000000

  private def rowTupled(i: Int): Row =
    TicTacToe.row(board)(i) // NOTE: used by unit tests

  private def isMatch(f: Matching)(rs: LazyList[RowWithMask]): Cell =
    rs.map(f).foldLeft[Cell](None)((result, cell) => result orElse cell)

  private def isWin(rs: LazyList[RowWithMask]): Cell =
    isMatch(isLine)(rs)

  private def isPendingWin(rs: LazyList[RowWithMask]): Cell =
    isMatch(isLinePending)(rs)

  private def isBlock(rs: LazyList[RowWithMask]): Cell =
    isMatch(isBlocking)(rs)

  private def isLine(x: RowWithMask): Cell = rowLine(x._1) match {
    case 1 => Some(true)
    case 2 => Some(false)
    case _ => None
  }

  private def isLinePending(x: RowWithMask): Cell = rowLinePending(x._1) match {
    case 1 => Some(true)
    case 2 => Some(false)
    case _ => None
  }

  private def isBlocking(x: RowWithMask): Cell = rowLineBlocking(x._1, x._2) match {
    case 1 => Some(true)
    case 2 => Some(false)
    case _ => None
  }

  private lazy val differenceTransposed: Board = currentMove.transpose
  private lazy val r0: Board = board
  private lazy val l0: Board = board.transpose
  private lazy val r1: Board = r0.rotate
  private lazy val r2: Board = r1.rotate
  private lazy val r3: Board = r2.rotate
  private lazy val rowsR0: LazyList[RowWithMask] = rowsWithMask(r0, currentMove.value)
  private lazy val rowsL0: LazyList[RowWithMask] = rowsWithMask(l0, differenceTransposed.value)
  private lazy val diagR: RowWithMask = diagonal(r0.value) -> diagonal(currentMove.value)
  private lazy val diagL: RowWithMask = diagonal(l0.value) -> diagonal(differenceTransposed.value)
  private lazy val diagonals: LazyList[RowWithMask] = diagR #:: diagL #:: LazyList.empty
}

object TicTacToe {

  type Prototype = (Board, TicTacToe)

  val size: Int = 3

  /**
   * Trait which extends the type class State with a concrete underlying type of TicTacToe.
   */
  trait TicTacToeState$ extends State[Board, TicTacToe] {

    /**
     * a significant sequence value that distinguishes this state from others and which can be derived from a P.
     *
     * @param s parameter from which we may derive the sequence.
     */
    def sequence(s: TicTacToe): Int = s.board.sequence

    /**
     * Method to construct an S from a proto-state:
     *
     * @param proto a (Board, TicTacToe) tuple.
     * @return a TicTacToe.
     */
    def construct(proto: (Board, TicTacToe)): TicTacToe =
      TicTacToe(proto._1, Some(proto._2))

    /**
     * In this game, all states are valid.
     *
     * @param s a state.
     * @return true.
     */
    def isValid(s: TicTacToe): Boolean = true

    /**
     * How close are we to winning?
     *
     * @param s a state.
     * @return the number of our aligned cells - their aligned cells.
     */
    def heuristic(s: TicTacToe): Double = s.heuristic

    /**
     * Method to determine if s is a winning state.
     * NOTE: it makes no sense to invoke isWin unless the result of isGoal is Some(true).
     *
     * @param s an S
     * @return true if s is a win, else false.
     */
    def isWin(s: TicTacToe): Boolean = s.win match {
      case Some(b) =>
        b == s.player
      case _ =>
        false
    }

    /**
     * Have we reached a result? And, if so, who won?
     *
     * @param s a (current) state.
     * @return a Option[Boolean]: if None then this state is not a goal state.
     *         If Some(b) then:
     *         if b is true, we got a definite result and the winner is determined by examining s in more detail.
     *         If b is false, we got a partial result and we should continue to seek a more definite result.
     */
    def isGoal(s: TicTacToe): Option[Boolean] =
      s.win map (_ => true) orElse s.draw

    /**
     * Return all of the possible transitions from the given state.
     *
     * CONSIDER refactoring so that we do not discard the state when constructing a Move.
     *
     * @param s a state.
     * @return a sequence of Transition[S]
     */
    def moves(s: TicTacToe): Seq[Transition[Board, TicTacToe]] = {
      val zs: Seq[(Int, Int)] = Shuffle(s.open, 3L) // we arbitrarily always want X to win
      val f: TicTacToe => (Int, Int) => Prototype = t => if (s.player) t.play0 else t.playX
      for (z <- zs) yield Move[Board, TicTacToe](x => f(x)(z._1, z._2)._1, z.toString())
    }

    def render(s: TicTacToe): String = s.render()
  }

  implicit object TicTacToeState$ extends TicTacToeState$

  /**
   * Method to construct a starting position TicTacToe.
   *
   * @param board the Board which defines a TicTacToe.
   * @return a TicTacToe with the given board, no predecessor and an empty Priority Queue.
   */
  def apply(board: Board): TicTacToe = apply(board, None)

  /**
   * Method to construct a starting position TicTacToe.
   *
   * @param proto (Board, TicTacToe).
   * @return a TicTacToe with the given board, no predecessor and an empty Priority Queue.
   */
  def apply(proto: (Board, TicTacToe)): TicTacToe =
    apply(proto._1, Some(proto._2))

  /**
   * Method to construct a starting position TicTacToe.
   *
   * @return a TicTacToe with all empty cells, no predecessor and an empty Priority Queue.
   */
  def apply(): TicTacToe =
    apply(Board(0, 0))

  /**
   * Apply method mostly for testing.
   *
   * TEST not currently used.
   *
   * @param board the current Board.
   * @param mask  a mask which defines the bits to be eliminated from board to yield the previous Board.
   * @return a TicTacToe.
   */
  def apply(board: Board, mask: Int): TicTacToe =
    TicTacToe(board, Some(previous(board.sequence - 1, board.value, mask)))

  /**
   * Method to construct a TicTacToe with prior from a particular bit pattern and a mask.
   *
   * @param x               the bit pattern.
   * @param maybePriorBoard an optional bit pattern to yield the prior state.
   * @return a TicTacToe with all empty cells.
   */
  def from(sequence: Row, x: Row, maybePriorBoard: Option[Board]): TicTacToe = TicTacToe(
    Board(sequence, x),
    maybePriorBoard.map((board: Board) => previous(board.sequence - 1, x, board.value))
  )

  /**
   * Method to parse a pattern for a starting position.
   * NOTE: do not use for later positions.
   *
   * @param s a String made up of 9 case-independent characters, each of which must be an X, 0, O, ., or space.
   *          CONSIDER allowing newlines.
   * @return a TicTacToe.
   */
  def parseString(s: String, maybeMask: Option[Int]): TicTacToe = {
    val cells = s.toCharArray.toSeq map {
      case ' ' | '.' => 0
      case 'X' | 'x' => 1
      case '0' | 'o' | 'O' => 2
      case x => throw DecisionTreeException(s"TicTacToe: illegal character: $x")
    }
    val sequence = s.replace(" ", "").replace(".", "").length
    if (cells.length >= 9) TicTacToe.from(sequence, TicTacToeOps.parseArray(cells.toArray), maybeMask map (Board(sequence, _)))
    else throw DecisionTreeException("insufficient elements")
  }

  /**
   * Method to parse a String of Xs and 0s into a TicTacToe, wrapped in Try.
   *
   * CONSIDER making this private.
   *
   * @param s the String to parse (which may include \n characters for better visualization).
   * @return a Try of TicTacToe.
   */
  def parse(s: String, maybeMask: Option[Int] = None): Try[TicTacToe] = {
    val x = s.replaceAll("""[\n\-]""", "")
    if (x.length == TicTacToe.size * TicTacToe.size) Success(parseString(x, maybeMask))
    else Failure(DecisionTreeException(s"TicTacToe: parse failure: $s"))
  }

  // XXX the starting position (all nine empty cells).
  val start: TicTacToe = apply()

  private def previous(sequence: Int, value: Int, mask: Int) =
    TicTacToe(Board(sequence, value ^ mask))

  private def row(board: Board)(i: Int): Row = board.row(i)

  private def rowsWithMask(board: Board, mask: Int) =
    LazyList.from(0).take(size).map(i => TicTacToe.row(board)(i) -> TicTacToeOps.row(mask, i))

  implicit object loggableTicTacToe extends Loggable[TicTacToe] {
    def toLog(t: TicTacToe): String = t.render()
  }
}

/**
 * This class represents 9 x 2 bits, at the high end of the 32-bit word.
 *
 * NOTE: this used to extend AnyVal before we added the sequence parameter.
 *
 * @param value the bit value of this board.
 *              NOTE: that the low 14 bits of this value should always be zero.
 */
case class Board(sequence: Int, value: Int) {
  def isEmpty: Boolean = value == 0

  def row(i: Int): Row =
    TicTacToeOps.row(value, i)

  def ^(b: Board): Board =
    Board(b.sequence, value ^ b.value) // XXX could use copy

  def &(b: Board): Board =
    Board(b.sequence, value & b.value)

  def |(b: Board): Board =
    Board(b.sequence, value | b.value)

  override def toString: String =
    s"$sequence: ${value.toHexString}"

  def render: String =
    s"$sequence: ${TicTacToeOps.render(value)}"

  def play(sequence: Int, xOrO: Boolean, row: Row, col: Row): Board =
    Board(sequence, playBoard(value, xOrO, row, col))

  def transpose: Board =
    Board(sequence, transposeBoard(value))

  def rotate: Board =
    Board(sequence, rotateBoard(value))

  def exchange: Board =
    Board(sequence, exchangeBoard(value))

  def corner: Boolean =
    (value & 0xCC0CC000) != 0

  def center: Boolean =
    (value & 0xC00000) != 0

}
