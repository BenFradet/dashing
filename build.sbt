enablePlugins(ScalaJSPlugin)

organization in ThisBuild := "com.github.benfradet"

val compilerOptions = Seq(
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
  }
)

lazy val scalajsDomVersion = "0.9.1"
lazy val scalajsReactVersion = "1.1.0"
lazy val reactVersion = "15.6.1"
lazy val chartjsVersion = "2.6.0"

lazy val dashing = project.in(file("."))
  .settings(baseSettings)
  .settings(
    moduleName := "dashing",
    scalaVersion := "2.12.3",
    version := "0.1.0-SNAPSHOT",
    scalaJSUseMainModuleInitializer := true,
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
