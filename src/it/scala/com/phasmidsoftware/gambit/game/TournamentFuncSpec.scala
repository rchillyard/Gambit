package com.phasmidsoftware.gambit.game

import com.phasmidsoftware.gambit.examples.connect4.Connect4
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should
import org.scalatest.tagobjects.Slow

class TournamentFuncSpec extends AnyFlatSpec with should.Matchers {

  behavior of "Tournament competitive ordering"

  it should "produce a complete Connect4Tournament table without throwing" taggedAs Slow in {
    import com.phasmidsoftware.gambit.examples.connect4.Connect4Tournament
    noException should be thrownBy Connect4Tournament.run(gamesPerPairing = 2)
  }
}