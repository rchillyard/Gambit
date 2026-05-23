package com.phasmidsoftware.gambit.game

import com.phasmidsoftware.gambit.game.AlphaBetaPlayer.logger
import com.phasmidsoftware.gambit.util.LazyLogger

import scala.util.boundary.break
import scala.util.{Random, boundary}

/**
  * A generic alpha-beta pruning player.
  * For more information, see [[https://en.wikipedia.org/wiki/Alpha-beta_pruning]]
  *
  * Implements minimax search with alpha-beta pruning to a configurable depth.
  * Alpha-beta pruning eliminates branches that cannot affect the final decision,
  * reducing the effective branching factor from b to approximately sqrt(b) in the
  * best case -- effectively doubling the achievable search depth for the same
  * computation compared to plain minimax.
  *
  * == Heuristic Convention ==
  *
  * `State.heuristic(s)` is positive when the player who just moved to reach `s`
  * is doing well. This is consistent with the convention used throughout Gambit.
  * At leaf nodes (terminal or depth limit reached), the raw heuristic is returned.
  * The maximizing/minimizing logic correctly accounts for this convention.
  *
  * == Move Ordering ==
  *
  * Moves are ordered by heuristic before recursing -- highest first for the
  * maximizing player, lowest first for the minimizing player. This shallow
  * search ordering maximises the probability of early pruning, approaching
  * the theoretical best-case performance of alpha-beta.
  *
  * == Mutable State ==
  *
  * Alpha and beta bounds are tracked with mutable variables, and `boundary`/`break`
  * is used for early termination on a prune. This is intentional: alpha-beta
  * pruning is fundamentally about early exit based on accumulated bounds, and
  * fighting that with purely functional constructs would sacrifice both clarity
  * and performance. Mutable state is appropriate here for the same reason it is
  * appropriate in high-performance sorting algorithms.
  *
  * == Transposition Table ==
  *
  * An optional `keyFn` maps a state to a cache key. When provided, `alphaBeta`
  * looks up the key before evaluating and caches the result afterwards, avoiding
  * re-evaluation of positions reachable by multiple move orderings (transpositions).
  * The table is cleared between games via `gameOver`.
  *
  * Caching behaviour is controlled by the `given TTCache[K]` in scope:
  *
  * - `FlatTTCache[K]`   — single `HashMap[K, TTEntry]`; reuses entries cached at equal or
  *   greater depth. Suitable for shallow games (TicTacToe, Connect Four).
  *
  * - `TrancheTTCache[K]` — separate sub-table per depth; benchmarked ~25% faster for deep
  *   searches (bridge double-dummy, ~52 plies).
  *
  * Both implementations store [[TTFlag]] (Exact / LowerBound / UpperBound) with each entry
  * and only reuse entries whose flag is compatible with the current alpha-beta window,
  * preventing cache poisoning from incompatible bounds.
  *
  * @tparam P  The type representing a proto-state, which helps transition between game states.
  * @tparam S  The type representing the game state.
  * @tparam M  The type representing the move in the game.
  * @tparam Pl The type representing the players in the game.
  * @tparam K  The type of the transposition table key. Use `Any` when no caching is needed
  *            (via the companion `apply`); otherwise a compact type such as `Int`,
  *            `(Long, Long)`, or a case class that has correct `equals`/`hashCode`.
  * @param me            the player identifier representing this player.
  * @param depth         the maximum search depth (default is 6). Determines how far
  *                      ahead the algorithm looks in the game tree.
  * @param keyFn         an optional function mapping a state to a transposition table key.
  *                      When `None` (default) no memoization is performed.
  * @param ttCache       the [[TTCache]] typeclass instance that handles probe/store/clear.
  *                      Supply a `given FlatTTCache[K]` for shallow games or a
  *                      `given TrancheTTCache[K]` for deep searches.  Only consulted
  *                      when `keyFn` is defined.
  * @param state         the typeclass providing state-specific functionalities.
  * @param game          the typeclass providing game-specific behaviours.
  */
