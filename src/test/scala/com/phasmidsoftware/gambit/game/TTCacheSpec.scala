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

  // UpperBound: value <= alphaOrig (search failed low), so the true value <= entry.value.
  // Reusable as a cutoff whenever entry.value <= the *current* alpha; a looser current
  // alpha may no longer guarantee a cutoff, in which case we fall through to a re-search.

  it should "reuse an UpperBound entry as a cutoff when entry.value <= current alpha" in {
    val cache = FlatTTCache[Int]()
    // value=-12 <= alphaOrig=-10 => UpperBound
    cache.store(1, depth = 4, value = -12.0, alphaOrig = -10.0, beta = 10.0)
    cache.probe(1, depth = 4, alpha = -10.0, beta = 10.0) shouldBe Some(-12.0)
  }

  it should "reuse an UpperBound entry as a cutoff even against a tighter current alpha" in {
    val cache = FlatTTCache[Int]()
    cache.store(1, depth = 4, value = -12.0, alphaOrig = -10.0, beta = 10.0)
    // current alpha=-8 is tighter (larger) than alphaOrig=-10; still >= entry.value, still a cutoff
    cache.probe(1, depth = 4, alpha = -8.0, beta = 10.0) shouldBe Some(-12.0)
  }

  it should "return None for an UpperBound entry once current alpha is looser than entry.value" in {
    val cache = FlatTTCache[Int]()
    cache.store(1, depth = 4, value = -12.0, alphaOrig = -10.0, beta = 10.0)
    // current alpha=-20 is looser (smaller) than entry.value=-12: no longer a guaranteed cutoff
    cache.probe(1, depth = 4, alpha = -20.0, beta = 10.0) shouldBe None
  }

  it should "reuse an UpperBound entry as a cutoff when entry.value equals current alpha" in {
    val cache = FlatTTCache[Int]()
    cache.store(1, depth = 4, value = -10.0, alphaOrig = -10.0, beta = 10.0)
    cache.probe(1, depth = 4, alpha = -10.0, beta = 10.0) shouldBe Some(-10.0)
  }

  // ---------------------------------------------------------------------------
  // FlatTTCache — LowerBound flag
  // ---------------------------------------------------------------------------

  behavior of "FlatTTCache — LowerBound entries"

  // LowerBound: value >= beta (search failed high / beta cutoff), so the true value >= entry.value.
  // Reusable as a cutoff whenever entry.value >= the *current* beta; a looser current
  // beta may no longer guarantee a cutoff, in which case we fall through to a re-search.

  it should "reuse a LowerBound entry as a cutoff when entry.value >= current beta" in {
    val cache = FlatTTCache[Int]()
    // value=12 >= beta=10 => LowerBound
    cache.store(1, depth = 4, value = 12.0, alphaOrig = -10.0, beta = 10.0)
    cache.probe(1, depth = 4, alpha = -10.0, beta = 10.0) shouldBe Some(12.0)
  }

  it should "reuse a LowerBound entry as a cutoff even against a tighter current beta" in {
    val cache = FlatTTCache[Int]()
    cache.store(1, depth = 4, value = 12.0, alphaOrig = -10.0, beta = 10.0)
    // current beta=9 is tighter (smaller) than betaOrig=10; still <= entry.value, still a cutoff
    cache.probe(1, depth = 4, alpha = -10.0, beta = 9.0) shouldBe Some(12.0)
  }

  it should "return None for a LowerBound entry once current beta is looser than entry.value" in {
    val cache = FlatTTCache[Int]()
    cache.store(1, depth = 4, value = 12.0, alphaOrig = -10.0, beta = 10.0)
    // current beta=15 is looser (larger) than entry.value=12: no longer a guaranteed cutoff
    cache.probe(1, depth = 4, alpha = -10.0, beta = 15.0) shouldBe None
  }

  it should "reuse a LowerBound entry as a cutoff when entry.value equals current beta" in {
    val cache = FlatTTCache[Int]()
    cache.store(1, depth = 4, value = 10.0, alphaOrig = -10.0, beta = 10.0)
    cache.probe(1, depth = 4, alpha = -10.0, beta = 10.0) shouldBe Some(10.0)
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

  it should "respect maxSize by evicting the least-recently-used entry" in {
    val cache = FlatTTCache[Int](maxSize = 2)
    cache.store(1, depth = 3, value = 1.0, alphaOrig = -10.0, beta = 10.0)
    cache.store(2, depth = 3, value = 2.0, alphaOrig = -10.0, beta = 10.0)
    cache.store(3, depth = 3, value = 3.0, alphaOrig = -10.0, beta = 10.0) // evicts key 1 (least recently touched)
    cache.size shouldBe 2
    cache.probe(1, depth = 3, alpha = -10.0, beta = 10.0) shouldBe None // evicted
    cache.probe(2, depth = 3, alpha = -10.0, beta = 10.0) shouldBe Some(2.0) // retained
    cache.probe(3, depth = 3, alpha = -10.0, beta = 10.0) shouldBe Some(3.0) // newly stored, retained
  }

  it should "treat a probe as a touch, protecting the entry from LRU eviction" in {
    val cache = FlatTTCache[Int](maxSize = 2)
    cache.store(1, depth = 3, value = 1.0, alphaOrig = -10.0, beta = 10.0)
    cache.store(2, depth = 3, value = 2.0, alphaOrig = -10.0, beta = 10.0)
    cache.probe(1, depth = 3, alpha = -10.0, beta = 10.0) // touches key 1, making key 2 the LRU entry
    cache.store(3, depth = 3, value = 3.0, alphaOrig = -10.0, beta = 10.0) // evicts key 2, not key 1
    cache.probe(1, depth = 3, alpha = -10.0, beta = 10.0) shouldBe Some(1.0) // protected by the earlier probe
    cache.probe(2, depth = 3, alpha = -10.0, beta = 10.0) shouldBe None // evicted
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

  it should "reuse a LowerBound entry as a cutoff at exact depth" in {
    val cache = TrancheTTCache[Int]()
    cache.store(1, depth = 4, value = 12.0, alphaOrig = -10.0, beta = 10.0)
    cache.probe(1, depth = 4, alpha = -10.0, beta = 10.0) shouldBe Some(12.0)
  }

  it should "reuse an UpperBound entry as a cutoff at exact depth" in {
    val cache = TrancheTTCache[Int]()
    cache.store(1, depth = 4, value = -12.0, alphaOrig = -10.0, beta = 10.0)
    cache.probe(1, depth = 4, alpha = -10.0, beta = 10.0) shouldBe Some(-12.0)
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

  it should "assign UpperBound when value equals alphaOrig, reusable while current alpha >= entry.value" in {
    val cache = FlatTTCache[String]()
    // value(-5) <= alphaOrig(-5) => UpperBound
    cache.store("x", depth = 3, value = -5.0, alphaOrig = -5.0, beta = 5.0)
    cache.probe("x", depth = 3, alpha = -5.0, beta = 5.0) shouldBe Some(-5.0)
    cache.probe("x", depth = 3, alpha = -4.0, beta = 5.0) shouldBe Some(-5.0)
    cache.probe("x", depth = 3, alpha = -6.0, beta = 5.0) shouldBe None // alpha looser than entry.value
  }

  it should "assign LowerBound when value equals beta, reusable while current beta <= entry.value" in {
    val cache = FlatTTCache[String]()
    // value(5) >= beta(5) => LowerBound
    cache.store("x", depth = 3, value = 5.0, alphaOrig = -5.0, beta = 5.0)
    cache.probe("x", depth = 3, alpha = -5.0, beta = 5.0) shouldBe Some(5.0)
    cache.probe("x", depth = 3, alpha = -5.0, beta = 6.0) shouldBe None // beta looser than entry.value
  }
}