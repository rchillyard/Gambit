package com.phasmidsoftware.gambit.util

import scala.annotation.tailrec

object Aggregators {

  def hasTwo[X](xs: Seq[X])(p: X => Boolean): Option[(X, X)] = filterN(Nil, xs)(2, p) match {
    case x1 :: x2 :: _ => Some((x1, x2))
    case _ => None
  }

  def hasOne[X](xs: Seq[X])(p: X => Boolean): Option[X] = filterN(Nil, xs)(1, p) match {
    case x :: _ => Some(x)
    case _ => None
  }

  @tailrec
  def filterN[X](r: Seq[X], w: Seq[X])(n: Int, p: X => Boolean): Seq[X] = w match {
    case _ if r.size == n => r
    case Nil => r
    case h :: t => filterN(if (p(h)) r :+ h else r, t)(n, p)
  }

}
