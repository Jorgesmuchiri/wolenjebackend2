lazy val akkaVersion     = "2.6.10"
lazy val akkaHttpVersion = "10.1.11"

lazy val sharedSettings = Seq(
  organization := "com.wolenejeMerchantCore",
  version      := "0.1.1",
  scalaVersion := "2.12.12"
)

lazy val wolenje = (project in file(".")).aggregate(core, service, web)

lazy val core = (project in file("core"))
  .settings(
    sharedSettings,
    libraryDependencies ++= Seq(
      "com.typesafe.akka"        %% "akka-actor"            % akkaVersion,
      "com.typesafe.akka"        %% "akka-http"             % akkaHttpVersion,
      "com.typesafe.akka"        %% "akka-stream"           % akkaVersion,
      "com.typesafe.akka"        %% "akka-http-spray-json"  % akkaHttpVersion,
      "com.github.jurajburian"   %% "mailer"                % "1.2.3" withSources,
      "com.typesafe.akka"        %% "akka-http-caching"     % akkaHttpVersion,
      "net.debasishg"            %% "redisclient"           % "3.20",
      "com.typesafe.slick"       %% "slick"                 % "3.3.2",
      "com.typesafe.slick"       %% "slick-hikaricp"        % "3.3.2",
      "org.postgresql"           %  "postgresql"            % "42.2.5",
      "joda-time"                %  "joda-time"             % "2.10.6",
      "org.scala-lang"           %  "scala-reflect"         % "2.10.3",
      "org.scalatest"            %  "scalatest_2.12"        % "3.0.1"          % "test",
      "com.typesafe.akka"        %% "akka-testkit"          % akkaVersion      % Test,
      "com.typesafe.akka"        %% "akka-http-testkit"     % akkaHttpVersion  % Test,
      "com.typesafe.akka"        %% "akka-stream-testkit"   % akkaVersion      % Test,
      "org.scala-lang.modules"   %% "scala-xml"             % "1.2.0",
      "org.apache.logging.log4j" %  "log4j-slf4j-impl"      % "2.7",
      "org.lz4"                  %  "lz4-java"              % "1.6.0"
    )
  )

lazy val service = (project in file("service"))
  .dependsOn(core)
  .settings(
    sharedSettings,
    libraryDependencies ++= Seq(
      "org.scalatest"       %  "scalatest_2.12"        % "3.0.1"          % "test",
      "com.typesafe.akka"   %% "akka-testkit"          % akkaVersion      % Test,
      "com.typesafe.akka"   %% "akka-http-testkit"     % akkaHttpVersion  % Test,
      "com.typesafe.akka"   %% "akka-stream-testkit"   % akkaVersion      % Test
    )
  )

lazy val web = (project in file("web"))
  .dependsOn(service)