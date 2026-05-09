import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

class PriorityQueueSpec extends AnyFlatSpec with should.Matchers {

    behavior of "PriorityQueue"

    it should "headOption" in {
        PriorityQueue[Int].headOption should matchPattern { case None => }
        PriorityQueue[Int](1).headOption should matchPattern { case Some(1) => }
    }

    it should "insert1" in {
        PriorityQueue[Int].insert(1) shouldBe PriorityQueue[Int](1)
    }

    it should "insert2" in {
        val pq12 = PriorityQueue[Int].insert(1).insert(2)
        pq12 shouldBe PriorityQueue[Int](Seq(1, 2))
        val pq1 = pq12.del
        pq1 should matchPattern { case (_, 1) => }
        pq1._1.del should matchPattern { case (_, 2) => }
    }

    it should "iterator" in {
        val iterator = PriorityQueue[Int](1).iterator
        iterator.hasNext shouldBe true
        iterator.next() shouldBe 1
        iterator.hasNext shouldBe false
    }

    it should "iterator2" in {
        val iterator = PriorityQueue[Int](Seq(1, 2)).iterator
        iterator.hasNext shouldBe true
        iterator.next() shouldBe 1
        iterator.hasNext shouldBe true
        iterator.next() shouldBe 2
        iterator.hasNext shouldBe false
    }

    it should "head" in {
        PriorityQueue[Int](1).insert(2).head shouldBe 1
    }

    it should "del0" in {
        val empty = PriorityQueue.apply[Int]
        a[PriorityQueueException] should be thrownBy empty.del
    }

    it should "del1" in {
        val (pq, x) = PriorityQueue(1).insert(2).del
        x shouldBe 1
        pq shouldBe PriorityQueue(2)
    }

    it should "apply1" in {
        PriorityQueue[Int].isEmpty shouldBe true
    }

    it should "apply2" in {
        PriorityQueue(1).isEmpty shouldBe false
    }

    it should "apply3" in {
        val pq = PriorityQueue(Seq(1, 2, 3))
        pq.isEmpty shouldBe false
        pq.del should matchPattern { case (_, 1) => }
    }

    it should "apply4" in {
        PriorityQueue(new PriorityQueueJava[Int]()).isEmpty shouldBe true
    }

    it should "maxPQ1" in {
        PriorityQueue.maxPQ[Int].insert(1).insert(2).head shouldBe 2
    }

    it should "maxPQ2" in {
        PriorityQueue.maxPQ[Int](2).insert(1).head shouldBe 2
    }

}
