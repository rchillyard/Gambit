package com.phasmidsoftware.decisiontree.examples.connect4

import com.phasmidsoftware.decisiontree.game.{Move, State, Transition}

/**
  * State[Connect4, Connect4] typeclass instance for Connect Four.
  *
  * P = S = Connect4: the proto-state and state are the same type.
  * construct takes (newState, previousState) and returns newState.
  */
object Connect4State extends State[Connect4, Connect4]:

  def sequence(s: Connect4): Int =
    java.lang.Long.bitCount(s.xBits) + java.lang.Long.bitCount(s.oBits)

  def construct(proto: (Connect4, Connect4)): Connect4 = proto._1

  def isValid(s: Connect4): Boolean = true

  def isWin(s: Connect4): Boolean = s.winner.isDefined

  /**
    * Goal detection:
    * Some(true)  — there is a winner
    * Some(false) — board is full with no winner (draw)
    * None        — game is still in progress
    */
  def isGoal(s: Connect4): Option[Boolean] =
    if s.winner.isDefined then Some(true)
    else if s.isFull then Some(false)
    else None

  /**
    * Heuristic: score from the perspective of the player about to move
    * (!s.player, since s.player is who just moved).
    *
    * For each direction, enumerate all windows of 4 valid cells.
    * Score a window if it contains only my pieces and empty cells:
    * 3 mine + 1 empty = 10 points
    * 2 mine + 2 empty =  1 point
    * Centre column control adds 3 points per piece advantage.
    */
  def heuristic(s: Connect4): Double =
    s.winner match
      case Some(true) => Double.MaxValue // X already won
      case Some(false) => Double.MinValue // O already won
      case None =>
        if s.isFull then 0.0
        else
          val xToMove = !s.player
          val (myBits, opBits) = if xToMove then (s.xBits, s.oBits) else (s.oBits, s.xBits)
          scoreFor(myBits, opBits) - scoreFor(opBits, myBits) + centreBonus(myBits, opBits)

  /**
    * Legal moves: one Transition per open column.
    */
  def moves(s: Connect4): Seq[Transition[Connect4, Connect4]] =
    val isX = !s.player
    s.open.map { col =>
      Move[Connect4, Connect4](
        f = curr => curr.play(col, isX),
        desc = s"col$col"
      )
    }

  def render(s: Connect4): String = s.render

  // ---------------------------------------------------------------------------
  // Heuristic helpers
  // ---------------------------------------------------------------------------

  /**
    * Score all windows of 4 for `myBits` against `opBits`.
    *
    * For each direction shift `sh`, a window starting at bit `p` covers
    * positions p, p+sh, p+2*sh, p+3*sh. We enumerate all windows by
    * shifting `myBits` and `opBits` and counting set bits in each.
    *
    * For each shift direction, `windows(sh)` gives the number of unblocked
    * windows with exactly k of my pieces (and 4-k empty cells).
    */
  private def scoreFor(myBits: Long, opBits: Long): Double =
    var score = 0.0
    for sh <- Connect4.winShifts do
      // A window of 4 has no opponent piece iff opBits has 0 in all 4 positions.
      // Count my pieces and check for blocking simultaneously.
      // We slide the window by shifting: position p is valid if the window
      // stays within the board (handled by boardMask).
      val notOp = ~opBits & Connect4.boardMask

      // Windows with no opponent: all 4 positions must be in notOp.
      val w0 = notOp & (notOp >> sh) & (notOp >> (2 * sh)) & (notOp >> (3 * sh))

      //      println(s"Window shift $sh: w0=$w0, myBits=$myBits, opBits=$opBits")

      // Of those windows, how many of the 4 positions are mine?
      // Count windows with exactly 2 or 3 of my pieces.
      // Use inclusion: windows with >= k mine = intersect k positions from myBits.
      val m1 = myBits & (myBits >> sh) // both pos 0 and 1 are mine
      val m2 = m1 & (m1 >> (2 * sh)) // all 4 are mine (>=4, irrelevant here)
      val anyMine = myBits | (myBits >> sh) | (myBits >> (2 * sh)) | (myBits >> (3 * sh))

      // Windows with 3 mine: exactly 3 of 4 positions in myBits, window unblocked.
      // = windows where exactly one position is empty (in notOp but not myBits).
      val emptyBits = notOp & ~myBits
      val e = emptyBits | (emptyBits >> sh) | (emptyBits >> (2 * sh)) | (emptyBits >> (3 * sh))

      // 3 mine + 1 empty: w0 (unblocked) & m1 (at least 2 consecutive mine) &
      // the 3rd mine exists & exactly 1 empty slot.
      // Simplify: use bitCount of (myBits shifted into window) for each start.
      // Direct approach — enumerate start positions explicitly.
      var p = 0L
      var mask = w0
      //      println(s"w0=$w0, bit=${w0 & (-w0)}, pos=${java.lang.Long.numberOfTrailingZeros(w0 & (-w0))}")
      while mask != 0 do
        val bit = mask & (-mask) // lowest set bit = one window start
        mask &= mask - 1
        // Count my pieces in this window.
        var mine = 0
        var pos = java.lang.Long.numberOfTrailingZeros(bit)
        for k <- 0 until 4 do
          if (myBits & (1L << (pos + k * sh))) != 0 then mine += 1
        if mine == 3 then score += 10.0
        else if mine == 2 then score += 1.0
    //        println(s"mine=$mine, score=$score")
    score

  /**
    * Bonus for centre column control (column 3).
    */
  private def centreBonus(myBits: Long, opBits: Long): Double =
    val centreMask = Connect4.columnMask(3)
    val myCentre = java.lang.Long.bitCount(myBits & centreMask)
    val opCentre = java.lang.Long.bitCount(opBits & centreMask)
    3.0 * (myCentre - opCentre)

  given State[Connect4, Connect4] = Connect4State