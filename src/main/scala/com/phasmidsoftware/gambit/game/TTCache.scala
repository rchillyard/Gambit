package com.phasmidsoftware.gambit.game

import scala.collection.mutable

/**
  * Transposition table flag indicating the type of a cached alpha-beta value.
  *
  * When alpha-beta search returns a value `v` for a node with window `[alphaOrig, beta]`:
  *  - `Exact`      — `alphaOrig < v < beta`: the value is exact; reuse freely.
  *  - `UpperBound` — `v <= alphaOrig`: search failed low; `v` is an upper bound on the true value.
  *  - `LowerBound` — `v >= beta`:      search failed high; `v` is a lower bound on the true value.
  */
enum TTFlag {
  case Exact, LowerBound, UpperBound
}

/**
  * A single entry in the transposition table, recording the cached minimax value,
  * the depth at which it was computed, and the bound type.
  */
case class TTEntry(value: Double, depth: Int, flag: TTFlag)

/**
  * Typeclass for a transposition table used by [[AlphaBetaPlayer]].
  *
  * Implementations are responsible for:
  *  - Storing a result together with the alpha-beta bounds under which it was computed
  *    (so that a [[TTFlag]] can be derived and checked on re-use).
  *  - Probing the table and returning a usable value only when the cached entry's flag
  *    is compatible with the current alpha-beta window.
  *  - Clearing all cached data between games.
  *
  * == Probe semantics ==
  *
  * Given a cached [[TTEntry]] with sufficient depth:
  *  - `Exact`      → return `entry.value` directly.
  *  - `LowerBound` → tighten alpha: if the updated alpha >= beta, return `entry.value` (cut-off);
  *    otherwise return `None` (continue searching with tightened alpha — not
  *    possible to propagate here, so we return None and let the caller re-search).
  *  - `UpperBound` → tighten beta:  if alpha >= the updated beta,  return `entry.value` (cut-off);
  *    otherwise return `None`.
  *
  * @tparam K the type of the transposition table key.
  */
trait TTCache[K]:
  /**
    * Look up `key` in the table.  Returns a usable value if the cached entry exists,
    * was computed at sufficient depth, and its flag is compatible with `[alpha, beta]`.
    * Returns `None` on a miss or an incompatible hit.
    */
  def probe(key: K, depth: Int, alpha: Double, beta: Double): Option[Double]

  /**
    * Store a result in the table.
    *
    * @param key       the transposition table key for the state.
    * @param depth     the search depth at which `value` was computed.
    * @param value     the minimax value returned for this node.
    * @param alphaOrig the value of alpha on *entry* to this node (before any updates).
    * @param beta      the beta bound in effect for this node.
    */
  def store(key: K, depth: Int, value: Double, alphaOrig: Double, beta: Double): Unit

  /** Clear all cached entries (called between games). */
  def clear(): Unit

// ---------------------------------------------------------------------------
// Concrete implementations
// ---------------------------------------------------------------------------

/**
  * A flat (single-HashMap) transposition table.
  *
  * Suitable for shallow games (e.g. TicTacToe, Connect Four) where results
  * computed at a greater-or-equal depth are valid to reuse at the current depth.
  *
  * @param maxSize maximum number of entries; writes are suppressed once the table
  *                reaches this size.  Default is unlimited.
  * @tparam K the key type.
  */
class FlatTTCache[K](maxSize: Int = Int.MaxValue) extends TTCache[K]:

  private val table: mutable.HashMap[K, TTEntry] = mutable.HashMap.empty

  def probe(key: K, depth: Int, alpha: Double, beta: Double): Option[Double] =
    table.get(key).flatMap { entry =>
      if entry.depth < depth then None
      else entry.flag match
        case TTFlag.Exact => Some(entry.value)
        case TTFlag.LowerBound if entry.value >= beta => Some(entry.value)
        case TTFlag.UpperBound if entry.value <= alpha => Some(entry.value)
        case _ => None // bound doesn't cross the current window: fall through to a full re-search
    }

  def store(key: K, depth: Int, value: Double, alphaOrig: Double, beta: Double): Unit =
    if table.size < maxSize then
      val flag =
        if value <= alphaOrig then TTFlag.UpperBound
        else if value >= beta then TTFlag.LowerBound
        else TTFlag.Exact
      table(key) = TTEntry(value, depth, flag)

  def clear(): Unit = table.clear()

  def size: Int = table.size

/**
  * A depth-tranche transposition table.
  *
  * Maintains a separate `HashMap[K, TTEntry]` per search depth.  A hit requires
  * an exact depth match (or, if `reuseDeeper` is true, also accepts entries from
  * deeper tranches).  Benchmarked ~25% faster than [[FlatTTCache]] for deep searches
  * such as bridge double-dummy (~52 plies).
  *
  * @param reuseDeeper if true, a miss at the current depth also checks deeper tranches.
  *                    Default false.
  * @param maxSize     total entry cap across all tranches.  Default unlimited.
  * @tparam K the key type.
  */
class TrancheTTCache[K](reuseDeeper: Boolean = false, maxSize: Int = Int.MaxValue) extends TTCache[K]:

  private val table: mutable.HashMap[Int, mutable.HashMap[K, TTEntry]] = mutable.HashMap.empty

  private def totalSize: Int = table.values.map(_.size).sum

  def probe(key: K, depth: Int, alpha: Double, beta: Double): Option[Double] =
    val candidate: Option[TTEntry] =
      table.get(depth).flatMap(_.get(key))
        .orElse(
          if reuseDeeper then
            table.keys.filter(_ > depth)
              .flatMap(d => table(d).get(key))
              .headOption
          else None
        )
    candidate.flatMap { entry =>
      entry.flag match
        case TTFlag.Exact => Some(entry.value)
        case TTFlag.LowerBound if entry.value >= beta => Some(entry.value)
        case TTFlag.UpperBound if entry.value <= alpha => Some(entry.value)
        case _ => None // bound doesn't cross the current window: fall through to a full re-search
    }

  def store(key: K, depth: Int, value: Double, alphaOrig: Double, beta: Double): Unit =
    if totalSize < maxSize then
      val flag =
        if value <= alphaOrig then TTFlag.UpperBound
        else if value >= beta then TTFlag.LowerBound
        else TTFlag.Exact
      val inner = table.getOrElseUpdate(depth, mutable.HashMap.empty)
      inner(key) = TTEntry(value, depth, flag)

  def clear(): Unit = table.clear()

  def size: Int = totalSize