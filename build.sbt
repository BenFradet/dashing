organization in ThisBuild := "com.github.benfradet"

lazy val compilerOptions = Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-unchecked",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ywarn-unused-import",
  "-Xfuture"
)

lazy val baseSettings = Seq(
  scalacOptions ++= compilerOptions,
  scalacOptions in (Compile, console) ~= {
    _.filterNot(Set("-Ywarn-unused-import"))
  },
  scalacOptions in (Test, console) ~= {
    _.filterNot(Set("-Ywarn-unused-import"))
  },
  scalaVersion := "2.12.3",
  version := "0.1.0-SNAPSHOT"
)

lazy val shared = (crossProject.crossType(CrossType.Pure).in(file("shared")))
  .settings(baseSettings)

lazy val sharedJVM = shared.jvm.settings(name := "sharedJVM")
lazy val sharedJS = shared.js.settings(name := "sharedJS")

lazy val scalajsDomVersion = "0.9.1"
lazy val scalajsReactVersion = "1.1.0"
lazy val reactVersion = "15.6.1"
lazy val chartjsVersion = "2.6.0"

lazy val client = project.in(file("client"))
  .settings(baseSettings)
  .settings(
    name := "client",
    scalaJSUseMainModuleInitializer := true,
    scalaJSUseMainModuleInitializer in Test := false,
    skip in packageJSDependencies := false,
    libraryDependencies ++= Seq(
      "com.github.japgolly.scalajs-react" %%% "core",
      "com.github.japgolly.scalajs-react" %%% "extra"
    ).map(_ % scalajsReactVersion) :+
      "org.scala-js" %%% "scalajs-dom" % scalajsDomVersion,
    jsDependencies ++= Seq(
      "org.webjars.bower" % "react" % reactVersion
        /        "react-with-addons.js"
        minified "react-with-addons.min.js"
        commonJSName "React",
      "org.webjars.bower" % "react" % reactVersion
        /         "react-dom.js"
        minified  "react-dom.min.js"
        dependsOn "react-with-addons.js"
        commonJSName "ReactDOM",
      "org.webjars.bower" % "chartjs" % chartjsVersion
        /        "Chart.js"
        minified "Chart.min.js"
    )
  )
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(sharedJS)

lazy val http4sVersion = "0.18.0-M1"
lazy val specs2Version = "3.9.5"
lazy val scalatagsVersion = "0.6.7"

lazy val server = project.in(file("server"))
  .settings(baseSettings)
  .settings(
    name := "server",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-dsl",
      "org.http4s" %% "http4s-blaze-server"
    ).map(_ % http4sVersion) ++ Seq(
      "com.lihaoyi" %% "scalatags" % scalatagsVersion
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
  .dependsOn(sharedJVM)