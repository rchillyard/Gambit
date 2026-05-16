package com.phasmidsoftware.gambit.game

import scala.collection.mutable
import scala.util.boundary.break
import scala.util.{Random, boundary}

/**
  * AlphaBetaPlayer provides an implementation of an AI player for a turn-based game
  * using the alpha-beta pruning algorithm. This player evaluates the game tree up to a
  * given depth to determine optimal moves, minimizing computation by pruning branches
  * that cannot influence the final result.
  *
  * The class includes an optional transposition table for caching evaluation results,
  * which improves efficiency by reusing previously computed states. Depending on the
  * configuration, this table can use either a flat mapping or a depth-tranche structure.
  *
  * @param me            the player instance represented by this AlphaBetaPlayer.
  * @param depth         the maximum search depth for the alpha-beta pruning algorithm.
  * @param keyFn         optional function to compute a unique hashable key for each game state;
  *                      used for caching in the transposition table.
  *
  * @param reuseDeeper   flag indicating whether cached results from deeper depths
  *                      should be reused in the transposition table.
  *
  * @param depthTranches flag indicating whether to organize the transposition table
  *                      into depth-specific tranches.
  *
  * @param state         an implicit State instance defining the rules of the game and
  *                      heuristic evaluation for states.
  *
  * @param game          an implicit Game instance that describes valid moves,
  *                      their application to states, and other game logic.
  */
class AlphaBetaPlayer[P, S, M, Pl](
                                    me: Pl,
                                    depth: Int = 6,
                                    keyFn: Option[S => Any] = None,
                                    reuseDeeper: Boolean = false,
                                    depthTranches: Boolean = false
                                  )(using state: State[P, S], game: Game[S, M, Pl])
  extends Player[S, M, Pl]:

  override def gameOver(result: GameResult[Pl], me: Pl): Unit =
    flatTable.clear()
    trancheTable.clear()

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
    if depthTranches then
      trancheTable.values.map(_.size).sum
    else
      flatTable.size

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

    def depthBasedLookup(key: Any, cached: Option[Double]) = cached match
      case Some(v) => v
      case None =>
        val result = evaluate
        trancheTable.getOrElseUpdate(depth, mutable.HashMap.empty)(key) = result
        if tableSize % 10000 == 0 then
          logger.debug(s"alphaBeta: tableSize=$tableSize, depth=$depth")
        result

    keyFn match
      case None =>
        evaluate
      case Some(f) =>
        val key = f(s)
        if !depthTranches then
          // Option 1: flat table, reuse any result cached at >= current depth
          flatLookup(depth, evaluate, key)
        else {
          // Options 2 & 3: depth-tranche table
          val cached = trancheTable.get(depth).flatMap(_.get(key))
            .orElse(if reuseDeeper then
              trancheTable.keys.filter(_ > depth).flatMap(d => trancheTable(d).get(key)).headOption
            else None)
          depthBasedLookup(key, cached)
        }

  private def flatLookup(depth: Int, evaluated: => Double, key: Any) = {
    flatTable.get(key) match
      case Some((cached, cachedDepth)) if cachedDepth >= depth =>
        cached
      case _ =>
        val result = evaluated
        flatTable(key) = (result, depth)
        if flatTable.size % 10000 == 0 then
          logger.debug(s"alphaBeta: tableSize=${flatTable.size}, depth=$depth")
        result
  }

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

  /**
    * Transposition table: either a flat table mapping keys to (value, depth),
    * or a depth-tranche table mapping depth to a sub-table of keys to values.
    * Controlled by the `depthTranches` constructor parameter.
    */
  private val flatTable: mutable.HashMap[Any, (Double, Int)] = mutable.HashMap.empty
  private val trancheTable: mutable.HashMap[Int, mutable.HashMap[Any, Double]] = mutable.HashMap.empty

  private val logger = org.slf4j.LoggerFactory.getLogger(getClass)