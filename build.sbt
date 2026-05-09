organization := "com.phasmidsoftware"

name := "DecisionTree"

version := "1.0.6-SNAPSHOT"

scalaVersion := "3.7.4"

val scalaTestVersion = "3.2.20"

scalacOptions ++= Seq("-deprecation", "-feature")

javacOptions ++= Seq("-source", "17", "-target", "17")

resolvers += "Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
  "com.phasmidsoftware" %% "flog" % "1.0.10",
  "com.phasmidsoftware" %% "visitor" % "1.6.0",
  "org.scalatest" %% "scalatest" % scalaTestVersion % "test",
  "ch.qos.logback" % "logback-classic" % "1.5.32" % "runtime",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.6",
  "junit" % "junit" % "4.13.2" % "test"
)
