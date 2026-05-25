package com.phasmidsoftware.gambit.examples.connect4

import com.phasmidsoftware.gambit.game.{AlphaBetaPlayer, Game, State, TTCache}

/**
  * Connect Four board state using a column-major bitboard representation.
  *
  * Two Longs (xBits, oBits) represent the pieces of each player.
  * Bit index for cell (row, col) = col * 7 + row, where row 0 is the bottom.
  * Sentinel bits at positions 6, 13, 20, 27, 34, 41, 48 are never set during
  * play; they prevent horizontal win detection from wrapping across columns.
  *
  * `heights` tracks the next available row index per column (0..5).
  * A column is full when heights(col) == Connect4.rows.
  *
  * You can read more about the game here: [[https://en.wikipedia.org/wiki/Connect_Four]]
  *
  * @param xBits   bit positions of X's pieces
  * @param oBits   bit positions of O's pieces
  * @param heights next available row per column
  */
case class Connect4(xBits: Long, oBits: Long, heights: Vector[Int]):

  /**
    * The player who just moved to create this state.
    * true = X just moved (odd number of total pieces).
    * false = O just moved or empty board (even number).
    */
  lazy val player: Boolean =
    (java.lang.Long.bitCount(xBits) + java.lang.Long.bitCount(oBits)) % 2 == 1

  /**
    * The open (non-full) column indices, in order 0..6.
    */
  lazy val open: Seq[Int] =
    (0 until Connect4.cols).filter(col => heights(col) < Connect4.rows)

  /**
    * Drop a piece into the given column. The piece lands on the lowest open row.
    * Does not validate that the column is open — callers should check `open`.
    *
    * @param col the column index (0..6).
    * @param isX true if X is playing, false if O.
    * @return the new Connect4 state.
    */
  def play(col: Int, isX: Boolean): Connect4 =
    val bit = 1L << (col * Connect4.stride + heights(col))
    val newHeights = heights.updated(col, heights(col) + 1)
    if isX then Connect4(xBits | bit, oBits, newHeights)
    else Connect4(xBits, oBits | bit, newHeights)

  /**
    * Whether the board is full (draw condition, assuming no winner).
    */
  lazy val isFull: Boolean = open.isEmpty

  /**
    * Detect whether the given bitboard has four in a row.
    * Uses the column-major sentinel layout for correct horizontal detection.
    */
  private def hasLine(bits: Long): Boolean =
    val h = bits & (bits >> Connect4.stride) // horizontal
    val v = bits & (bits >> 1) // vertical
    val d1 = bits & (bits >> (Connect4.stride - 1)) // diagonal /
    val d2 = bits & (bits >> (Connect4.stride + 1)) // diagonal \
    (h & (h >> (Connect4.stride * 2))) != 0 ||
      (v & (v >> 2)) != 0 ||
      (d1 & (d1 >> ((Connect4.stride - 1) * 2))) != 0 ||
      (d2 & (d2 >> ((Connect4.stride + 1) * 2))) != 0

  /**
    * The winner of this position, if any.
    * Some(true) = X wins, Some(false) = O wins, None = no winner yet.
    */
  lazy val winner: Option[Boolean] =
    if hasLine(xBits) then Some(true)
    else if hasLine(oBits) then Some(false)
    else None

  /**
    * Render the board as a String, top row first.
    * X = X, O = 0, empty = .
    */
  def render: String =
    val sb = new StringBuilder
    for row <- (Connect4.rows - 1) to 0 by -1 do
      for col <- 0 until Connect4.cols do
        val bit = 1L << (col * Connect4.stride + row)
        val ch = if (xBits & bit) != 0 then 'X'
        else if (oBits & bit) != 0 then '0'
        else '.'
        sb.append(ch)
      sb.append('\n')
    sb.toString

  override def toString: String = render

