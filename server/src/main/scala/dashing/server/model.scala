package dashing.server

import scala.concurrent.duration.FiniteDuration

import cats.Monoid
import cats.instances.all._
import cats.syntax.semigroup._

object model {
  final case class DashingConfig(
    ghToken: String,
    org: String,
    heroRepo: String,
    topNRepos: Int,
    cacheDuration: FiniteDuration
  )

  final case class DataPoint(label: String, value: Double)
  type Timeline = List[DataPoint]

  final case class GHObject(author: String, created: String)

  final case class GHObjectTimeline(members: Timeline, nonMembers: Timeline)
  final case class Repo(name: String, starsTimeline: Timeline, stars: Int)
  object Repo {
    implicit val repoMonoid: Monoid[Repo] = new Monoid[Repo] {
      def combine(r1: Repo, r2: Repo): Repo = {
        val combined = r1.starsTimeline.map(dt => dt.label -> dt.value).toMap |+|
          r2.starsTimeline.map(dt => dt.label -> dt.value).toMap
        Repo(
          r1.name |+| r2.name,
          combined.map { case (l, v) => DataPoint(l, v) }.toList,
          r1.stars |+| r2.stars
        )
      }
      def empty: Repo = Repo("", List.empty, 0)
    }
  }
  final case class Repos(repos: List[Repo])
}
