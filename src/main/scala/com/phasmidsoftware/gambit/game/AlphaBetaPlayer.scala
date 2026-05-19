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
  * Three caching modes are available, controlled by `depthTranches` and `reuseDeeper`:
  *
  * - '''Flat table''' (`depthTranches = false`, default): a single `HashMap[Key, (Double, Int)]`
  *   where the `Int` is the depth at which the result was cached. A cached entry is reused
  *   if it was computed at least as deep as the current search. Works well for shallow games
  *   (e.g. TicTacToe) where cross-depth reuse is beneficial.
  *
  * - '''Depth-tranche exact''' (`depthTranches = true, reuseDeeper = false`): separate sub-tables
  *   per depth. Only exact-depth matches are reused. Works best for deep searches (e.g. bridge
  *   double-dummy, ~52 plies) where a result cached at a shallower depth is not valid for a
  *   deeper search. Benchmarked ~25% faster than flat for bridge.
  *
  * - '''Depth-tranche reuse''' (`depthTranches = true, reuseDeeper = true`): as above but also
  *   scans deeper tranches on a miss. Tends to be slowest in practice due to scan overhead
  *   outweighing the benefit of cross-depth reuse.
  *
  * Recommendation: use flat for shallow games, depth-tranche exact for deep searches.
  *
  * @tparam P  The type representing a proto-state, which helps transition between game states.
  * @tparam S  The type representing the game state.
  * @tparam M  The type representing the move in the game.
  * @tparam Pl The type representing the players in the game.
  * @tparam K  The type representing the key for the transposition table entries in the game.
  * @constructor Creates a new AlphaBetaPlayer instance.
  * @param me            the player identifier representing this player.
  * @param depth         the maximum search depth (default is 6). Determines how far
  *                      ahead the algorithm looks in the game tree.
  *
  * @param keyFn         an optional function to generate unique keys for transposition
  *                      table entries based on game states.
  *
  * @param depthTranches a flag to indicate if separate tables should be maintained
  *                      for different depths (default is false).
  *
  * @param reuseDeeper   Whether to reuse cached evaluations from deeper depths during
  *                      evaluation. Only valid if `depthTranches` is true. Default is false.
  *
  * @param maxTableSize  The maximum size of the transposition table. Default is Int.MaxValue.
  * @param state         The typeclass providing state-specific functionalities.
  * @param game          The typeclass providing game-specific behaviors.
  */
