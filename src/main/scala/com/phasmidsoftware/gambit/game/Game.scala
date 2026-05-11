package com.phasmidsoftware.gambit.game

import com.phasmidsoftware.gambit.game.GameResult

/**
  * Typeclass: describes the rules of a game in terms of state S, move M,
  * and player identity Pl.
  *
  * A Game instance captures everything the GameRunner needs to know about
  * the mechanics of a specific game — how moves are applied, whose turn it
  * is, where the game starts, and how many players are involved.
  *
  * This separates game rules (Game[S, M, Pl]) from game strategy (Player[S, M, Pl])
  * and game execution (GameRunner[P, S, M, Pl]).
  *
  * @tparam S  the state type.
  * @tparam M  the move type.
  * @tparam Pl the player identity type (e.g., Boolean for two-player,
  *            an enum for bridge's four seats).
  */
trait Game[S, M, Pl]:
  /**
    * The starting state of the game.
    */
  def start: S

  /**
    * The player who moves first.
    */
  def startingPlayer: Pl

  /**
    * The ordered sequence of all player identities.
    * Used by GameRunner to initialise the players map and notify all players
    * of the game result.
    */
  def players: Seq[Pl]

  /**
    * The player whose turn it is to make the NEXT move from state `s`.
    *
    * To be completely unambiguous: if the game is in state `s`, and we are
    * about to call `applyMove`, the player returned by `currentPlayer(s)` is
    * the one who will make that move. It is NOT the player who made the last
    * move to reach `s` — that player has already moved and is waiting.
    *
    * Example (`TicTacToe`, `Pl = Boolean`):
    * empty board → currentPlayer = true  (X moves first)
    * after X plays → currentPlayer = false (O moves next)
    * after O plays → currentPlayer = true  (X moves next)
    *
    * Default implementation for two-player games: returns `startingPlayer`
    * when `state.isFirstPlayerToMove(s)` is true, otherwise the other player.
    * Override for games with more than two players.
    *
    * @param s     the current state.
    * @param state the implicit State[P, S] used to determine move parity.
    * @return the identity of the player who is about to move.
    */
  def currentPlayer[P](s: S)(using state: State[P, S]): Pl =
    if state.isFirstPlayerToMove(s) then startingPlayer
    else players.find(_ != startingPlayer).getOrElse(startingPlayer)

  /**
    * The legal moves available from state `s`.
    * Used by MCTS and other players that need raw move values rather than
    * State transitions.
    *
    * @param s the current state.
    * @return a sequence of legal moves.
    */
  def moves(s: S): Seq[M]

  /**
    * Apply a move made by player `pl` to state `s`, returning the new state.
    *
    * @param s  the current state.
    * @param m  the move to apply.
    * @param pl the player making the move.
    * @return the new state after the move.
    */
  def applyMove(s: S, m: M, pl: Pl): S

  /**
    * Given the current state and the player who just moved, return the
    * identity of the next player to move.
    *
    * @param s       the state after the move was applied.
    * @param current the player who just moved.
    * @return the next player.
    */
  def nextPlayer(s: S, current: Pl): Pl

  /**
    * Determine the winner from a terminal state.
    * Called by GameRunner when State.isGoal returns Some(true).
    * The `current` parameter is the player who is *about to move* —
    * i.e., the player who did NOT make the winning move.
    *
    * Default implementation for zero-sum two-player games: the player
    * who is NOT `current` wins (+1), `current` loses (-1).
    * Override for multiplayer or non-zero-sum games.
    *
    * @param s       the terminal state.
    * @param current the player whose turn it would have been next.
    * @return a GameResult mapping each player to their score.
    */
  def winner(s: S, current: Pl): GameResult[Pl] =
    val prev = players.find(_ != current).getOrElse(current)
    players.map { pl =>
      pl -> (if pl == prev then 1 else if pl == current then -1 else 0)
    }.toMap