object Connect4:
  /** Number of columns. */
  val cols: Int = 7

  /** Number of rows. */
  val rows: Int = 6

  /**
    * Column stride in the bitboard: rows + 1 sentinel bit per column.
    * The sentinel bit at position col*stride + rows is never set during play,
    * preventing horizontal win detection from wrapping across columns.
    */
  val stride: Int = rows + 1 // = 7

  /**
    * Shift amounts for win detection in each of the four directions:
    * stride     = horizontal (one column apart)
    * 1          = vertical   (one row apart)
    * stride - 1 = diagonal /
    * stride + 1 = diagonal \
    */
  val winShifts: Seq[Int] = Seq(stride, 1, stride - 1, stride + 1)

  /**
    * Bitmask for all cells in the given column.
    *
    * @param col the column index (0..6).
    * @return a `Long` with bits set for all rows in that column.
    */
  def columnMask(col: Int): Long =
    ((1L << rows) - 1L) << (col * stride)

  /**
    * Bitmask covering all valid (non-sentinel) cell positions.
    * Must be defined after columnMask.
    */
  val boardMask: Long =
    (0 until cols).foldLeft(0L)((mask, col) => mask | columnMask(col))

  /** The empty starting position. */
  val start: Connect4 = Connect4(0L, 0L, Vector.fill(cols)(0))

  /**
    * Parse a Connect4 position from a string representation.
    * The string is read top-to-bottom, left-to-right.
    * 'X' = X, '0' or 'O' = O, '.' or ' ' = empty.
    * Newlines and '-' are stripped.
    * Must contain exactly rows*cols meaningful characters.
    *
    * @param s the string to parse.
    * @return a Connect4 position.
    */
  def parse(s: String): Connect4 =
    val stripped = s.replaceAll("[\n\\-]", "")
    require(stripped.length == rows * cols,
      s"Connect4.parse: expected ${rows * cols} chars, got ${stripped.length}")
    var xBits = 0L
    var oBits = 0L
    val heights = Array.fill(cols)(0)
    // Parse top-to-bottom, fill bottom-to-top.
    val cells = stripped.toCharArray

    def bitMask(row: Int, col: Int) = 1L << (col * stride + row)

    for row <- (rows - 1) to 0 by -1 do
      for col <- 0 until cols do
        val idx = (rows - 1 - row) * cols + col
        cells(idx) match
          case 'X' | 'x' =>
            xBits |= bitMask(row, col)
            if heights(col) <= row then heights(col) = row + 1
          case '0' | 'O' | 'o' =>
            oBits |= bitMask(row, col)
            if heights(col) <= row then heights(col) = row + 1
          case '.' | ' ' => // empty
          case c => throw new IllegalArgumentException(s"Connect4.parse: illegal char '$c'")
    Connect4(xBits, oBits, heights.toVector)


/**
  * A Connect Four AI player using the alpha-beta pruning optimization technique
  * for minimax search. The player aims to determine the optimal moves for winning
  * or forcing a draw in the game.
  *
  * @param me    Specifies the player's identity in the game.
  *              True indicates the player is X, false indicates the player is O.
  * @param depth The maximum depth of the search tree used in alpha-beta pruning.
  *              Defaults to 6.
  * @param keyFn An optional function that generates a unique key for the
  *              Transposition Table (TT), to identify equivalent game states.
  *              Defaults to None.
  *
  *              Uses contextual abstractions:
  *              - `state` for managing the transformation of game states.
  *              - `game` for game-specific logic, including rules and evaluation utilities.
  *              - `ttCache` as a caching mechanism for storing precomputed game states
  *              during alpha-beta expansion for performance optimization.
  *
  *              Extends:
  *              - AlphaBetaPlayer, a generic implementation of alpha-beta search AI
  *              for turn-based games, parameterized by the game state, result, move,
  *              player type, and Transposition Table key type.
  */
class AlphaBetaPlayerConnect4(
                               me: Boolean,
                               depth: Int = 6
                             )(using state: State[Connect4, Connect4], game: Game[Connect4, Int, Boolean], ttCache: TTCache[(Long, Long)])
  extends AlphaBetaPlayer[Connect4, Connect4, Int, Boolean, (Long, Long)](me, depth)(using state, game, ttCache)