package com.phasmidsoftware.util

import scala.annotation.tailrec

/**
 * This code is a solution to a LeetCode problem:
 * https://leetcode.com/discuss/interview-question/1526418/Count-strength-of-pa%20%20ssword-or-amazon
 */
object PasswordStrength extends App {

    @tailrec
    def substrings(r: Set[String], ws: List[String]): Set[String] = ws match {
        case Nil => r
        case w :: t =>
            val f: (Int, Int) => String = w.substring
            val g = f.tupled
            val xs = for (i <- 0 until w.length; t <- List((0, i), (i, i+1), (i+1, w.length))) yield t
            val _ws = t ++ (xs map g filterNot (s => s == w || s == ""))
            substrings(r + w, _ws)
    }

    def apply(w: String): Int = substrings(Set(), List(w)).foldLeft(0)((a, w) => a + w.distinct.size)

    println(PasswordStrength("good"))
}

