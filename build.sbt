import sbt.Keys._

val sl4jApiVersion = "1.7.30"
val scalaTestVersion = "3.2.2"
val kolibriDatatypesVersion = "0.1.0-alpha1"

val akkaVersion = "2.6.12"
val akkaContribVersion = "2.5.31"
val akkaHttpVersion = "10.2.1"
val akkaManagementVersion = "1.0.8"
val shapelessVersion = "2.3.3"
val logbackVersion = "1.2.3"
val kryoSerializationVersion = "2.0.1"
val awsSdkVersion = "1.11.713"
val apacheCommonsIOVersion = "2.8.0"
val macWireVersion = "2.3.7"


ThisBuild / organization := "de.awagen.kolibri"
ThisBuild / scalaVersion := "2.13.2"
ThisBuild / version := "0.1.0-alpha1"


lazy val jvmOptions = Seq(
  "-Xms1G",
  "-Xmx4G",
  "-Xss1M",
  "-XX:+CMSClassUnloadingEnabled",
  "-XX:MaxPermSize=256M"
)

test in assembly := {} //causes no tests to be executed when calling "sbt assembly" (without this setting executes all)
assemblyJarName in assembly := s"kolibri-base.${version.value}.jar" //jar name
//sbt-assembly settings. If it should only hold for specific subproject build, place the 'assemblyMergeStrategy in assembly' within subproject settings
assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs@_*) => MergeStrategy.discard
  case manifest if manifest.contains("MANIFEST.MF") =>
    // We don't need manifest files since sbt-assembly will create
    // one with the given settings
    MergeStrategy.discard
  case referenceOverrides if referenceOverrides.contains("reference-overrides.conf") =>
    // Keep the content for all reference-overrides.conf files
    MergeStrategy.concat
  case reference if reference.contains("reference.conf") =>
    // Keep the content for all reference.conf files
    MergeStrategy.concat
  //HttpRequest.class included since assembly plugin exited due to conflict on akka-http-core library, but was same version (maybe conflict between test and runtime?)
  case x if x.endsWith("HttpRequest.class") =>
    MergeStrategy.first
  // same class conflicts (too general but resolving for now)
  case x if x.endsWith(".class") =>
    MergeStrategy.first
  case x =>
    // For all the other files, use the default sbt-assembly merge strategy
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

// Scala Compiler Options
ThisBuild / scalacOptions ++= Seq(
  //"-target:jvm-8",
  "-encoding", "UTF-8",
  "-deprecation", // warning and location for usages of deprecated APIs
  "-feature", // warning and location for usages of features that should be imported explicitly
  "-unchecked", // additional warnings where generated code depends on assumptions
  "-Xlint", // recommended additional warnings
  "-Ywarn-value-discard", // Warn when non-Unit expression results are unused
  "-Ywarn-dead-code",
)
//javaOptions
ThisBuild / javaOptions in Runtime ++= jvmOptions
ThisBuild / javaOptions in Test ++= jvmOptions

//javacOptions
ThisBuild / javacOptions ++= Seq(
  "-source", "13",
  "-target", "13"
)

//scalacOptions
ThisBuild / scalacOptions ++= Seq(
  "-encoding", "utf8", // Option and arguments on same line
  "-language:postfixOps" // New lines for each options
)

//by default run types run on same JVM as sbt. This might lead to problems, thus we fork the JVM.
ThisBuild / fork in Runtime := true
ThisBuild / fork in Test := true
ThisBuild / fork in run := true

//logging
//disables buffered logging (buffering would cause results of tests to be logged only at end of all tests)
//http://www.scalatest.org/user_guide/using_scalatest_with_sbt
ThisBuild / logBuffered in Test := false
//disable version conflict messages
ThisBuild / evictionWarningOptions in update := EvictionWarningOptions.default
  .withWarnTransitiveEvictions(false)
  .withWarnDirectEvictions(false)

val additionalResolvers = Seq(
  ("scalaz-bintray" at "https://dl.bintray.com/scalaz/releases").withAllowInsecureProtocol(false),
  ("Akka Snapshot Repository" at "https://repo.akka.io/snapshots/").withAllowInsecureProtocol(false),
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots"))
//  Resolver.mavenLocal)


val additionalDependencies = Seq(
  //akka-management there to host HTTP endpoints used during bootstrap process
  "com.lightbend.akka.management" %% "akka-management" % akkaManagementVersion,
  //helps forming (or joining to) a cluster using akka discovery to discover peer nodes
  "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % akkaManagementVersion,
  //akka-management-cluster-http is extension to akka-management and allows interaction with cluster through HTTP interface
  //thus it is not necessary but might be conveniant
  // (https://doc.akka.io/docs/akka-management/current/cluster-http-management.html)
  "com.lightbend.akka.management" %% "akka-management-cluster-http" % akkaManagementVersion,
  // tag-based aws discovery
  "com.lightbend.akka.discovery" %% "akka-discovery-aws-api" % akkaManagementVersion,
  // to discover other members of the cluster
  "com.typesafe.akka" %% "akka-discovery" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-metrics" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion, // needed to use cluster singleton (https://doc.akka.io/docs/akka/2.5/cluster-singleton.html?language=scala)
  "com.typesafe.akka" %% "akka-contrib" % akkaContribVersion,
  "com.typesafe.akka" %% "akka-distributed-data" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion,
  "com.typesafe.akka" %% "akka-protobuf" % akkaVersion,
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
  //scala test framework (scalactic is recommended but not required)(http://www.scalatest.org/install)
  "org.scalactic" %% "scalactic" % scalaTestVersion % Test,
  "org.scalatest" %% "scalatest" % scalaTestVersion % Test,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http2-support" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % logbackVersion,
  "com.chuusai" %% "shapeless" % shapelessVersion,
  "io.altoo" %% "akka-kryo-serialization" % kryoSerializationVersion,
  "com.amazonaws" % "aws-java-sdk" % awsSdkVersion,
  // kolibri datatypes
  "de.awagen.kolibri" %% "kolibri-datatypes" % kolibriDatatypesVersion,
  "org.slf4j" % "slf4j-api" % sl4jApiVersion,
  "commons-io" % "commons-io" % apacheCommonsIOVersion,
)


lazy val `kolibri-base` = (project in file("."))
  .settings(
    name := "kolibri-base",
    libraryDependencies ++= additionalDependencies,
    resolvers ++= additionalResolvers,
    mainClass in assembly := Some("de.awagen.kolibri.base.cluster.ClusterNode"),
  )
  .enablePlugins(JvmPlugin)