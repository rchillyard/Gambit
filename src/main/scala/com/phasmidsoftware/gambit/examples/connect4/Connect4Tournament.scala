package com.phasmidsoftware.gambit.examples.connect4

import com.phasmidsoftware.gambit.examples.connect4.Connect4State.given
import com.phasmidsoftware.gambit.game.{AlphaBetaPlayer, Contestant, GambitConfig, MCTSPlayer, Tournament}

import scala.util.Random

/**
  * Runs a round-robin tournament between all Connect Four player types
  * and prints the league table to stdout.
  *
  * Player parameters (depths, iteration counts, exploration constant) are
  * read from `application.conf` via [[com.phasmidsoftware.gambit.game.GambitConfig]].
  * Edit the config file to tune contestants without recompiling.
  *
  * Players entered:
  *   - RandomPlayer
  *   - HeuristicPlayer  (one-ply greedy)
  *   - AlphaBeta(d=d1)  (minimax, depth from config)
  *   - AlphaBeta(d=d2)  (minimax, deeper depth from config)
  *   - MCTS(i=i1)       (Monte Carlo, iteration count from config)
  *   - MCTS(i=i2)       (Monte Carlo, higher iteration count from config)
  */
object Connect4Tournament:

  /**
    * The entry point for the Connect Four tournament application.
    * Command-line args override config defaults.
    *
    * @param args optional: args(0) = gamesPerPairing (Int), args(1) = seed (Long).
    */
  def main(args: Array[String]): Unit =
    val gamesPerPairing = args.headOption.flatMap(_.toIntOption).getOrElse(GambitConfig.tournamentGamesPerPairing)
    val seed = args.lift(1).flatMap(_.toLongOption).getOrElse(System.currentTimeMillis())
    println(run(gamesPerPairing = gamesPerPairing, seed = seed))

  /**
    * Build and run the tournament, returning the league table as a String.
    *
    * Player parameters are read from [[GambitConfig]] (`application.conf`).
    * Override by editing the config file -- no recompilation needed.
    *
    * @param gamesPerPairing number of games each ordered pair plays
    *                        (default from config: `gambit.tournament.gamesPerPairing`).
    *
    * @param seed            random seed; default is `System.currentTimeMillis()`.
    * @return the formatted league table.
    */
  def run(
           gamesPerPairing: Int = GambitConfig.tournamentGamesPerPairing,
           seed: Long = System.currentTimeMillis()
         ): String =
    val announcement = s"Connect Four Tournament ($gamesPerPairing games per pairing)"
    val marquee = "*" * announcement.length
    println(marquee)
    println(announcement)
    println(marquee)

    val rng = new Random(seed)
    val d1 = GambitConfig.alphaBetaDepth1
    val d2 = GambitConfig.alphaBetaDepth2
    val i1 = GambitConfig.mctsIterations1
    val i2 = GambitConfig.mctsIterations2
    val c = GambitConfig.mctsExplorationConstant

    println(s"depth1=$d1, depth2=$d2, iterations1=$i1, iterations2=$i2, explorationConstant=$c")

    val contestants = Seq(
      Contestant("Random", new RandomPlayer),
      Contestant("Heuristic", new HeuristicPlayer),
      Contestant(s"AlphaBeta(d=$d1)", AlphaBetaPlayer[Connect4, Connect4, Int, Boolean](me = true, depth = d1)),
      Contestant(s"AlphaBeta(d=$d2)", AlphaBetaPlayer[Connect4, Connect4, Int, Boolean](me = true, depth = d2)),
      Contestant(s"MCTS(i=$i1)", MCTSPlayer[Connect4, Connect4, Int, Boolean](me = true, iterations = i1, explorationConstant = c)),
      Contestant(s"MCTS(i=$i2)", MCTSPlayer[Connect4, Connect4, Int, Boolean](me = true, iterations = i2, explorationConstant = c)),
    )

    println(s"Contestants: ${contestants.map(_.name).mkString(", ")}")
    System.out.flush()

    val tournament = Tournament[Connect4, Connect4, Int, Boolean](
      contestants = contestants,
      gamesPerPairing = gamesPerPairing,
      random = rng
    )

    s"\n${tournament.leagueTable}"