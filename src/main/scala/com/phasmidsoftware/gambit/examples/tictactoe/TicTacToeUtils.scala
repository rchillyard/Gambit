package com.phasmidsoftware.gambit.examples.tictactoe

/**
  * Shared utility methods for TicTacToe cell operations.
  */
object TicTacToeUtils:

  /**
    * Given a board diff with exactly one cell changed, return its flat cell index (0..8, row-major).
    * The diff is the XOR of two board values where exactly one cell differs.
    *
    * @param diff the XOR of two board values.
    * @return the flat cell index (0..8) of the changed cell.
    * @throws RuntimeException if no changed cell is found.
    */
  def cellFromDiff(diff: Int): Int =
    (0 until TicTacToe.size * TicTacToe.size).find { i =>
      ((diff >>> (30 - i * 2)) & 3) != 0
    }.getOrElse(throw new RuntimeException(s"cellFromDiff: no cell found in diff $diff"))