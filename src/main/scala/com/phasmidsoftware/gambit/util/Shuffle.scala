package com.phasmidsoftware.gambit.util

import java.util
import java.util.Random
import scala.collection.mutable.ListBuffer

object Shuffle {

  def apply[X](xs: Iterable[X], seed: Long = System.currentTimeMillis()): Seq[X] = {
    import scala.jdk.CollectionConverters.*
    val listBuffer: ListBuffer[X] = new ListBuffer().appendAll(xs)
    val jList: util.List[X] = listBuffer.asJava
    java.util.Collections.shuffle(jList, new Random(seed))
    jList.asScala.toSeq
  }
}
