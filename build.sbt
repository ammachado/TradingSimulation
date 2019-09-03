import sbt.Keys._
import sbt._

EclipseKeys.skipParents in ThisBuild := false

name := "TradingSimProject"

version in ThisBuild := "0.1"

scalaVersion in ThisBuild := "2.12.9"

autoScalaLibrary in ThisBuild := true

//ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }

scalacOptions in ThisBuild ++= Seq("-deprecation", "-feature")

lazy val frontend = (project in file("frontend"))
    .enablePlugins(PlayScala)
    .settings(
        name := "frontend",
        libraryDependencies ++= (Seq(specs2) ++ Dependencies.frontend ++ Seq(filters, cacheApi)),
        pipelineStages := Seq(rjs, digest, gzip)
    )
    .dependsOn(ts)
    .aggregate(ts)

lazy val ts = (project in file("ts"))
    .settings(
        name := "ts",
        libraryDependencies ++= Dependencies.ts,
        // Add res directory to runtime classpath
        unmanagedClasspath in Runtime += baseDirectory.value / "src/main/resources"
    )

// Some of our tests require sequential execution
parallelExecution in Test in ts := false

parallelExecution in Test in frontend := false
