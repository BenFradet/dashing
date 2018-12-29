package dashing.client

import scala.scalajs.js

object model {
  sealed trait Dashboard
  case object Home extends Dashboard
  case object HeroRepoDash extends Dashboard
  case object TopNReposDash extends Dashboard
  case object QuarterlyPRsDash extends Dashboard
  case object MonthlyPRsDash extends Dashboard

  @js.native
  trait Repo extends js.Object {
    def name: String
    def starsTimeline: js.Dictionary[Double]
    def stars: Int
  }

  final case class RepoState(name: String, starsTimeline: Map[String, Double], stars: Int)
  object RepoState {
    def empty = RepoState("", Map.empty, 0)
  }

  final case class PRsState(prsByOrg: Map[String, Map[String, Double]])
  object PRsState {
    def empty = PRsState(Map.empty)
  }
}
