# Contributing to Gambit

Thank you for your interest in contributing! Gambit is primarily an
educational project, so contributions that improve clarity, correctness,
or pedagogical value are especially welcome.

## What We Welcome

- **Bug fixes** — particularly in game logic, heuristics, or search
- **New games** — anything that fits the `State[P,S]` / `Game[S,M,Pl]`
  typeclass pair (Chess is the natural next step)
- **New player types** — e.g. heuristic rollouts for MCTS, iterative
  deepening for AlphaBeta
- **Documentation improvements** — clearer Scaladoc, better examples
- **Test coverage** — especially for edge cases in bitboard logic or
  search correctness
- **Performance improvements** — provided they do not sacrifice readability

## What We Are Not Looking For (Right Now)

- UI or visualisation layers
- Dependencies on large external frameworks
- Changes that break the generic typeclass structure

If you are unsure whether your idea fits, open an issue first and describe
what you have in mind.

## How to Contribute

1. **Fork** the repository and create a branch from `master`:
   ```
   git checkout -b my-feature
   ```

2. **Write tests first** where possible. All new code should be covered
   by unit tests in the appropriate `*Spec.scala` file.

3. **Follow the existing conventions:**
    - Scala 3 syntax throughout (no Scala 2 compat idioms)
    - `case class` for immutable state, mutable `var` only where justified
      (see `MCTSNode` and `AlphaBetaPlayer` for precedents)
    - Scaladoc on all public methods and classes
    - Tag slow tests (`playGames` with many iterations) with
      `taggedAs org.scalatest.tagobjects.Slow`

4. **Check the heuristic convention** before touching any `heuristic`
   method or `AlphaBetaPlayer`: `heuristic(s)` must be positive when the
   player who *just moved* to reach `s` is doing well. Violating this
   convention silently produces wrong search behaviour. See
   `GamePlayingDesign.md` for details.

5. **Run the test suite** before submitting:
   ```
   sbt test
   ```
   CircleCI will also run the slow tests on merge to `master`.

6. **Open a pull request** against `master` with a clear description of
   what changed and why. Reference any related issues.

## Commit Messages

Follow the style used in the project history: a short imperative summary
line, then a bullet list of specifics if needed. Examples:

```
Add AlphaBetaPlayer with minimax search and alpha-beta pruning

- Implement generic AlphaBetaPlayer[P, S, M, Pl] with configurable depth
- Fix initial alpha bound: use -Double.MaxValue, not Double.MinValue
- Add AlphaBetaPlayerSpec covering TicTacToe and Connect4
```

## Code Style

- 2-space indentation
- `private` everything that doesn't need to be public
- Prefer `val` and immutable collections; justify any `var` with a comment
- No magic numbers — name your constants

## Questions

Open a GitHub issue or contact r.hillyard@northeastern.edu.