package com.phasmidsoftware.decisiontree.game

import scala.collection.mutable
import scala.util.Random

/**
  * A node in the MCTS search tree.
  *
  * Mutable: visits and wins are updated in-place during backpropagation.
  * Children are added during expansion.
  * untriedMoves shrinks as children are expanded.
  *
  * No parent reference — backpropagation uses an explicit path stack
  * accumulated during selection, avoiding circular references and the
  * GC/equality issues that back-references cause in tree structures.
  *
  * @param state        the game state at this node.
  * @param move         the move that led to this state (None for root).
  * @param movedBy      the player who made `move` (None for root).
  * @param visits       number of times this node has been visited.
  * @param wins         cumulative score from simulations through this node,
  *                     from the perspective of `movedBy`.
  *
  * @param children     expanded child nodes.
  * @param untriedMoves moves not yet expanded into children.
  * @tparam S  the state type.
  * @tparam M  the move type.
  * @tparam Pl the player identity type.
  */
class MCTSNode[S, M, Pl](
                          val state: S,
                          val move: Option[M],
                          val movedBy: Option[Pl],
                          var visits: Int,
                          var wins: Double,
                          val children: mutable.ListBuffer[MCTSNode[S, M, Pl]],
                          var untriedMoves: List[M]
                        ):
  def isFullyExpanded: Boolean = untriedMoves.isEmpty

  def isLeaf: Boolean = children.isEmpty

  override def toString: String =
    s"MCTSNode(move=$move, movedBy=$movedBy, visits=$visits, wins=$wins, " +
      s"children=${children.size}, untried=${untriedMoves.size})"

object MCTSNode:
  def root[S, M, Pl](state: S, moves: List[M]): MCTSNode[S, M, Pl] =
    new MCTSNode(state, None, None, 0, 0.0, mutable.ListBuffer.empty, moves)

  def child[S, M, Pl](state: S, move: M, movedBy: Pl, moves: List[M]): MCTSNode[S, M, Pl] =
    new MCTSNode(state, Some(move), Some(movedBy), 0, 0.0, mutable.ListBuffer.empty, moves)

/**
  * A generic Monte Carlo Tree Search player.
  *
  * Implements the standard four-phase MCTS loop:
  *   1. Selection   — walk the tree by UCB1 until an unexpanded node is found.
  *   2. Expansion   — add one new child for an untried move.
  *   3. Simulation  — play randomly to a terminal state (rollout).
  *   4. Backprop    — update visit/win counts along the path to root.
  *
  * The search tree is rebuilt from scratch on each call to `chooseMove`.
  *
  * == Future upgrades ==
  *
  * - **Tree reuse**: retain the subtree rooted at the chosen move between
  *   calls, avoiding redundant re-exploration of already-visited states.
  *
  * - **Actor-based parallelism**: move the mutable tree into an Akka/Pekko
  *   actor. Multiple rollout worker actors could then submit simulation
  *   results to the tree actor concurrently (root parallelization), giving
  *   a near-linear speedup with the number of cores.
  *
  * - **Heuristic rollouts**: replace pure random simulation with a
  *   heuristic-guided playout for stronger play.
  *
  * @param me                  this player's identity.
  * @param iterations          number of MCTS iterations per move (default 1000).
  * @param explorationConstant UCB1 exploration parameter C (default √2).
  * @param state               implicit State[P, S] for goal detection.
  * @param game                implicit Game[S, M, Pl] for move application.
  * @tparam P  the proto-state type.
  * @tparam S  the state type.
  * @tparam M  the move type.
  * @tparam Pl the player identity type.
  */
