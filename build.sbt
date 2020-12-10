val Http4sVersion = "0.21.5"
val CirceVersion = "0.13.0"
val Specs2Version = "4.10.0"
val LogbackVersion = "1.2.3"
val cron4sVersion = "0.6.1"

val zioVersion = "1.0.0-RC21"
val zioConfigVersion = "1.0.0-RC21"
val zioInteropCatsVersion = "2.1.3.0-RC16"
val greyhoundVersion = "0.1.1"

val circeExtrasVersion = "0.13.0"

lazy val root = (project in file("."))
  .settings(
    organization := "com.ak",
    name := "cronula",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "2.12.12",
    libraryDependencies ++= Seq(


      "dev.zio" %% "zio" % zioVersion,
      "dev.zio" %% "zio-config" % zioConfigVersion,
      "dev.zio" %% "zio-config-magnolia" % zioConfigVersion,
      "dev.zio" %% "zio-config-typesafe" % zioConfigVersion,
      "dev.zio" %% "zio-interop-cats" % zioInteropCatsVersion,

      "dev.zio" %% "zio-streams" % zioVersion,

      "com.github.alonsodomin.cron4s" %% "cron4s-core" % cron4sVersion,
      "com.github.alonsodomin.cron4s" %% "cron4s-circe" % cron4sVersion,

      "org.http4s" %% "http4s-blaze-server" % Http4sVersion,
      "org.http4s" %% "http4s-blaze-client" % Http4sVersion,
      "org.http4s" %% "http4s-circe" % Http4sVersion,
      "org.http4s" %% "http4s-dsl" % Http4sVersion,

      "org.specs2" %% "specs2-core" % Specs2Version % Test,
      "com.wix" %% "specs2-jmock" % "1.5.1" % Test,
      "org.jmock" % "jmock-junit4" % "2.12.0" % Test,
      "ch.qos.logback" % "logback-classic" % LogbackVersion,

      "com.wix" %% "greyhound-core" % greyhoundVersion,

      "io.circe" %% "circe-parser" % CirceVersion,
      "io.circe" %% "circe-generic-extras" % circeExtrasVersion,
      "io.circe" %% "circe-shapes" % circeExtrasVersion,
      "io.circe" %% "circe-generic" % CirceVersion,
    ),
    addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
  )

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-language:higherKinds",
  "-language:postfixOps",
  "-feature",
  "-Xfatal-warnings",
  "-Ypartial-unification"
)
