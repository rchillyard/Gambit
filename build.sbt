organization := "com.phasmidsoftware"

name := "gambit"

version := "1.3.0"

scalaVersion := "3.9.0" // the current LTS

val scalaTestVersion = "3.2.20"

// The compiler options that all the Phasmid libraries share.
scalacOptions ++= Seq(
  "-encoding", "UTF-8",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Wunused:all",
  "-Wvalue-discard",
  "-Wnonunit-statement"
)
// Tests may use a value as a statement (for example, in a matcher), so they skip -Wnonunit-statement.
Test / scalacOptions ~= (_.filterNot(_ == "-Wnonunit-statement"))

javacOptions ++= Seq("-source", "21", "-target", "21")

lazy val versionConfig = "1.4.9"
lazy val versionScalaLogging = "3.9.6"
lazy val versionLogback = "1.5.38"
lazy val versionVisitor = "1.7.0"
lazy val versionFlog = "1.1.0"
lazy val versionJupiter = "6.0.3"
// The interface version must match the sbt-jupiter-interface plugin in project/plugins.sbt.
lazy val versionJupiterInterface = "0.19.0"

libraryDependencies ++= Seq(
  "com.phasmidsoftware"        %% "flog"             % versionFlog,
  "com.phasmidsoftware"        %% "visitor"          % versionVisitor,
  "com.typesafe.scala-logging" %% "scala-logging"    % versionScalaLogging,
  "com.typesafe"                % "config"           % versionConfig,
  "ch.qos.logback"              % "logback-classic"  % versionLogback % Runtime,
  "org.scalatest"              %% "scalatest"        % scalaTestVersion % Test,
  // The Java unit tests. jupiter-interface is the sbt test *interface*: without it sbt compiles
  // these tests and never runs them, which is what had happened here. See issue #39.
  "org.junit.jupiter"           % "junit-jupiter-api"    % versionJupiter % Test,
  "org.junit.jupiter"           % "junit-jupiter-engine" % versionJupiter % Test,
  "com.github.sbt.junit"        % "jupiter-interface"    % versionJupiterInterface % Test
)

lazy val IT = config("it") extend Test

lazy val root = project.in(file("."))
  .configs(IT)
  .settings(
    inConfig(IT)(Defaults.testSettings),
    IT / scalaSource := baseDirectory.value / "src" / "it" / "scala"
  )

// ScalaTest's tag filters must be scoped to ScalaTest. Given on the command line as
// `testOnly -- -l org.scalatest.tags.Slow`, sbt passes them to *every* test framework, and
// jupiter-interface answers an unrecognised selector by running none of the Java tests --
// silently, reported only as "0 total" among the skipped, with exit code 0. Tests.Argument
// scopes them, so the Java tests run in both jobs. See issue #39.
// Plain `sbt test` still runs everything, slow tests included.
addCommandAlias("fastTests", """;set Test/testOptions := Seq(Tests.Argument(TestFrameworks.ScalaTest, "-l", "org.scalatest.tags.Slow")) ;test""")
addCommandAlias("slowTests", """;set Test/testOptions := Seq(Tests.Argument(TestFrameworks.ScalaTest, "-n", "org.scalatest.tags.Slow")) ;test""")


// Scaladoc is published to https://rchillyard.github.io/Gambit/latest/api/ by `sbt ghpagesPushSite`
enablePlugins(GhpagesPlugin, SiteScaladocPlugin)

git.remoteRepo := "git@github.com:rchillyard/Gambit.git"