class AlphaBetaPlayer[P, S, M, Pl, K](
                                       me: Pl,
                                       depth: Int = 6,
                                       keyFn: Option[S => K] = None
                                     )(using state: State[P, S], game: Game[S, M, Pl], ttCache: TTCache[K])
  extends Player[S, M, Pl]:

  /**
    * Returns the total number of entries currently held in the transposition table.
    * Returns 0 when `keyFn` is `None` (no caching).
    *
    * NOTE: do not make this private.
    *
    * @return the total number of elements in the cache.
    */
  def tableSize: Int = keyFn match
    case None => 0
    case Some(_) => ttCache match
      case c: FlatTTCache[K] => c.size
      case c: TrancheTTCache[K] => c.size
      case _ => 0

  /**
    * Selects the best move for the current player in the specified game state using the
    * alpha-beta pruning algorithm. Returns `None` if the state is already terminal or
    * there are no available moves.
    *
    * NOTE: has side-effect of logging (DEBUG).
    *
    * @param s      the current game state.
    * @param random an instance of Random (reserved for future randomisation).
    * @return an Option containing the selected move if a valid move exists, otherwise None.
    */
  override def chooseMove(s: S, random: Random): Option[M] =
    chooseMoveWithScore(s, random).map(_._1)

  /**
    * Selects the best move and returns it together with its minimax score.
    * Returns `None` if the state is already terminal or there are no available moves.
    *
    * The score is from the perspective of `me`: positive means `me` is doing well.
    * Callers can use the score directly to determine win/loss without re-evaluating
    * the heuristic on the resulting state (which would only be a shallow estimate).
    *
    * NOTE: has side-effect of logging (DEBUG).
    *
    * @param s      the current game state.
    * @param random an instance of Random (reserved for future randomisation).
    * @return an Option containing the (move, score) pair if a valid move exists, otherwise None.
    */
  def chooseMoveWithScore(s: S, random: Random): Option[(M, Double)] =
    if state.isGoal(s).isDefined
    then None
    else
      val moves = game.moves(s)
      if moves.isEmpty
      then None
      else
        val currentPl = game.currentPlayer(s)(using state)
        val maximizing = currentPl == me
        logger.debug(s"chooseMove: currentPl=$currentPl, me=$me, maximizing=$maximizing")
        val scored = moves.map(invokeAlphaBeta(s, currentPl, maximizing))
        val best = if maximizing then scored.maxBy(_._2) else scored.minBy(_._2)
        logger.debug(s"chooseMove: best=${best._1}, score=${best._2}")
        Some(best._1, best._2)

  /**
    * Handles the conclusion of a game by clearing both transposition tables,
    * resetting the player's memoization state ready for the next game.
    *
    * @param result the outcome of the game.
    * @param me     the player instance for whom the game is over.
    */
  override def gameOver(result: GameResult[Pl], me: Pl): Unit =
    ttCache.clear()

  // ---------------------------------------------------------------------------
  // Alpha-beta search
  // ---------------------------------------------------------------------------

  /**
    * Invokes the alpha-beta pruning algorithm for a given game state and move, returning the move
    * with its computed minimax score. This helper function evaluates the score of a successor
    * state resulting from the applied move, logging diagnostic information about the state,
    * heuristic value, and whether the next player is maximizing or minimizing.
    *
    * @param s          the current game state.
    * @param currentPl  the player making the move in the current state.
    * @param maximizing a Boolean indicating whether the current player is maximizing.
    * @param m          the move to evaluate.
    * @return a tuple containing the move `m` and its computed minimax score.
    */
  private def invokeAlphaBeta(s: S, currentPl: Pl, maximizing: Boolean)(m: M): (M, Double) = {
    val next = game.applyMove(s, m, currentPl)
    val nextMaximizing = state.isMaximizing(next, maximizing)
    logger.debug(s"chooseMove: move=$m, nextMaximizing=$nextMaximizing, heuristic=${state.heuristic(next)}")
    m -> alphaBeta(next, depth - 1, -Double.MaxValue, Double.MaxValue, nextMaximizing)
  }

  /**
    * Recursive alpha-beta search.
    *
    * Returns the minimax value of state `s` from the perspective of `me`,
    * searching to the given depth with alpha-beta pruning.
    * When `keyFn` is defined, checks and updates the transposition table.
    *
    * @param s          the state to evaluate.
    * @param depth      remaining search depth (0 = evaluate immediately).
    * @param alpha      best score the maximizing player is guaranteed so far.
    * @param beta       best score the minimizing player is guaranteed so far.
    * @param maximizing true if the current player is maximizing (i.e. is `me`).
    * @return the minimax value of `s`.
    */
  private def alphaBeta(s: S, depth: Int, alpha: Double, beta: Double, maximizing: Boolean): Double =
    lazy val leafValue: Double = state.leafValue(s, maximizing)

    def evaluate: Double =
      state.isGoal(s) match
        case Some(b) =>
          logger.debug(s"alphaBeta: terminal: win: $b, maximizing=$maximizing, leafValue=$leafValue, heuristic=${state.heuristic(s)}")
          leafValue
        case None if depth == 0 =>
          leafValue
        case None =>
          val currentPl = game.currentPlayer(s)(using state)
          val moves = orderedMoves(s, currentPl, maximizing)
          if maximizing
          then maximizingSearch(moves, depth - 1, alpha, beta)
          else minimizingSearch(moves, depth - 1, alpha, beta)

    cachedEvaluate(s, depth, alpha, beta, evaluate)

  /**
    * Maximizing half of alpha-beta: iterates over successor states, updating
    * the alpha bound and pruning when alpha >= beta.
    *
    * @param moves the ordered sequence of (move, successor-state) pairs.
    * @param depth remaining search depth to pass to recursive calls.
    * @param alpha current alpha bound.
    * @param beta  current beta bound.
    * @return the best (highest) score found for the maximizing player.
    */
  private def maximizingSearch(moves: Seq[(M, S)], depth: Int, alpha: Double, beta: Double): Double =
    var a = alpha
    var best = -Double.MaxValue
    boundary:
      moves.foreach { (_, next) =>
        best = best.max(alphaBeta(next, depth, a, beta, state.isMaximizing(next, true)))
        a = a.max(best)
        if a >= beta then break(best) // prune: minimizer won't allow this
      }
    best

  /**
    * Minimizing half of alpha-beta: iterates over successor states, updating
    * the beta bound and pruning when alpha >= beta.
    *
    * @param moves the ordered sequence of (move, successor-state) pairs.
    * @param depth remaining search depth to pass to recursive calls.
    * @param alpha current alpha bound.
    * @param beta  current beta bound.
    * @return the best (lowest) score found for the minimizing player.
    */
  private def minimizingSearch(moves: Seq[(M, S)], depth: Int, alpha: Double, beta: Double): Double =
    var b = beta
    var best = Double.MaxValue
    boundary:
      moves.foreach { (_, next) =>
        best = best.min(alphaBeta(next, depth, alpha, b, state.isMaximizing(next, false)))
        b = b.min(best)
        if alpha >= b then break(best) // prune: maximizer won't allow this
      }
    best

  /**
    * Wraps `evaluate` with transposition-table lookup and store.
    * The `evaluate` argument is by-name so it is only called on a cache miss.
    *
    * When `keyFn` is `None`, evaluates directly with no caching.
    * Otherwise delegates to the `given TTCache[K]` for probe/store.
    *
    * @param s     the state being evaluated.
    * @param depth the current search depth.
    * @param alpha the alpha bound in effect on entry to this node.
    * @param beta  the beta bound in effect on entry to this node.
    * @param evaluate the evaluation thunk, called only on a cache miss.
    * @return the cached or freshly computed minimax value.
    */
  private def cachedEvaluate(s: S, depth: Int, alpha: Double, beta: Double, evaluate: => Double): Double =
    keyFn match
      case None =>
        evaluate
      case Some(f) =>
        val key = f(s)
        ttCache.probe(key, depth, alpha, beta) match
          case Some(cached) =>
            cached
          case None =>
            val result = evaluate
            ttCache.store(key, depth, result, alpha, beta)
            result

  /**
    * Generates and orders successor (move, state) pairs for move ordering.
    * Maximizing player gets successors sorted highest-heuristic first;
    * minimizing player gets lowest-heuristic first.
    */
  private def orderedMoves(s: S, currentPl: Pl, maximizing: Boolean): Seq[(M, S)] =
    val successors = game.moves(s).map(m => m -> game.applyMove(s, m, currentPl))
    if maximizing
    then successors.sortBy((_, next) => -state.heuristic(next))
    else successors.sortBy((_, next) => state.heuristic(next))

