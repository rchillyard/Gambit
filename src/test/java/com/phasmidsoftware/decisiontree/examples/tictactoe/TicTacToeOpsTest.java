package com.phasmidsoftware.decisiontree.examples.tictactoe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for TicTacToeOps.
 * <p>
 * Bit encoding reminder:
 * Each cell occupies 2 bits; cell 0 is at bits 30-31, cell 8 at bits 14-15.
 * 00 = empty, 01 = X, 10 = O.
 * X at cell i: 1 << (30 - i*2)
 * O at cell i: 2 << (30 - i*2)
 */
public class TicTacToeOpsTest {

    // -----------------------------------------------------------------------
    // parseArray
    // -----------------------------------------------------------------------

    @Test
    public void parseArray_emptyBoard() {
        int[] a = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0};
        assertEquals(0, TicTacToeOps.parseArray(a));
    }

    @Test
    public void parseArray_xAtCell0() {
        // X (=1) at cell 0 only.
        int[] a = new int[]{1, 0, 0, 0, 0, 0, 0, 0, 0};
        assertEquals(0x40000000, TicTacToeOps.parseArray(a));
    }

    @Test
    public void parseArray_oAtCell0() {
        // O (=2) at cell 0 only.
        int[] a = new int[]{2, 0, 0, 0, 0, 0, 0, 0, 0};
        assertEquals(0x80000000, TicTacToeOps.parseArray(a));
    }

    @Test
    public void parseArray_xAtCell8() {
        // X at cell 8 (bottom-right).
        int[] a = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 1};
        assertEquals(0x00004000, TicTacToeOps.parseArray(a));
    }

    @Test
    public void parseArray_xTopRow() {
        // X at cells 0,1,2.
        int[] a = new int[]{1, 1, 1, 0, 0, 0, 0, 0, 0};
        int result = TicTacToeOps.parseArray(a);
        assertEquals(0x15, TicTacToeOps.row(result, 0));
    }

    // -----------------------------------------------------------------------
    // open
    // -----------------------------------------------------------------------

    @Test
    public void open_emptyBoard() {
        int[] result = TicTacToeOps.open(0);
        assertEquals(9, result.length);
        for (int i = 0; i < 9; i++) assertEquals(i, result[i]);
    }

    @Test
    public void open_xAtCell0() {
        int board = 0x40000000; // X at cell 0
        int[] result = TicTacToeOps.open(board);
        assertEquals(8, result.length);
        assertEquals(1, result[0]); // cell 0 is occupied; cell 1 is the first open
    }

    @Test
    public void open_fullBoard() {
        // All cells occupied (arbitrary values, just non-zero non-open pairs).
        // X at all 9 cells.
        int[] a = new int[]{1, 1, 1, 1, 1, 1, 1, 1, 1};
        int board = TicTacToeOps.parseArray(a);
        int[] result = TicTacToeOps.open(board);
        assertEquals(0, result.length);
    }

    // -----------------------------------------------------------------------
    // render
    // -----------------------------------------------------------------------

    @Test
    public void render_emptyBoard() {
        assertEquals("...-...-...-", TicTacToeOps.render(0));
    }

    @Test
    public void render_xAtCell0() {
        int board = 0x40000000;
        assertEquals("X..-...-...-", TicTacToeOps.render(board));
    }

    @Test
    public void render_oAtCell4() {
        // O at centre (cell 4).
        int[] a = new int[]{0, 0, 0, 0, 2, 0, 0, 0, 0};
        int board = TicTacToeOps.parseArray(a);
        assertEquals("...-.0.-...-", TicTacToeOps.render(board));
    }

    @Test
    public void render_xTopRow() {
        int[] a = new int[]{1, 1, 1, 0, 0, 0, 0, 0, 0};
        int board = TicTacToeOps.parseArray(a);
        assertEquals("XXX-...-...-", TicTacToeOps.render(board));
    }

    // -----------------------------------------------------------------------
    // playBoard
    // -----------------------------------------------------------------------

    @Test
    public void playBoard_xAtCell0() {
        int result = TicTacToeOps.playBoard(0, true, 0, 0);
        assertEquals(0x40000000, result);
    }

    @Test
    public void playBoard_oAtCell4() {
        int result = TicTacToeOps.playBoard(0, false, 1, 1);
        assertEquals(TicTacToeOps.parseArray(new int[]{0, 0, 0, 0, 2, 0, 0, 0, 0}), result);
    }

    @Test
    public void playBoard_xAtCell8() {
        int result = TicTacToeOps.playBoard(0, true, 2, 2);
        assertEquals(0x00004000, result);
    }

    @Test
    public void playBoard_doesNotOverwrite() {
        // Playing X at cell 0, then O at cell 0 should OR (not replace).
        int board = TicTacToeOps.playBoard(0, true, 0, 0); // X at (0,0)
        int result = TicTacToeOps.playBoard(board, false, 0, 0); // O at (0,0) — ORs in
        // Both bits set = 11 (corrupted) but OR behaviour is documented.
        assertTrue(result != board);
    }

    // -----------------------------------------------------------------------
    // row
    // -----------------------------------------------------------------------

    @Test
    public void row_emptyBoard() {
        assertEquals(0, TicTacToeOps.row(0, 0));
        assertEquals(0, TicTacToeOps.row(0, 1));
        assertEquals(0, TicTacToeOps.row(0, 2));
    }

    @Test
    public void row_xTopRow() {
        int[] a = new int[]{1, 1, 1, 0, 0, 0, 0, 0, 0};
        int board = TicTacToeOps.parseArray(a);
        assertEquals(0x15, TicTacToeOps.row(board, 0)); // XXX = 010101
        assertEquals(0, TicTacToeOps.row(board, 1));
        assertEquals(0, TicTacToeOps.row(board, 2));
    }

    @Test
    public void row_oMiddleRow() {
        int[] a = new int[]{0, 0, 0, 2, 2, 2, 0, 0, 0};
        int board = TicTacToeOps.parseArray(a);
        assertEquals(0, TicTacToeOps.row(board, 0));
        assertEquals(0x2A, TicTacToeOps.row(board, 1)); // 000 = 101010
        assertEquals(0, TicTacToeOps.row(board, 2));
    }

    // -----------------------------------------------------------------------
    // rowLine
    // -----------------------------------------------------------------------

    @Test
    public void rowLine_empty() {
        assertEquals(0, TicTacToeOps.rowLine(0));
    }

    @Test
    public void rowLine_xWins() {
        assertEquals(1, TicTacToeOps.rowLine(0x15)); // XXX
    }

    @Test
    public void rowLine_oWins() {
        assertEquals(2, TicTacToeOps.rowLine(0x2A)); // 000
    }

    @Test
    public void rowLine_noLine() {
        // X at cell 0, O at cell 1 — mixed row, no line.
        assertEquals(0, TicTacToeOps.rowLine(0x09)); // X0. = 00 10 01 ... not a line
    }

    // -----------------------------------------------------------------------
    // rowLinePending
    // -----------------------------------------------------------------------

    @Test
    public void rowLinePending_xTwoInRow() {
        assertEquals(1, TicTacToeOps.rowLinePending(0x14)); // XX.
        assertEquals(1, TicTacToeOps.rowLinePending(0x11)); // X.X
        assertEquals(1, TicTacToeOps.rowLinePending(0x05)); // .XX
    }

    @Test
    public void rowLinePending_oTwoInRow() {
        assertEquals(2, TicTacToeOps.rowLinePending(0x28)); // 00.
        assertEquals(2, TicTacToeOps.rowLinePending(0x22)); // 0.0
        assertEquals(2, TicTacToeOps.rowLinePending(0x0A)); // .00
    }

    @Test
    public void rowLinePending_noThreat() {
        assertEquals(0, TicTacToeOps.rowLinePending(0));
        assertEquals(0, TicTacToeOps.rowLinePending(0x15)); // full XXX — not pending
    }

    // -----------------------------------------------------------------------
    // rowLineBlocking
    // -----------------------------------------------------------------------

    @Test
    public void rowLineBlocking_xBlocksO() {
        // 00X pattern — X (=1) blocks O (=2), blocking cell in mask.
        assertEquals(1, TicTacToeOps.rowLineBlocking(0x29, 0x01));
        assertEquals(1, TicTacToeOps.rowLineBlocking(0x26, 0x04));
        assertEquals(1, TicTacToeOps.rowLineBlocking(0x1A, 0x10));
    }

    @Test
    public void rowLineBlocking_oBlocksX() {
        // XX0 pattern — O (=2) blocks X (=1).
        assertEquals(2, TicTacToeOps.rowLineBlocking(0x19, 0x08));
        assertEquals(2, TicTacToeOps.rowLineBlocking(0x16, 0x02));
        assertEquals(2, TicTacToeOps.rowLineBlocking(0x25, 0x20));
    }

    @Test
    public void rowLineBlocking_noBlocking() {
        assertEquals(0, TicTacToeOps.rowLineBlocking(0, 0));
        assertEquals(0, TicTacToeOps.rowLineBlocking(0x15, 0x01)); // full X row, not blocking
    }

    // -----------------------------------------------------------------------
    // rotateBoard — 4 rotations return to identity
    // -----------------------------------------------------------------------

    @Test
    public void rotateBoard_fourRotationsIsIdentity() {
        int[] a = new int[]{1, 0, 2, 0, 1, 0, 2, 0, 1};
        int board = TicTacToeOps.parseArray(a);
        int r = TicTacToeOps.rotateBoard(
                TicTacToeOps.rotateBoard(
                        TicTacToeOps.rotateBoard(
                                TicTacToeOps.rotateBoard(board))));
        assertEquals(board, r);
    }

    @Test
    public void rotateBoard_xAtCorner() {
        // X at top-left (cell 0) rotated 90° CW should go to top-right (cell 2).
        int board = 0x40000000; // X at cell 0
        int rotated = TicTacToeOps.rotateBoard(board);
        assertEquals("..X-...-...-", TicTacToeOps.render(rotated));
    }

    @Test
    public void rotateBoard_xTopRow_becomesRightColumn() {
        // XXX in top row rotated 90° CW → XXX in right column.
        int[] a = new int[]{1, 1, 1, 0, 0, 0, 0, 0, 0};
        int board = TicTacToeOps.parseArray(a);
        int rotated = TicTacToeOps.rotateBoard(board);
        assertEquals("..X-..X-..X-", TicTacToeOps.render(rotated));
    }

    // -----------------------------------------------------------------------
    // hFlip — two flips return to identity
    // -----------------------------------------------------------------------

    @Test
    public void hFlip_twoFlipsIsIdentity() {
        int[] a = new int[]{1, 0, 2, 0, 1, 0, 2, 0, 1};
        int board = TicTacToeOps.parseArray(a);
        assertEquals(board, TicTacToeOps.hFlip(TicTacToeOps.hFlip(board)));
    }

    @Test
    public void hFlip_xTopRow_becomesBottomRow() {
        int[] a = new int[]{1, 1, 1, 0, 0, 0, 0, 0, 0};
        int board = TicTacToeOps.parseArray(a);
        int flipped = TicTacToeOps.hFlip(board);
        assertEquals("...-...-XXX-", TicTacToeOps.render(flipped));
    }

    // -----------------------------------------------------------------------
    // transposeBoard
    // -----------------------------------------------------------------------

    @Test
    public void transposeBoard_xTopRow_becomesLeftColumn() {
        // XXX in top row transposed → XXX in left column.
        int[] a = new int[]{1, 1, 1, 0, 0, 0, 0, 0, 0};
        int board = TicTacToeOps.parseArray(a);
        int transposed = TicTacToeOps.transposeBoard(board);
        assertEquals("X..-X..-X..-", TicTacToeOps.render(transposed));
    }

    // -----------------------------------------------------------------------
    // exchangeBoard
    // -----------------------------------------------------------------------

    @Test
    public void exchangeBoard_swapsXandO() {
        // X at cell 0, O at cell 4 — exchange → O at cell 0, X at cell 4.
        int[] a = new int[]{1, 0, 0, 0, 2, 0, 0, 0, 0};
        int board = TicTacToeOps.parseArray(a);
        int exchanged = TicTacToeOps.exchangeBoard(board);
        int[] b = new int[]{2, 0, 0, 0, 1, 0, 0, 0, 0};
        assertEquals(TicTacToeOps.parseArray(b), exchanged);
    }

    @Test
    public void exchangeBoard_twoExchangesIsIdentity() {
        int[] a = new int[]{1, 2, 0, 0, 1, 2, 0, 0, 1};
        int board = TicTacToeOps.parseArray(a);
        assertEquals(board, TicTacToeOps.exchangeBoard(TicTacToeOps.exchangeBoard(board)));
    }

    // -----------------------------------------------------------------------
    // diagonal
    // -----------------------------------------------------------------------

    @Test
    public void diagonal_emptyBoard() {
        assertEquals(0, TicTacToeOps.diagonal(0));
    }

    @Test
    public void diagonal_xMainDiagonal() {
        // X at cells 0, 4, 8 — main diagonal.
        int[] a = new int[]{1, 0, 0, 0, 1, 0, 0, 0, 1};
        int board = TicTacToeOps.parseArray(a);
        assertEquals(0x15, TicTacToeOps.diagonal(board)); // XXX
    }
}