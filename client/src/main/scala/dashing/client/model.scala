package dashing.client

import scala.scalajs.js

object model {
  sealed trait Dashboard
  case object Home extends Dashboard
  case object HeroRepoDash extends Dashboard
  case object TopNReposDash extends Dashboard
  case object PRsDash extends Dashboard

  @js.native
  trait Repo extends js.Object {
    def name: String
    def starsTimeline: js.Dictionary[Int]
    def stars: Int
  }

  final case class RepoState(name: String, starsTimeline: List[(String, Int)], stars: Int)
  object RepoState {
    def empty = RepoState("", List.empty, 0)
  }

  @js.native
  trait Timeline extends js.Object {
    def members: js.Dictionary[Int]
    def nonMembers: js.Dictionary[Int]
  }

  final case class GHObjectState(members: Map[String, Int], nonMembers: Map[String, Int])
  object GHObjectState {
    def empty = GHObjectState(Map.empty, Map.empty)
  }
}