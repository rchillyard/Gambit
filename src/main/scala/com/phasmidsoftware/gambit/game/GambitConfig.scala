package com.phasmidsoftware.gambit.game

import com.typesafe.config.{Config, ConfigFactory}

/**
  * Loads and exposes typed configuration values for the Gambit framework.
  *
  * Configuration is read from `application.conf` on the classpath (standard
  * Typesafe Config behaviour). All values have hard-coded fallbacks so the
  * application runs correctly even if no config file is present.
  *
  * == HOCON structure ==
  * {{{
  * gambit {
  *   tournament.gamesPerPairing = 6
  *   alphaBeta.depth1           = 4
  *   alphaBeta.depth2           = 6
  *   mcts.iterations1           = 200
  *   mcts.iterations2           = 500
  *   mcts.explorationConstant   = 1.4142135623730951
  * }
  * }}}
  */
object GambitConfig:

  private val config: Config = ConfigFactory.load().getConfig("gambit")

  /** Default number of games per pairing in a tournament. */
  val tournamentGamesPerPairing: Int = config.getInt("tournament.gamesPerPairing")

  /** Depth for the shallower AlphaBeta contestant. */
  val alphaBetaDepth1: Int = config.getInt("alphaBeta.depth1")

  /** Depth for the deeper AlphaBeta contestant. */
  val alphaBetaDepth2: Int = config.getInt("alphaBeta.depth2")

  /** Iteration count for the lighter MCTS contestant. */
  val mctsIterations1: Int = config.getInt("mcts.iterations1")

  /** Iteration count for the heavier MCTS contestant. */
  val mctsIterations2: Int = config.getInt("mcts.iterations2")

  /** UCB1 exploration constant C for MCTS (default √2). */
  val mctsExplorationConstant: Double = config.getDouble("mcts.explorationConstant")