organization := "com.phasmidsoftware"

name := "DecisionTree"

version := "1.0.7"

scalaVersion := "3.7.4"

val scalaTestVersion = "3.2.20"

scalacOptions ++= Seq(
//  "-unchecked",
//  "-Xfatal-warnings",
  "-deprecation",
  "-feature"
)

javacOptions ++= Seq("-source", "17", "-target", "17")

libraryDependencies ++= Seq(
  "com.phasmidsoftware"        %% "flog"             % "1.0.13",
  "com.phasmidsoftware"        %% "visitor"          % "1.6.0",
  "com.typesafe.scala-logging" %% "scala-logging"    % "3.9.6",
  "ch.qos.logback"              % "logback-classic"  % "1.5.32" % Runtime,
  "org.scalatest"              %% "scalatest"        % scalaTestVersion % Test,
  "junit" % "junit" % "4.13.2" % "test" // Used by java unit tests
)
