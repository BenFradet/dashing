package dashing.server

import scala.concurrent.duration.FiniteDuration

import cats.{Eq, Monoid}
import cats.instances.all._
import cats.syntax.semigroup._

object model {
  final case class StarDashboardsConfig(
    org: String,
    heroRepo: String,
    topNRepos: Int,
  )
  final case class PRDashboardsConfig(
    orgs: List[String],
    lookback: FiniteDuration,
  )
  final case class DashingConfig(
    ghToken: String,
    prDashboards: PRDashboardsConfig,
    starDashboards: StarDashboardsConfig,
    cacheDuration: FiniteDuration,
    host: String,
    port: Int,
  )

  final case class Quarter(year: Int, quarter: Int) {
    override def toString: String = s"$year Q$quarter"
  }

  final case class GHObject(author: String, created: String)

  final case class Repo(name: String, starsTimeline: Map[String, Double], stars: Int)
  object Repo {
    implicit val repoEq: Eq[Repo] = Eq.fromUniversalEquals
    implicit val repoMonoid: Monoid[Repo] = new Monoid[Repo] {
      def combine(r1: Repo, r2: Repo): Repo = {
        val combined = r1.starsTimeline |+| r2.starsTimeline
        Repo(
          r1.name |+| r2.name,
          combined,
          r1.stars |+| r2.stars
        )
      }
      def empty: Repo = Repo("", Map.empty, 0)
    }
  }
  final case class Repos(repos: List[Repo])
}
