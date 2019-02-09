package dashing.server

import scala.concurrent.duration.FiniteDuration

import cats.{Eq, Monoid}
import cats.instances.all._
import cats.syntax.semigroup._
import io.circe.Decoder

object model {
  final case class StarDashboardsConfig(
    org: String,
    heroRepo: String,
    topNRepos: Int,
  )
  final case class PRDashboardsConfig(
    orgs: List[String],
    peopleToIgnore: List[String],
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

  sealed trait PageInfo {
    def endCursor: String
    def hasNextPage: Boolean
  }

  final case class StarsInfo(
    starsTimeline: List[String],
    endCursor: String,
    hasNextPage: Boolean
  ) extends PageInfo
  object StarsInfo {
    implicit val decoder: Decoder[StarsInfo] = Decoder.instance { c =>
      for {
        rawStars <- c.get[List[Map[String, String]]]("edges")
        starsTimeline = rawStars.map(_.values).flatten
        pageInfoCursor = c.downField("pageInfo")
        endCursor <- pageInfoCursor.get[String]("endCursor")
        hasNextPage <- pageInfoCursor.get[Boolean]("hasNextPage")
      } yield StarsInfo(starsTimeline, endCursor, hasNextPage)
    }.prepare(_.downField("data").downField("repository").downField("stargazers"))
    implicit val starsInfoEq: Eq[StarsInfo] = Eq.fromUniversalEquals
    implicit val starsInfoMonoid: Monoid[StarsInfo] = new Monoid[StarsInfo] {
      def combine(s1: StarsInfo, s2: StarsInfo): StarsInfo =
        StarsInfo(
          s1.starsTimeline |+| s2.starsTimeline,
          "",
          s1.hasNextPage || s2.hasNextPage
        )
      def empty: StarsInfo = StarsInfo(List.empty, "", true)
    }
  }
}
