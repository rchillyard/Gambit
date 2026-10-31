organization := "com.phasmidsoftware"

name := "gambit"

version := "1.2.6"

scalaVersion := "3.9.0" // the current LTS

val scalaTestVersion = "3.2.20"

scalacOptions ++= Seq(
  "-unchecked",
  "-Werror",
  "-deprecation",
  "-feature",
  "-Wunused:all"
)

javacOptions ++= Seq("-source", "17", "-target", "17")

lazy val versionConfig = "1.4.9"
lazy val versionScalaLogging = "3.9.6"
lazy val versionLogback = "1.5.38"
lazy val versionVisitor = "1.6.0"
lazy val versionFlog = "1.0.15"

libraryDependencies ++= Seq(
  "com.phasmidsoftware"        %% "flog"             % versionFlog,
  "com.phasmidsoftware"        %% "visitor"          % versionVisitor,
  "com.typesafe.scala-logging" %% "scala-logging"    % versionScalaLogging,
  "com.typesafe"                % "config"           % versionConfig,
  "ch.qos.logback"              % "logback-classic"  % versionLogback % Runtime,
  "org.scalatest"              %% "scalatest"        % scalaTestVersion % Test,
  "junit" % "junit" % "4.13.2" % "test" // Used by java unit tests
)

lazy val IT = config("it") extend Test

lazy val root = project.in(file("."))
  .configs(IT)
  .settings(
    inConfig(IT)(Defaults.testSettings),
    IT / scalaSource := baseDirectory.value / "src" / "it" / "scala"
  )

// JUnit-style reports, collected by CircleCI
Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-u", "target/test-reports")

// Scaladoc is published to https://rchillyard.github.io/Gambit/latest/api/ by `sbt ghpagesPushSite`
enablePlugins(GhpagesPlugin, SiteScaladocPlugin)

git.remoteRepo := "git@github.com:rchillyard/Gambit.git"