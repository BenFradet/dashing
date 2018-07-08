package dashing.server

import cats.Monoid
import cats.instances.all._
import cats.syntax.semigroup._
import io.circe.Encoder
import io.circe.syntax._
import io.circe.generic.auto._

object model {
  final case class DashingConfig(
    ghToken: String,
    org: String,
    heroRepo: String,
    topNRepos: Int
  )

  final case class DataPoint(label: String, value: Double)
  type Timeline = List[DataPoint]

  final case class GHObject(author: String, created: String)

  sealed trait CacheEntry
  object CacheEntry {
    implicit val cacheEntryEncoder: Encoder[CacheEntry] = Encoder.instance {
      case c: GHObjectTimeline => c.asJson
      case c: Repo => c.asJson
      case c: Repos => c.repos.asJson
    }
  }
  final case class GHObjectTimeline(members: Timeline, nonMembers: Timeline) extends CacheEntry
  final case class Repo(name: String, starsTimeline: Timeline, stars: Int) extends CacheEntry
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
  final case class Repos(repos: List[Repo]) extends CacheEntry
}
