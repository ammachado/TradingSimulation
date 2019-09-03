import sbt._

object Dependencies {

  lazy val frontend: Seq[ModuleID] = common ++ webjars
  lazy val ts: Seq[ModuleID] = common ++ tradingsimulation

  val common: Seq[ModuleID] = Seq(
    "org.scala-lang" % "scala-compiler" % "2.12.9",
    "junit" % "junit" % "4.12" % "test",
    "org.scalatest" %% "scalatest" % "3.0.8" % "test",
    "com.typesafe" % "config" % "1.3.3" withSources() withJavadoc(),
    "com.typesafe.akka" %% "akka-actor" % "2.5.25" withSources() withJavadoc(),
    "com.typesafe.akka" %% "akka-remote" % "2.5.25" withSources() withJavadoc(),
    "com.typesafe.akka" %% "akka-slf4j" % "2.5.25" withSources() withJavadoc(),
    "com.typesafe.akka" %% "akka-testkit" % "2.5.25" % "test",
    "com.typesafe.slick" %% "slick" % "2.1.0" withSources() withJavadoc()
  )

  val webjars: Seq[ModuleID] = Seq(
    "org.webjars" % "requirejs" % "2.3.6",
    "org.webjars" % "underscorejs" % "1.9.0",
    "org.webjars" % "jquery" % "1.11.1",
    "org.webjars" % "highstock" % "2.0.4",
    "org.webjars" % "highcharts-ng" % "0.0.8",
    "org.webjars" % "bootstrap" % "3.3.7-1" exclude("org.webjars", "jquery"),
    "org.webjars" % "bootswatch-superhero" % "3.3.7",
    "org.webjars" % "angularjs" % "1.2.16-2" exclude("org.webjars", "jquery"),
    "org.webjars" % "angular-ui-bootstrap" % "0.12.1-1",
    "org.webjars" % "ng-table" % "0.3.3",
    "net.liftweb" %% "lift-json" % "3.3.0"
  )

  val tradingsimulation: Seq[ModuleID] = Seq(
    "net.liftweb" %% "lift-json" % "3.3.0" withSources() withJavadoc(),
    "org.apache.httpcomponents" % "fluent-hc" % "4.5.9" withSources() withJavadoc(),
    "org.xerial" % "sqlite-jdbc" % "3.28.0" withSources() withJavadoc()
  )
}