class MCTSPlayer[P, S, M, Pl](
                               me: Pl,
                               iterations: Int = 1000,
                               explorationConstant: Double = math.sqrt(2)
                             )(using state: State[P, S], game: Game[S, M, Pl])
  extends Player[S, M, Pl]:

  override def chooseMove(s: S, random: Random): Option[M] =
    if state.isGoal(s).isDefined then None
    else
      val root = MCTSNode.root[S, M, Pl](s, game.moves(s).toList)
      // game.currentPlayer(s) is the player about to move from s —
      // i.e. the player who will make the first move in the search tree.
      val rootPlayer = game.currentPlayer(s)(using state)
      for _ <- 1 to iterations do iterate(root, rootPlayer, random)
      // Most visited child is the most robust choice.
      root.children.maxByOption(_.visits).flatMap(_.move)

  // ---------------------------------------------------------------------------
  // MCTS phases
  // ---------------------------------------------------------------------------

  /**
    * One full MCTS iteration: selection → expansion → simulation → backprop.
    *
    * @param root       the root of the search tree.
    * @param rootPlayer the player to move at the root.
    * @param random     random source for expansion and simulation.
    */
  private def iterate(root: MCTSNode[S, M, Pl], rootPlayer: Pl, random: Random): Unit =
    val (node, path, pl) = select(root, rootPlayer)
    val (leaf, fullPath, leafPl) = expand(node, path, pl, random)
    val result = simulate(leaf.state, leafPl, random)
    backpropagate(fullPath, result)

  /**
    * Selection: descend by UCB1, accumulating path and tracking current player.
    * Stops at a terminal node or one with untried moves.
    *
    * Returns (selectedNode, path, playerToMoveAtSelectedNode).
    */
  private def select(
                      root: MCTSNode[S, M, Pl],
                      rootPlayer: Pl
                    ): (MCTSNode[S, M, Pl], List[MCTSNode[S, M, Pl]], Pl) =
    var node = root
    var path = List(root)
    var pl = rootPlayer
    while node.isFullyExpanded && !node.isLeaf && state.isGoal(node.state).isEmpty do
      node = node.children.maxBy(child => ucb1(child, node.visits))
      path = node :: path
      // The player who just moved to reach this node is node.movedBy.
      // The player to move next is their opponent.
      pl = node.movedBy.map(mover => game.nextPlayer(node.state, mover)).getOrElse(pl)
    (node, path, pl)

  /**
    * Expansion: pick a random untried move, create a child, add to tree.
    * Returns (newLeaf, updatedPath, playerToMoveFromLeaf).
    */
  private def expand(
                      node: MCTSNode[S, M, Pl],
                      path: List[MCTSNode[S, M, Pl]],
                      pl: Pl,
                      random: Random
                    ): (MCTSNode[S, M, Pl], List[MCTSNode[S, M, Pl]], Pl) =
    if state.isGoal(node.state).isDefined || node.untriedMoves.isEmpty then
      (node, path, pl)
    else
      val idx = random.nextInt(node.untriedMoves.size)
      val move = node.untriedMoves(idx)
      node.untriedMoves = node.untriedMoves.patch(idx, Nil, 1)
      val nextState = game.applyMove(node.state, move, pl)
      val nextPl = game.nextPlayer(nextState, pl)
      val child = MCTSNode.child[S, M, Pl](nextState, move, pl, game.moves(nextState).toList)
      node.children += child
      (child, child :: path, nextPl)

  /**
    * Simulation: play randomly from `s` to a terminal state.
    * Returns the GameResult of the terminal position.
    *
    * @param s  the starting state.
    * @param pl the player to move first in the simulation.
    */
  private def simulate(s: S, pl: Pl, random: Random): GameResult[Pl] =
    var current = s
    var currentPl = pl
    while state.isGoal(current).isEmpty do
      val moves = game.moves(current)
      if moves.isEmpty then
        return GameResult.draw(game.players)
      val move = moves(random.nextInt(moves.size))
      current = game.applyMove(current, move, currentPl)
      currentPl = game.nextPlayer(current, currentPl)
    state.isGoal(current) match
      case Some(true) => game.winner(current, currentPl)
      case Some(false) => GameResult.draw(game.players)
      case None => GameResult.draw(game.players)

  /**
    * Backpropagation: walk the path from leaf to root, updating visit counts
    * and win scores. Each node's win score is from the perspective of the
    * player who moved INTO that node (`movedBy`).
    */
  private def backpropagate(path: List[MCTSNode[S, M, Pl]], result: GameResult[Pl]): Unit =
    path.foreach { node =>
      node.visits += 1
      node.movedBy.foreach { mover =>
        node.wins += GameResult.score(result, mover)
      }
    }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /**
    * UCB1 score for a child given its parent's visit count.
    * Unvisited nodes get MaxValue to ensure they are tried first.
    */
  private def ucb1(node: MCTSNode[S, M, Pl], parentVisits: Int): Double =
    if node.visits == 0 then Double.MaxValue
    else
      node.wins / node.visits +
        explorationConstant * math.sqrt(math.log(parentVisits.toDouble) / node.visits)