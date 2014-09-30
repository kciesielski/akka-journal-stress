name := "akka-journal-stress"

version := "1.0"

scalaVersion := "2.10.4"

resolvers += "spray repo" at "http://repo.spray.io"

resolvers += "spray nightlies" at "http://nightlies.spray.io"

val akkaVersion = "2.3.4"

val json4sVersion = "3.2.10"

libraryDependencies ++= Seq(
  "com.typesafe.akka"  %% "akka-actor"       % akkaVersion,
  "com.typesafe.akka"  %% "akka-slf4j"       % akkaVersion,
  "com.typesafe.akka"  %% "akka-persistence-experimental"       % akkaVersion,
  "com.typesafe.akka"  %% "akka-testkit"     % akkaVersion    % "test",
  "joda-time"           % "joda-time"        % "2.4",
  "org.json4s"         %% "json4s-jackson"   % json4sVersion,
  "org.json4s"         %% "json4s-ext"       % json4sVersion,
  "org.json4s"         %% "json4s-mongo"     % json4sVersion,
  "ch.qos.logback"      % "logback-classic"  % "1.0.13",
  "io.spray"            % "spray-can"        % "1.2-20130712",
  "io.spray"            % "spray-routing"    % "1.2-20130712",
  "io.spray"           %% "spray-json"       % "1.2.3",
  "org.specs2"         %% "specs2"           % "1.14"         % "test",
  "io.spray"            % "spray-testkit"    % "1.2-20130712" % "test",
  "com.novocode"        % "junit-interface"  % "0.7"          % "test->default"
)

scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-Xlint",
  "-Ywarn-dead-code",
  "-language:_",
  "-target:jvm-1.7",
  "-encoding", "UTF-8"
)

testOptions += Tests.Argument(TestFrameworks.JUnit, "-v")