class AlphaBetaPlayer[P, S, M, Pl, K](
                                       me: Pl,
                                       depth: Int = 6,
                                       keyFn: Option[S => K] = None,
                                       depthTranches: Boolean = false,
                                       reuseDeeper: Boolean = false,
                                       maxTableSize: Int = Int.MaxValue
                                     )(using state: State[P, S], game: Game[S, M, Pl])
  extends Player[S, M, Pl]:

  /**
    * Calculates the total number of elements in the tables managed by the player.
    * If `depthTranches` is true, the sizes of all the tranche tables are summed up.
    * Otherwise, the size of the flat table is returned.
    *
    * NOTE: do not make this private.
    *
    * @return the total number of elements in the tables.
    */
  def tableSize: Int =
    if depthTranches
    then trancheTable.values.map(_.size).sum
    else flatTable.size

  /**
    * Selects the best move for the current player in the specified game state using the Alpha-Beta pruning algorithm.
    * If the state corresponds to a goal or there are no available moves, no move is chosen.
    *
    * NOTE: side-effect of logging (DEBUG)
    *
    * @param s      the current game state.
    * @param random an instance of Random, used for potential randomization (if needed).
    * @return an Option containing the selected move if a valid move exists, otherwise None.
    */
  override def chooseMove(s: S, random: Random): Option[M] =
    if state.isGoal(s).isDefined
    then None
    else
      val moves = game.moves(s)
      if moves.isEmpty then None
      else
        val currentPl = game.currentPlayer(s)(using state)
        val maximizing = currentPl == me
        val scored = moves.map { m =>
          val next = game.applyMove(s, m, currentPl)
          logger.debug(s"chooseMove: move=$m, heuristic=${state.heuristic(next)}")
          m -> alphaBeta(next, depth - 1, -Double.MaxValue, Double.MaxValue, !maximizing)
        }
        val best = if maximizing then scored.maxBy(_._2) else scored.minBy(_._2)
        logger.debug(s"chooseMove: best=${best._1}, score=${best._2}")
        Some(best._1)

  /**
    * Handles the conclusion of a game by clearing the internal tables
    * (flatTable and trancheTable) to reset the player's state.
    *
    * @param result the outcome of the game, represented as a `GameResult`
    *               that associates players with their respective scores or results.
    * @param me     the player instance representing the current player
    *               for whom the gameOver method is invoked.
    * @return Unit as this method performs a side-effect and does not return a value.
    */
  override def gameOver(result: GameResult[Pl], me: Pl): Unit =
    flatTable.clear()
    trancheTable.clear()

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

    keyFn match
      case None =>
        evaluate
      case Some(f) =>
        val key = f(s)
        if !depthTranches then
          // Flat table mode
          flatTable.get(key) match
            case Some((cached, cachedDepth)) if cachedDepth >= depth => cached
            case _ =>
              val result = evaluate
              if tableSize < maxTableSize then
                flatTable(key) = (result, depth)
                if flatTable.size % 10000 == 0 then
                  logger.debug(s"alphaBeta: tableSize=${flatTable.size}, depth=$depth")
              result
        else
          // Depth-tranche mode
          val cached = trancheTable.get(depth).flatMap(_.get(key))
            .orElse(if reuseDeeper then
              trancheTable.keys.filter(_ > depth).flatMap(d => trancheTable(d).get(key)).headOption
            else None)
          cached match
            case Some(v) => v
            case None =>
              val result = evaluate
              if tableSize < maxTableSize then
                trancheTable.getOrElseUpdate(depth, mutable.HashMap.empty)(key) = result
                if tableSize % 10000 == 0 then
                  logger.debug(s"alphaBeta: tableSize=$tableSize, depth=$depth")
              result

  /**
    * Generate and order successor (move, state) pairs for move ordering.
    */
  private def orderedMoves(s: S, currentPl: Pl, maximizing: Boolean): Seq[(M, S)] =
    val successors = game.moves(s).map { m =>
      m -> game.applyMove(s, m, currentPl)
    }
    if maximizing then successors.sortBy((_, next) => -state.heuristic(next))
    else successors.sortBy((_, next) => state.heuristic(next))

  private val flatTable: mutable.HashMap[K, (Double, Int)] = mutable.HashMap.empty
  private val trancheTable: mutable.HashMap[Int, mutable.HashMap[K, Double]] = mutable.HashMap.empty

  private val logger = org.slf4j.LoggerFactory.getLogger(getClass)

/**
  * A singleton object that provides a way to create an instance of `AlphaBetaPlayer`.
  * The `AlphaBetaPlayer` is a game-playing agent that uses the Alpha-Beta pruning algorithm
  * to efficiently evaluate and select moves in a game environment. This is typically used in
  * games involving heuristic tree searches, such as chess or tic-tac-toe.
  *
  * The `apply` method allows for the construction of an `AlphaBetaPlayer` with customizable
  * parameters.
  */
object AlphaBetaPlayer:
  /**
    * Applies the Alpha-Beta search algorithm to create an `AlphaBetaPlayer` for decision-making
    * in a game scenario. This method constructs a player object tailored to evaluate and choose
    * optimal moves using the provided state, game, and depth parameters.
    *
    * @tparam P  The type representing a proto-state, which helps transition between game states.
    * @tparam S  The type representing the game state.
    * @tparam M  The type representing the move in the game.
    * @tparam Pl The type representing the players in the game.
    * @param me    the player instance being used for the game.
    * @param depth the maximum search depth for the Alpha-Beta search algorithm. Defaults to 6.
    * @param state an implicit parameter describing the state of the game, including rules for transitions
    *              and heuristic evaluations.
    *
    * @param game  an implicit parameter representing the game mechanics, including valid moves and turn alternations.
    * @return an `AlphaBetaPlayer` initialized with the given player, depth, and supporting game logic.
    */
  def apply[P, S, M, Pl](me: Pl, depth: Int = 6)(using state: State[P, S], game: Game[S, M, Pl]): AlphaBetaPlayer[P, S, M, Pl, Any] =
    new AlphaBetaPlayer[P, S, M, Pl, Any](me, depth, None)(using state, game)