/**
  * Companion object for `AlphaBetaPlayer`.
  *
  * Provides a convenience `apply` for the common case where no transposition
  * table is needed. The key type is erased to `Any`; callers that want a typed
  * key should use `new AlphaBetaPlayer[P, S, M, Pl, K](...)` directly.
  */
object AlphaBetaPlayer:

  /**
    * Creates an `AlphaBetaPlayer` with no transposition table.
    *
    * A `FlatTTCache[Any]` is provided implicitly but never consulted since
    * `keyFn` defaults to `None`.
    *
    * @tparam P  proto-state type.
    * @tparam S  state type.
    * @tparam M  move type.
    * @tparam Pl player-identity type.
    * @param me    the player identifier for this player.
    * @param depth maximum search depth (default 6).
    * @param state implicit `State[P, S]` typeclass instance.
    * @param game  implicit `Game[S, M, Pl]` typeclass instance.
    * @return a new `AlphaBetaPlayer` with no caching.
    */
  def apply[P, S, M, Pl](me: Pl, depth: Int = 6)(using state: State[P, S], game: Game[S, M, Pl]): AlphaBetaPlayer[P, S, M, Pl, Any] =
    given TTCache[Any] = FlatTTCache[Any]()

    new AlphaBetaPlayer[P, S, M, Pl, Any](me, depth, None)(using state, game, summon[TTCache[Any]])

  private val logger = LazyLogger(getClass)
