package com.phasmidsoftware.gambit.game

import scala.collection.mutable
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
  * The key type is `Any` so callers can supply any suitable key without Gambit
  * needing to know the concrete type. A typical Bridge usage:
  * {{{
  *   case class StateKey(whist: Whist, trick: Trick, tricks: Tricks)
  *   AlphaBetaPlayer(..., keyFn = Some(s => StateKey(s.whist, s.trick, s.tricks)))
  * }}}
  *
  * @param me    this player's identity.
  * @param depth search depth in plies (half-moves). Default 6.
  *              Higher values give stronger play at the cost of
  *              exponentially more computation.
  * @param keyFn optional function mapping a state to a transposition table key.
  *              When `None` (default) no memoization is performed.
  * @param state implicit State[P, S] for goal detection and heuristic.
  * @param game  implicit Game[S, M, Pl] for move generation and application.
  * @tparam P  the proto-state type.
  * @tparam S  the state type.
  * @tparam M  the move type.
  * @tparam Pl the player identity type.
  */
class AlphaBetaPlayer[P, S, M, Pl](
                                    me: Pl,
                                    depth: Int = 6,
                                    keyFn: Option[S => Any] = None
                                  )(using state: State[P, S], game: Game[S, M, Pl])
  extends Player[S, M, Pl]:

  /**
    * Transposition table: maps state keys to (cachedValue, depthAtWhichCached).
    * A cached entry is only used if it was computed at least as deep as the
    * current search depth, preventing shallow cached values from corrupting
    * deeper searches.
    * Only used when `keyFn` is defined. Cleared in `gameOver`.
    */
  private val table: mutable.HashMap[Any, (Double, Int)] = mutable.HashMap.empty

  override def chooseMove(s: S, random: Random): Option[M] =
    if state.isGoal(s).isDefined then None
    else
      val moves = game.moves(s)
      if moves.isEmpty then None
      else
        val currentPl = game.currentPlayer(s)(using state)
        val maximizing = currentPl == me
        val scored = moves.map { m =>
          val next = game.applyMove(s, m, currentPl)
          m -> alphaBeta(next, depth - 1, -Double.MaxValue, Double.MaxValue, !maximizing)
        }
        val best = if maximizing then scored.maxBy(_._2) else scored.minBy(_._2)
        Some(best._1)

  override def gameOver(result: GameResult[Pl], me: Pl): Unit =
    table.clear()

  // ---------------------------------------------------------------------------
  // Alpha-beta search
  // ---------------------------------------------------------------------------

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
    // heuristic(s) is from the perspective of the player who just moved INTO s.
    // alphaBeta returns a value from me's perspective (positive = good for me).
    // When the maximizing player (me) just moved: heuristic sign is correct.
    // When the minimizing player just moved: heuristic must be negated.
    def leafValue: Double = if maximizing then -state.heuristic(s) else state.heuristic(s)

    def evaluate: Double =
      state.isGoal(s) match
        case Some(_) => leafValue
        case None if depth == 0 => leafValue
        case None =>
          val currentPl = game.currentPlayer(s)(using state)
          val moves = orderedMoves(s, currentPl, maximizing)
          if maximizing then
            var a = alpha
            var best = -Double.MaxValue
            boundary:
              moves.foreach { (m, next) =>
                best = best.max(alphaBeta(next, depth - 1, a, beta, false))
                a = a.max(best)
                if a >= beta then break(best) // prune: minimizer won't allow this
              }
            best
          else
            var b = beta
            var best = Double.MaxValue
            boundary:
              moves.foreach { (m, next) =>
                best = best.min(alphaBeta(next, depth - 1, alpha, b, true))
                b = b.min(best)
                if alpha >= b then break(best) // prune: maximizer won't allow this
              }
            best

    // Transposition table lookup and update.
    keyFn match
      case None => evaluate
      case Some(f) =>
        val key = f(s)
        table.get(key) match
          case Some((cached, cachedDepth)) if cachedDepth >= depth => cached
          case _ =>
            val result = evaluate
            table(key) = (result, depth)
            result

  /**
    * Generate and order successor (move, state) pairs for move ordering.
    *
    * For the maximizing player, high-heuristic successors are tried first --
    * they are most likely to raise alpha quickly and trigger pruning.
    * For the minimizing player, low-heuristic successors are tried first.
    *
    * @param s          the current state.
    * @param currentPl  the player to move.
    * @param maximizing true if the current player is maximizing.
    * @return ordered sequence of (move, successor state) pairs.
    */
  private def orderedMoves(s: S, currentPl: Pl, maximizing: Boolean): Seq[(M, S)] =
    val successors = game.moves(s).map { m =>
      m -> game.applyMove(s, m, currentPl)
    }
    if maximizing then successors.sortBy((_, next) => -state.heuristic(next))
    else successors.sortBy((_, next) => state.heuristic(next))