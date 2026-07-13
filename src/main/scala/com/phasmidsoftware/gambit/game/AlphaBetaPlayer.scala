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
  * An optional key function maps a state to a cache key. When set via [[withKeyFn]],
  * `alphaBeta` looks up the key before evaluating and caches the result afterwards,
  * avoiding re-evaluation of positions reachable by multiple move orderings
  * (transpositions). The table is cleared between games via `gameOver`.
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
  *
  * @param me      the player identifier representing this player.
  * @param depth   the maximum search depth (default is 6). Determines how far
  *                ahead the algorithm looks in the game tree.
  *
  * @param ttCache the [[TTCache]] typeclass instance that handles probe/store/clear.
  *                Supply a `given FlatTTCache[K]` for shallow games or a
  *                `given TrancheTTCache[K]` for deep searches. Only consulted
  *                when a key function has been set via [[withKeyFn]].
  *
  * @param state   the typeclass providing state-specific functionalities.
  * @param game    the typeclass providing game-specific behaviours.
  */
class AlphaBetaPlayer[P, S, M, Pl, K](
                                       me: Pl,
                                       depth: Int = 6
                                     )(using state: State[P, S], game: Game[S, M, Pl], ttCache: TTCache[K])
  extends Player[S, M, Pl]:

  private val nodeCount = new java.util.concurrent.atomic.AtomicInteger(0)
  private var maxNodes: Int = Int.MaxValue
  private var keyFn: Option[S => K] = None
  private var window: AlphaBetaWindow = AlphaBetaWindow.full

  /** The best (move, score) pair evaluated so far at the top level.
    * Updated after each top-level move is fully scored.
    * Returned by `runPlayer` when a [[NodeLimitException]] is thrown mid-search.
    */
  private var bestSoFar: Option[(M, Double)] = None

  /** The worst (move, score) pair evaluated so far at the top level from the
    * protagonist's perspective — i.e. the best defensive result the antagonist
    * has achieved across fully-evaluated top-level moves.
    * Updated in the same loop as [[bestSoFar]].
    * Together with [[bestSoFar]], used after a [[NodeLimitException]] to return a
    * qualified partial result rather than [[DDResult.Inconclusive]] when one side
    * has a witness.
    */
  private var worstSoFar: Option[(M, Double)] = None

  /** All (move, score) pairs from the most recently completed iteration,
    * used to re-order moves for the next iterative-deepening iteration. */
  private var scoredMoves: Seq[(M, Double)] = Seq.empty

  /**
    * Sets the maximum number of nodes to evaluate before throwing [[NodeLimitException]].
    * Returns `this` for chaining.
    */
  def withMaxNodes(n: Int): this.type =
    maxNodes = n
    nodeCount.set(0)
    bestSoFar = None
    worstSoFar = None
    scoredMoves = Seq.empty
    this

  /**
    * Sets the transposition table key function.
    * When set, `alphaBeta` looks up the key before evaluating and caches the result
    * afterwards, avoiding re-evaluation of transposed positions.
    * Returns `this` for chaining.
    */
  def withKeyFn(f: S => K): this.type =
    keyFn = Some(f)
    this

  /**
    * Narrows the initial alpha-beta window for the root search (aspiration search).
    * Replaces the default full window `[-∞, +∞]` with the given [[AlphaBetaWindow]].
    *
    * For bridge double-dummy, where the question is purely binary ("can NS make N tricks?"),
    * `AlphaBetaWindow(-0.5, 0.5)` is ideal: any positive terminal score maps to `Some(true)`,
    * any non-positive to `Some(false)`, so the search fails fast on both sides.
    *
    * Returns `this` for chaining.
    */
  def withAspirationWindow(w: AlphaBetaWindow): this.type =
    window = w
    this

  /**
    * Returns the best (move, score) pair found so far at the top level.
    * Only meaningful after a [[NodeLimitException]] has been thrown.
    */
  def getBestSoFar: Option[(M, Double)] = bestSoFar

  /**
    * Returns the worst (move, score) pair found so far at the top level —
    * i.e. the best result achieved by the antagonist across fully-evaluated moves.
    * Only meaningful after a [[NodeLimitException]] has been thrown.
    */
  def getWorstSoFar: Option[(M, Double)] = worstSoFar

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
        Some(chooseMoveWithScoreAtDepth(s, currentPl, maximizing, depth, moves))

  /**
    * Runs iterative deepening alpha-beta search, increasing depth by `depthStep`
    * each iteration until the node limit is hit or `depth` is reached.
    *
    * At each depth, top-level moves are ordered by the scores from the previous
    * iteration (best first for the maximizing player), giving the move-ordering
    * benefit of iterative deepening. The node counter is reset at the start of each
    * iteration (so `maxNodes` is a per-depth budget, not a cumulative one) to let the
    * GC recover between depths once the previous iteration's stack has unwound.
    *
    * @param s         the root state to search from.
    * @param random    a Random instance (reserved for future use).
    * @param depthStep the depth increment per iteration (4 for bridge: trick boundaries).
    * @return the (move, score, completedDepth) of the last fully completed
    *         iteration, or `None` if not even the first iteration completed.
    */
  def chooseMoveIterativeDeepening(s: S, random: Random, depthStep: Int): Option[(M, Double, Int)] =
    if state.isGoal(s).isDefined
    then None
    else
      val moves = game.moves(s)
      if moves.isEmpty
      then None
      else
        val currentPl = game.currentPlayer(s)(using state)
        val maximizing = currentPl == me
        var lastCompleted: Option[(M, Double, Int)] = None
        var orderedMs = moves
        var currentDepth = depthStep
        var continue = true
        while continue && currentDepth <= depth do
          nodeCount.set(0)
          try
            val (bestM, bestScore) = chooseMoveWithScoreAtDepth(s, currentPl, maximizing, currentDepth, orderedMs)
            lastCompleted = Some(bestM, bestScore, currentDepth)
            orderedMs = if maximizing
            then scoredMoves.sortBy(-_._2).map(_._1)
            else scoredMoves.sortBy(_._2).map(_._1)
            logger.info(s"iterativeDeepening: completed depth=$currentDepth, best=$bestM, score=$bestScore")
            currentDepth += depthStep
          catch
            case _: NodeLimitException =>
              logger.info(s"iterativeDeepening: node limit at depth=$currentDepth, returning completedDepth=${lastCompleted.map(_._3)}")
              continue = false
        lastCompleted

  /**
    * Runs a single alpha-beta iteration at the given depth over the provided
    * (pre-ordered) top-level move list. Updates `bestSoFar`, `worstSoFar`, and
    * `scoredMoves` as side-effects.
    *
    * @param s          the root state.
    * @param currentPl  the player to move at the root.
    * @param maximizing whether `currentPl` is the maximizing player.
    * @param d          the search depth for this iteration.
    * @param ms         the pre-ordered sequence of top-level moves.
    * @return the (move, score) pair of the best move found.
    */
  private def chooseMoveWithScoreAtDepth(s: S, currentPl: Pl, maximizing: Boolean, d: Int, ms: Seq[M]): (M, Double) =
    bestSoFar = None
    worstSoFar = None
    scoredMoves = Seq.empty
    val scored = ms.map { m =>
      val result = invokeAlphaBetaAtDepth(s, currentPl, maximizing, d)(m)
      bestSoFar = Some(bestSoFar.fold(result) { current =>
        if maximizing then if result._2 > current._2 then result else current
        else if result._2 < current._2 then result else current
      })
      worstSoFar = Some(worstSoFar.fold(result) { current =>
        if maximizing then if result._2 < current._2 then result else current
        else if result._2 > current._2 then result else current
      })
      result
    }
    scoredMoves = scored
    if maximizing then scored.maxBy(_._2) else scored.minBy(_._2)

  /**
    * Handles the conclusion of a game by clearing the transposition table and
    * resetting all mutable player state ready for the next game.
    *
    * @param result the outcome of the game.
    * @param me     the player instance for whom the game is over.
    */
  override def gameOver(result: GameResult[Pl], me: Pl): Unit =
    ttCache.clear()
    nodeCount.set(0)
    bestSoFar = None
    worstSoFar = None
    scoredMoves = Seq.empty

  // ---------------------------------------------------------------------------
  // Alpha-beta search
  // ---------------------------------------------------------------------------

  /**
    * Invokes the alpha-beta pruning algorithm for a given game state and move at
    * the specified depth, returning the move with its computed minimax score.
    *
    * @param s          the current game state.
    * @param currentPl  the player making the move in the current state.
    * @param maximizing a Boolean indicating whether the current player is maximizing.
    * @param d          the search depth for this invocation.
    * @param m          the move to evaluate.
    * @return a tuple containing the move `m` and its computed minimax score.
    */
  private def invokeAlphaBetaAtDepth(s: S, currentPl: Pl, maximizing: Boolean, d: Int)(m: M): (M, Double) =
    val next = game.applyMove(s, m, currentPl)
    val nextMaximizing = state.isMaximizing(next, maximizing)
    logger.debug(s"chooseMove: move=$m, nextMaximizing=$nextMaximizing, heuristic=${state.heuristic(next)}")
    m -> searchWithAspirationRetry(next, d - 1, nextMaximizing)

  /**
    * Searches with the (possibly narrow) aspiration `window`, and re-searches with the
    * full window if that narrow search fails -- i.e. returns a value `<= window.alpha`
    * (failed low) or `>= window.beta` (failed high).
    *
    * A narrow window is only a valid substitute for a full search if every value it can
    * ever produce for a non-terminal position is guaranteed to fall strictly inside it
    * (so that only a genuinely proven result can fail it). Nothing enforces that
    * guarantee here -- `State.heuristic`/`leafValue` is caller-supplied and may
    * legitimately return values outside the window for an unproven position. Without this
    * retry, such a value would be indistinguishable from a real cutoff, and -- once the
    * transposition table is allowed to reuse `LowerBound`/`UpperBound` entries as cutoffs,
    * not just `Exact` ones -- would get cached and replayed at every later transposition
    * of that position instead of being independently re-derived each time, turning a
    * one-off heuristic overshoot into a repeated, amplified wrong answer.
    *
    * The full-window re-search still benefits from whatever the narrow search already
    * resolved: entries stored during it remain in the shared transposition table, and
    * `TTCache.probe`'s own depth/bound checks correctly refuse to reuse a narrow-window
    * cutoff that doesn't also satisfy the wider window, forcing exactly the nodes that
    * need it to be recomputed.
    */
  private def searchWithAspirationRetry(s: S, d: Int, maximizing: Boolean): Double =
    val narrow = alphaBeta(s, d, window.alpha, window.beta, maximizing)
    if window != AlphaBetaWindow.full && (narrow <= window.alpha || narrow >= window.beta)
    then alphaBeta(s, d, AlphaBetaWindow.full.alpha, AlphaBetaWindow.full.beta, maximizing)
    else narrow

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
    val n = nodeCount.incrementAndGet()
    if n > maxNodes
    then throw NodeLimitException(nodeCount.get)

    if n % 100_000 == 0 then
      val rt = Runtime.getRuntime
      val used = (rt.totalMemory - rt.freeMemory) / 1024 / 1024
      val total = rt.totalMemory / 1024 / 1024
      val max = rt.maxMemory / 1024 / 1024
      logger.info(f"nodes=$n%,d  heap used=${used}MB / total=${total}MB / max=${max}MB")

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

    new AlphaBetaPlayer[P, S, M, Pl, Any](me, depth)(using state, game, summon[TTCache[Any]])

  private val logger = LazyLogger(getClass)

/**
  * The alpha-beta window for the root search.
  *
  * @param alpha the lower bound: the maximizing player is guaranteed at least this score.
  * @param beta  the upper bound: the minimizing player is guaranteed at most this score.
  */
case class AlphaBetaWindow(alpha: Double, beta: Double)

/**
  * Object representing constants or utilities related to the `AlphaBetaWindow`.
  */
object AlphaBetaWindow:
  /** The full window: no pruning at the root. */
  val full: AlphaBetaWindow = AlphaBetaWindow(-Double.MaxValue, Double.MaxValue)


/**
  * Exception thrown when the node count limit is exceeded.
  * Caught by `analyzeDoubleDummy` to return the best result found so far.
  */
class NodeLimitException(val nodes: Int)
  extends Exception(s"Node limit reached at $nodes nodes")