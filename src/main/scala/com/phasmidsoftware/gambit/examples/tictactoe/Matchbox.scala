package com.phasmidsoftware.gambit.examples.tictactoe

import scala.util.Random

/**
  * A Matchbox represents one position in the MENACE machine.
  * It holds a bead count for each legal move (open cell index 0..8).
  * Weighted random selection picks a move proportional to bead counts.
  * After a game, beads are added (win) or removed (loss), with a floor of 1.
  *
  * @param beads a Map from cell index (0..8, row-major) to bead count.
  */
case class Matchbox(beads: Map[Int, Int]) {

  /**
    * Select a move by weighted random sampling over the bead counts.
    * Returns None if the matchbox is empty (should not happen in normal play).
    *
    * @param random a Random instance for reproducibility.
    * @return Some(cellIndex) or None.
    */
  def select(random: Random): Option[Int] = {
    val total = beads.values.sum
    if (total == 0) None
    else {
      val pick = random.nextInt(total)
      // Walk the entries accumulating weight until we exceed pick.
      beads.iterator.scanLeft((0, -1)) { case ((acc, _), (cell, count)) =>
        (acc + count, cell)
      }.dropWhile(_._1 <= pick).nextOption().map(_._2)
    }
  }

  /**
    * Reward: add `delta` beads to the given cell. Used after a win.
    *
    * @param cell  the cell index that was played.
    * @param delta the number of beads to add (typically 3 for a win).
    * @return a new Matchbox with updated beads.
    */
  def reward(cell: Int, delta: Int = Matchbox.winDelta): Matchbox =
    copy(beads = beads.updatedWith(cell)(_.map(_ + delta)))

  /**
    * Penalise: remove `delta` beads from the given cell, floored at `floor`.
    * Used after a loss.
    *
    * @param cell  the cell index that was played.
    * @param delta the number of beads to remove (typically 1 for a loss).
    * @param floor the minimum bead count (default 1, so a move is never impossible).
    * @return a new Matchbox with updated beads.
    */
  def penalise(cell: Int, delta: Int = Matchbox.lossDelta, floor: Int = Matchbox.beadFloor): Matchbox =
    copy(beads = beads.updatedWith(cell)(_.map(n => math.max(floor, n - delta))))

  /**
    * The number of distinct moves this matchbox knows about.
    */
  def moveCount: Int = beads.size

  /**
    * Total beads across all moves.
    */
  def totalBeads: Int = beads.values.sum

  override def toString: String =
    beads.toSeq.sortBy(_._1).map { case (k, v) => s"$k:$v" }.mkString("Matchbox(", ", ", ")")
}

object Matchbox {

  /** Initial bead count per move. Original MENACE used 4 for the opening, tapering down. */
  val initialBeads: Int = 4

  /** Beads added to each move in the winning matchboxes. */
  val winDelta: Int = 3

  /** Beads removed from each move in the losing matchboxes. */
  val lossDelta: Int = 1

  /** Minimum beads per move — never zero so the move remains selectable. */
  val beadFloor: Int = 1

  /**
    * Construct a fresh Matchbox for a position with the given open cells.
    * Each open cell starts with `initialBeads` beads.
    *
    * @param openCells sequence of open cell indices (0..8, row-major).
    * @return a new Matchbox.
    */
  def apply(openCells: Seq[Int]): Matchbox =
    Matchbox(openCells.map(_ -> initialBeads).toMap)

  /**
    * Construct a Matchbox from a TicTacToe position.
    * Open cells are derived from the board's open sequence, converted to flat indices.
    *
    * @param ttt the TicTacToe position.
    * @return a new Matchbox with one entry per open cell.
    */
  def fromPosition(ttt: TicTacToe): Matchbox =
    apply(ttt.open.map { case (r, c) => r * TicTacToe.size + c })
}