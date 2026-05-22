package com.phasmidsoftware.gambit.game

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TTCacheSpec extends AnyFlatSpec with Matchers {

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /** Store a single entry and return the cache for chaining. */
  private def storeOne[K](cache: TTCache[K], key: K, depth: Int, value: Double, alphaOrig: Double, beta: Double): TTCache[K] =
    cache.store(key, depth, value, alphaOrig, beta)
    cache

  // ---------------------------------------------------------------------------
  // FlatTTCache — miss cases
  // ---------------------------------------------------------------------------

  behavior of "FlatTTCache — misses"

  it should "return None on an empty table" in {
    val cache = FlatTTCache[Int]()
    cache.probe(42, depth = 4, alpha = -10.0, beta = 10.0) shouldBe None
  }

  it should "return None when cached depth is less than required depth" in {
    val cache = FlatTTCache[String]()
    // Store at depth 2 with an Exact value
    cache.store("key", depth = 2, value = 5.0, alphaOrig = -10.0, beta = 10.0)
    // Probe at depth 4 — shallower entry should not be reused
    cache.probe("key", depth = 4, alpha = -10.0, beta = 10.0) shouldBe None
  }

  it should "return None after clear()" in {
    val cache = FlatTTCache[String]()
    cache.store("key", depth = 3, value = 7.0, alphaOrig = -10.0, beta = 10.0)
    cache.clear()
    cache.probe("key", depth = 3, alpha = -10.0, beta = 10.0) shouldBe None
  }

  // ---------------------------------------------------------------------------
  // FlatTTCache — Exact flag
  // ---------------------------------------------------------------------------

  behavior of "FlatTTCache — Exact entries"

  it should "store an Exact entry when value is strictly inside the window" in {
    val cache = FlatTTCache[Int]()
    // alphaOrig=-10, beta=10, value=5 => Exact
    cache.store(1, depth = 3, value = 5.0, alphaOrig = -10.0, beta = 10.0)
    cache.probe(1, depth = 3, alpha = -10.0, beta = 10.0) shouldBe Some(5.0)
  }

  it should "reuse an Exact entry cached at greater depth" in {
    val cache = FlatTTCache[Int]()
    cache.store(1, depth = 6, value = 3.0, alphaOrig = -10.0, beta = 10.0)
    cache.probe(1, depth = 4, alpha = -10.0, beta = 10.0) shouldBe Some(3.0)
  }

  it should "return an Exact entry regardless of the current alpha/beta window" in {
    val cache = FlatTTCache[Int]()
    cache.store(1, depth = 4, value = 2.0, alphaOrig = -10.0, beta = 10.0)
    // Probe with a different window — Exact is always reusable
    cache.probe(1, depth = 4, alpha = 0.0, beta = 5.0) shouldBe Some(2.0)
  }

  // ---------------------------------------------------------------------------
  // FlatTTCache — UpperBound flag
  // ---------------------------------------------------------------------------

  behavior of "FlatTTCache — UpperBound entries"

  // UpperBound: value <= alphaOrig (search failed low)
  // True value <= entry.value.  Reuse only if entry.value <= alpha (still a fail-low).

  it should "store an UpperBound entry when value <= alphaOrig" in {
    val cache = FlatTTCache[Int]()
    // value=-12 <= alphaOrig=-10 => UpperBound; Exact-only probe returns None
    cache.store(1, depth = 4, value = -12.0, alphaOrig = -10.0, beta = 10.0)
    cache.probe(1, depth = 4, alpha = -10.0, beta = 10.0) shouldBe None
  }

  it should "return None for UpperBound (Exact-only: all non-Exact entries are misses)" in {
    val cache = FlatTTCache[Int]()
    // value=-12 <= alphaOrig=-10 => UpperBound; Exact-only probe always returns None
    cache.store(1, depth = 4, value = -12.0, alphaOrig = -10.0, beta = 10.0)
    cache.probe(1, depth = 4, alpha = -8.0, beta = 10.0) shouldBe None
  }

  it should "return None for UpperBound entry even when value equals alpha" in {
    val cache = FlatTTCache[Int]()
    // Exact-only: UpperBound entries are never reused
    cache.store(1, depth = 4, value = -10.0, alphaOrig = -10.0, beta = 10.0)
    cache.probe(1, depth = 4, alpha = -10.0, beta = 10.0) shouldBe None
  }

  // ---------------------------------------------------------------------------
  // FlatTTCache — LowerBound flag
  // ---------------------------------------------------------------------------

  behavior of "FlatTTCache — LowerBound entries"

  // LowerBound: value >= beta (search failed high / beta cutoff)
  // True value >= entry.value.  Reuse only if entry.value >= beta (still a fail-high).

  it should "store a LowerBound entry when value >= beta" in {
    val cache = FlatTTCache[Int]()
    // value=12 >= beta=10 => LowerBound; Exact-only probe returns None
    cache.store(1, depth = 4, value = 12.0, alphaOrig = -10.0, beta = 10.0)
    cache.probe(1, depth = 4, alpha = -10.0, beta = 10.0) shouldBe None
  }

  it should "return None for LowerBound (Exact-only: all non-Exact entries are misses)" in {
    val cache = FlatTTCache[Int]()
    // value=12 >= beta=10 => LowerBound; Exact-only probe always returns None
    cache.store(1, depth = 4, value = 12.0, alphaOrig = -10.0, beta = 10.0)
    cache.probe(1, depth = 4, alpha = -10.0, beta = 15.0) shouldBe None
  }

  it should "return None for LowerBound entry even when value equals beta" in {
    val cache = FlatTTCache[Int]()
    // Exact-only: LowerBound entries are never reused
    cache.store(1, depth = 4, value = 10.0, alphaOrig = -10.0, beta = 10.0)
    cache.probe(1, depth = 4, alpha = -10.0, beta = 10.0) shouldBe None
  }

  // ---------------------------------------------------------------------------
  // FlatTTCache — size and overwrite
  // ---------------------------------------------------------------------------

  behavior of "FlatTTCache — size"

  it should "report correct size" in {
    val cache = FlatTTCache[Int]()
    cache.size shouldBe 0
    cache.store(1, depth = 3, value = 1.0, alphaOrig = -10.0, beta = 10.0)
    cache.size shouldBe 1
    cache.store(2, depth = 3, value = 2.0, alphaOrig = -10.0, beta = 10.0)
    cache.size shouldBe 2
    cache.store(1, depth = 5, value = 3.0, alphaOrig = -10.0, beta = 10.0) // overwrite key 1
    cache.size shouldBe 2
  }

  it should "respect maxSize and suppress writes beyond limit" in {
    val cache = FlatTTCache[Int](maxSize = 2)
    cache.store(1, depth = 3, value = 1.0, alphaOrig = -10.0, beta = 10.0)
    cache.store(2, depth = 3, value = 2.0, alphaOrig = -10.0, beta = 10.0)
    cache.store(3, depth = 3, value = 3.0, alphaOrig = -10.0, beta = 10.0) // suppressed
    cache.size shouldBe 2
    cache.probe(3, depth = 3, alpha = -10.0, beta = 10.0) shouldBe None
  }

  // ---------------------------------------------------------------------------
  // TrancheTTCache — miss cases
  // ---------------------------------------------------------------------------

  behavior of "TrancheTTCache — misses"

  it should "return None on an empty table" in {
    val cache = TrancheTTCache[Int]()
    cache.probe(1, depth = 4, alpha = -10.0, beta = 10.0) shouldBe None
  }

  it should "return None when entry exists at a different depth (reuseDeeper=false)" in {
    val cache = TrancheTTCache[Int]()
    cache.store(1, depth = 6, value = 5.0, alphaOrig = -10.0, beta = 10.0)
    cache.probe(1, depth = 4, alpha = -10.0, beta = 10.0) shouldBe None
  }

  it should "return None after clear()" in {
    val cache = TrancheTTCache[Int]()
    cache.store(1, depth = 4, value = 5.0, alphaOrig = -10.0, beta = 10.0)
    cache.clear()
    cache.probe(1, depth = 4, alpha = -10.0, beta = 10.0) shouldBe None
  }

  // ---------------------------------------------------------------------------
  // TrancheTTCache — exact depth match
  // ---------------------------------------------------------------------------

  behavior of "TrancheTTCache — exact depth match"

  it should "hit on exact depth match for Exact entry" in {
    val cache = TrancheTTCache[Int]()
    cache.store(1, depth = 4, value = 5.0, alphaOrig = -10.0, beta = 10.0)
    cache.probe(1, depth = 4, alpha = -10.0, beta = 10.0) shouldBe Some(5.0)
  }

  it should "return None for LowerBound entry at exact depth (Exact-only)" in {
    val cache = TrancheTTCache[Int]()
    cache.store(1, depth = 4, value = 12.0, alphaOrig = -10.0, beta = 10.0)
    cache.probe(1, depth = 4, alpha = -10.0, beta = 10.0) shouldBe None
  }

  it should "return None for UpperBound entry at exact depth (Exact-only)" in {
    val cache = TrancheTTCache[Int]()
    cache.store(1, depth = 4, value = -12.0, alphaOrig = -10.0, beta = 10.0)
    cache.probe(1, depth = 4, alpha = -10.0, beta = 10.0) shouldBe None
  }

  // ---------------------------------------------------------------------------
  // TrancheTTCache — reuseDeeper
  // ---------------------------------------------------------------------------

  behavior of "TrancheTTCache — reuseDeeper"

  it should "find an entry from a deeper tranche when reuseDeeper=true" in {
    val cache = TrancheTTCache[Int](reuseDeeper = true)
    cache.store(1, depth = 6, value = 5.0, alphaOrig = -10.0, beta = 10.0)
    // probe at depth 4 — shallower than stored depth 6, reuseDeeper allows it
    cache.probe(1, depth = 4, alpha = -10.0, beta = 10.0) shouldBe Some(5.0)
  }

  it should "not find a deeper tranche entry when reuseDeeper=false" in {
    val cache = TrancheTTCache[Int](reuseDeeper = false)
    cache.store(1, depth = 6, value = 5.0, alphaOrig = -10.0, beta = 10.0)
    cache.probe(1, depth = 4, alpha = -10.0, beta = 10.0) shouldBe None
  }

  it should "not find a shallower tranche entry even when reuseDeeper=true" in {
    val cache = TrancheTTCache[Int](reuseDeeper = true)
    cache.store(1, depth = 3, value = 5.0, alphaOrig = -10.0, beta = 10.0)
    // stored at depth 3, probing at depth 4 — depth 3 is not deeper than 4
    cache.probe(1, depth = 4, alpha = -10.0, beta = 10.0) shouldBe None
  }

  // ---------------------------------------------------------------------------
  // TrancheTTCache — size
  // ---------------------------------------------------------------------------

  behavior of "TrancheTTCache — size"

  it should "report correct total size across tranches" in {
    val cache = TrancheTTCache[Int]()
    cache.size shouldBe 0
    cache.store(1, depth = 3, value = 1.0, alphaOrig = -10.0, beta = 10.0)
    cache.store(2, depth = 4, value = 2.0, alphaOrig = -10.0, beta = 10.0)
    cache.store(3, depth = 4, value = 3.0, alphaOrig = -10.0, beta = 10.0)
    cache.size shouldBe 3
  }

  it should "respect maxSize across all tranches" in {
    val cache = TrancheTTCache[Int](maxSize = 2)
    cache.store(1, depth = 3, value = 1.0, alphaOrig = -10.0, beta = 10.0)
    cache.store(2, depth = 4, value = 2.0, alphaOrig = -10.0, beta = 10.0)
    cache.store(3, depth = 5, value = 3.0, alphaOrig = -10.0, beta = 10.0) // suppressed
    cache.size shouldBe 2
  }

  // ---------------------------------------------------------------------------
  // Flag assignment — verify store logic directly via probe
  // ---------------------------------------------------------------------------

  behavior of "TTFlag assignment"

  it should "assign Exact when value is strictly between alphaOrig and beta" in {
    val cache = FlatTTCache[String]()
    cache.store("x", depth = 3, value = 0.0, alphaOrig = -5.0, beta = 5.0)
    // Exact entries are always returned regardless of probe window
    cache.probe("x", depth = 3, alpha = -100.0, beta = 100.0) shouldBe Some(0.0)
  }

  it should "assign UpperBound when value equals alphaOrig (Exact-only: all probes return None)" in {
    val cache = FlatTTCache[String]()
    // value(-5) <= alphaOrig(-5) => UpperBound; stored but never returned by probe
    cache.store("x", depth = 3, value = -5.0, alphaOrig = -5.0, beta = 5.0)
    cache.probe("x", depth = 3, alpha = -5.0, beta = 5.0) shouldBe None
    cache.probe("x", depth = 3, alpha = -4.0, beta = 5.0) shouldBe None
    cache.probe("x", depth = 3, alpha = -6.0, beta = 5.0) shouldBe None
  }

  it should "assign LowerBound when value equals beta (Exact-only: all probes return None)" in {
    val cache = FlatTTCache[String]()
    // value(5) >= beta(5) => LowerBound; stored but never returned by probe
    cache.store("x", depth = 3, value = 5.0, alphaOrig = -5.0, beta = 5.0)
    cache.probe("x", depth = 3, alpha = -5.0, beta = 5.0) shouldBe None
    cache.probe("x", depth = 3, alpha = -5.0, beta = 6.0) shouldBe None
  }
}