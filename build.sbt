organization in ThisBuild := "com.github.benfradet"

lazy val baseSettings = Seq(
  scalacOptions in (Compile, console) ~= {
    _.filterNot(Set("-Ywarn-unused-import"))
  },
  scalacOptions in (Test, console) ~= {
    _.filterNot(Set("-Ywarn-unused-import"))
  },
  scalaVersion := "2.12.8",
  version := "0.1.0-SNAPSHOT"
)

import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}
lazy val shared = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("shared"))
  .settings(baseSettings)

lazy val sharedJVM = shared.jvm.settings(name := "sharedJVM")
lazy val sharedJS = shared.js.settings(name := "sharedJS")

lazy val scalajsDomVersion = "0.9.6"
lazy val scalajsReactVersion = "1.3.1"
lazy val reactVersion = "16.7.0"
lazy val chartjsVersion = "2.7.2"

lazy val client = project.in(file("client"))
  .settings(baseSettings)
  .settings(
    name := "client",
    scalaJSUseMainModuleInitializer := true,
    scalaJSUseMainModuleInitializer in Test := false,
    skip in packageJSDependencies := false,
    scalacOptions in (Compile) ~= {
      _.filterNot(Set("-Xfatal-warnings"))
    },
    libraryDependencies ++= Seq(
      "com.github.japgolly.scalajs-react" %%% "core",
      "com.github.japgolly.scalajs-react" %%% "extra"
    ).map(_ % scalajsReactVersion) :+
      "org.scala-js" %%% "scalajs-dom" % scalajsDomVersion,
    jsDependencies ++= Seq(
      "org.webjars.npm" % "js-tokens" % "4.0.0" / "META-INF/resources/webjars/js-tokens/4.0.0/index.js",
      "org.webjars.npm" % "react" % reactVersion
        /        "umd/react.development.js"
        minified "umd/react.production.min.js"
        commonJSName "React",
      "org.webjars.npm" % "react-dom" % reactVersion
        /         "umd/react-dom.development.js"
        minified  "umd/react-dom.production.min.js"
        dependsOn "umd/react.development.js"
        commonJSName "ReactDOM",
      "org.webjars.npm" % "react-dom" % reactVersion
        /         "umd/react-dom-server.browser.development.js"
        minified  "umd/react-dom-server.browser.production.min.js"
        dependsOn "umd/react-dom.development.js"
        commonJSName "ReactDOMServer",
      "org.webjars" % "chartjs" % chartjsVersion
        /        "Chart.js"
        minified "Chart.min.js"
    )
  )
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(sharedJS)

import com.typesafe.sbt.packager.docker._
dockerBaseImage := "openjdk:8u171-jre-alpine"
dockerExposedPorts := Seq(8080)
dockerExposedVolumes := Seq("/dashing/config")
maintainer in Docker := "Ben Fradet <https://github.com/BenFradet>"

lazy val http4sVersion = "0.20.0-M4"
lazy val github4sVersion = "0.20.0"
lazy val circeVersion = "0.11.0"
lazy val circeConfigVersion = "0.5.0"
lazy val scalatagsVersion = "0.6.7"
lazy val mulesVersion = "0.2.0-M2"
lazy val logbackVersion = "1.2.3"
lazy val specs2Version = "4.3.6"

lazy val server = project.in(file("server"))
  .settings(baseSettings)
  .settings(
    name := "server",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-dsl",
      "org.http4s" %% "http4s-blaze-server"
    ).map(_ % http4sVersion) ++ Seq(
      "com.47deg" %% "github4s",
      "com.47deg" %% "github4s-cats-effect"
    ).map(_ % github4sVersion) ++ Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-generic"
    ).map(_ % circeVersion) ++ Seq(
      "com.lihaoyi" %% "scalatags" % scalatagsVersion,
      "io.circe" %% "circe-config" % circeConfigVersion,
      "io.chrisdavenport" %% "mules" % mulesVersion,
      "ch.qos.logback" % "logback-classic" % logbackVersion
    ) ++ Seq(
      "org.specs2" %% "specs2-core" % specs2Version,
      "org.http4s" %% "http4s-testing" % http4sVersion
    ).map(_ % "test")
  )
  .settings(
    // lets us access client-fastopt.js
    resources in Compile += (fastOptJS in (client, Compile)).value.data,
    // lets us access client-fastopt.js.map
    resources in Compile += (fastOptJS in (client, Compile)).value
      .map((f: File) => new File(f.getAbsolutePath + ".map"))
      .data,
    // lets us access client-jsdeps.js
    (managedResources in Compile) +=
      (artifactPath in (client, Compile, packageJSDependencies)).value
  )
  .enablePlugins(JavaServerAppPackaging)
  .dependsOn(sharedJVM)
