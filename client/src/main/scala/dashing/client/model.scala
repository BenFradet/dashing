package dashing.client

import scala.scalajs.js

object model {
  sealed trait Dashboard
  case object Home extends Dashboard
  case object HeroRepoDash extends Dashboard
  case object TopNReposDash extends Dashboard
  case object PRsDash extends Dashboard

  @js.native
  trait DataPoint extends js.Object {
    def label: String
    def value: Double
  }

  @js.native
  trait Repo extends js.Object {
    def name: String
    def starsTimeline: js.Array[DataPoint]
    def stars: Int
  }

  final case class RepoState(name: String, starsTimeline: List[DataPoint], stars: Int)
  object RepoState {
    def empty = RepoState("", List.empty, 0)
  }

  final case class GHObjectState(nonMembers: List[DataPoint])
  object GHObjectState {
    def empty = GHObjectState(List.empty)
  }
}
