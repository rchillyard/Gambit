package com.phasmidsoftware.gambit.examples.connect4

import com.phasmidsoftware.gambit.examples.connect4.Connect4State.given
import com.phasmidsoftware.gambit.game.{AlphaBetaPlayer, Contestant, MCTSPlayer, Tournament}
import scala.util.Random

/**
  * Runs a round-robin tournament between all Connect Four player types
  * and prints the league table to stdout.
  *
  * Players entered:
  *   - RandomPlayer
  *   - HeuristicPlayer  (one-ply greedy)
  *   - AlphaBeta(d=4)   (minimax, depth 4)
  *   - AlphaBeta(d=6)   (minimax, depth 6)
  *   - MCTS(i=200)      (Monte Carlo, 200 iterations)
  *   - MCTS(i=500)      (Monte Carlo, 500 iterations)
  */
object Connect4Tournament:

  def main(args: Array[String]): Unit =
    val gamesPerPairing = args.headOption.flatMap(_.toIntOption).getOrElse(6)
    println(run(gamesPerPairing = gamesPerPairing))

  /**
    * Build and run the tournament, returning the league table as a String.
    *
    * @param gamesPerPairing number of games each ordered pair plays (default 6).
    * @param seed            random seed for reproducibility (default 42).
    * @return the formatted league table.
    */
  def run(gamesPerPairing: Int = 6, seed: Long = 42L): String =
    val rng = new Random(seed)

    val contestants = Seq(
      Contestant("Random",         new RandomPlayer),
      Contestant("Heuristic",      new HeuristicPlayer),
      Contestant("AlphaBeta(d=4)", AlphaBetaPlayer[Connect4, Connect4, Int, Boolean](me = true, depth = 4)),
      Contestant("AlphaBeta(d=6)", AlphaBetaPlayer[Connect4, Connect4, Int, Boolean](me = true, depth = 6)),
      Contestant("MCTS(i=200)",    MCTSPlayer[Connect4, Connect4, Int, Boolean](me = true, iterations = 200)),
      Contestant("MCTS(i=500)",    MCTSPlayer[Connect4, Connect4, Int, Boolean](me = true, iterations = 500)),
    )

    val tournament = Tournament[Connect4, Connect4, Int, Boolean](
      contestants     = contestants,
      gamesPerPairing = gamesPerPairing,
      random          = rng
    )

    s"Connect Four Tournament ($gamesPerPairing games per pairing)\n\n${tournament.leagueTable}"