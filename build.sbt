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

lazy val scalajsDomVersion = "0.9.1"
lazy val scalajsReactVersion = "1.1.0"
lazy val reactVersion = "15.6.1"
lazy val chartjsVersion = "2.6.0"

lazy val shared = (crossProject.crossType(CrossType.Pure).in(file("shared")))

lazy val sharedJVM = shared.jvm.settings(name := "sharedJVM")
lazy val sharedJS = shared.js.settings(name := "sharedJS")

lazy val client = project.in(file("client"))
  .settings(baseSettings)
  .settings(
    name := "client",
    scalaJSUseMainModuleInitializer := true,
    scalaJSUseMainModuleInitializer in Test := false,
    skip in packageJSDependencies := false,
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % scalajsDomVersion,
      "com.github.japgolly.scalajs-react" %%% "core" % scalajsReactVersion,
      "com.github.japgolly.scalajs-react" %%% "extra" % scalajsReactVersion
    ),
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

lazy val server = project.in(file("server"))
  .settings(baseSettings)
  .settings(
    name := "server"
  )