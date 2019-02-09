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
          s1.endCursor |+| s2.endCursor,
          s1.hasNextPage && s2.hasNextPage
        )
      def empty: StarsInfo = StarsInfo(List.empty, "", true)
    }
  }

  final case class AuthorAndTimestamp(
    author: String,
    timestamp: String
  )
  object AuthorAndTimestamp {
    implicit val decoder: Decoder[AuthorAndTimestamp] = Decoder.instance { c =>
      for {
        author <- c.downField("author").get[String]("login")
        timestamp <- c.get[String]("createdAt")
      } yield AuthorAndTimestamp(author, timestamp)
    }.prepare(_.downField("node"))
  }
  final case class PullRequestsInfo(
    pullRequests: List[AuthorAndTimestamp],
    endCursor: String,
    hasNextPage: Boolean
  ) extends PageInfo
  object PullRequestsInfo {
    implicit val decoder: Decoder[PullRequestsInfo] = Decoder.instance { c =>
      for {
        prs <- c.get[List[AuthorAndTimestamp]]("edges")
        pageInfoCursor = c.downField("pageInfo")
        endCursor <- pageInfoCursor.get[String]("endCursor")
        hasNextPage <- pageInfoCursor.get[Boolean]("hasNextPage")
      } yield PullRequestsInfo(prs, endCursor, hasNextPage)
    }.prepare(_.downField("data").downField("repository").downField("pullRequests"))
    implicit val prsInfoEq: Eq[PullRequestsInfo] = Eq.fromUniversalEquals
    implicit val prsInfoMonoid: Monoid[PullRequestsInfo] = new Monoid[PullRequestsInfo] {
      def combine(p1: PullRequestsInfo, p2: PullRequestsInfo): PullRequestsInfo =
        PullRequestsInfo(
          p1.pullRequests |+| p2.pullRequests,
          p1.endCursor |+| p2.endCursor,
          p1.hasNextPage && p2.hasNextPage
        )
      def empty: PullRequestsInfo = PullRequestsInfo(List.empty, "", true)
    }
  }

  final case class OrgMembersInfo(
    members: List[String],
    endCursor: String,
    hasNextPage: Boolean
  ) extends PageInfo
  object OrgMembersInfo {
    implicit val decoder: Decoder[OrgMembersInfo] = Decoder.instance { c =>
      for {
        rawMembers <- c.get[List[Map[String, String]]]("nodes")
        members = rawMembers.map(_.values).flatten
        pageInfoCursor = c.downField("pageInfo")
        endCursor <- pageInfoCursor.get[String]("endCursor")
        hasNextPage <- pageInfoCursor.get[Boolean]("hasNextPage")
      } yield OrgMembersInfo(members, endCursor, hasNextPage)
    }.prepare(_.downField("data").downField("organization").downField("membersWithRole"))
    implicit val orgMembersInfoEq: Eq[OrgMembersInfo] = Eq.fromUniversalEquals
    implicit val orgMembersInfoMonoid: Monoid[OrgMembersInfo] = new Monoid[OrgMembersInfo] {
      def combine(o1: OrgMembersInfo, o2: OrgMembersInfo): OrgMembersInfo =
        OrgMembersInfo(
          o1.members |+| o2.members,
          "",
          o1.hasNextPage || o2.hasNextPage
        )
      def empty: OrgMembersInfo = OrgMembersInfo(List.empty, "", true)
    }
  }

  final case class RepositoryAndStars(
    repository: String,
    firstHundredStars: Int
  )
  object RepositoryAndStars {
    implicit val decoder: Decoder[RepositoryAndStars] = Decoder.instance { c =>
      for {
        name <- c.get[String]("name")
        firstHundredStars <- c.downField("stargazers").get[Int]("totalCount")
      } yield RepositoryAndStars(name, firstHundredStars)
    }
  }
  final case class OrgRepositoriesInfo(
    repositoriesAndStars: List[RepositoryAndStars],
    endCursor: String,
    hasNextPage: Boolean
  ) extends PageInfo
  object OrgRepositoriesInfo {
    implicit val decoder: Decoder[OrgRepositoriesInfo] = Decoder.instance { c =>
      for {
        repositoriesAndStars <- c.get[List[RepositoryAndStars]]("nodes")
        pageInfoCursor = c.downField("pageInfo")
        endCursor <- pageInfoCursor.get[String]("endCursor")
        hasNextPage <- pageInfoCursor.get[Boolean]("hasNextPage")
      } yield OrgRepositoriesInfo(repositoriesAndStars, endCursor, hasNextPage)
    }.prepare(_.downField("data").downField("organization").downField("repositories"))

    implicit val orgRepositoriesInfoEq: Eq[OrgRepositoriesInfo] = Eq.fromUniversalEquals

    implicit val orgRepositoriesInfoMonoid: Monoid[OrgRepositoriesInfo] =
      new Monoid[OrgRepositoriesInfo] {
        def combine(o1: OrgRepositoriesInfo, o2: OrgRepositoriesInfo): OrgRepositoriesInfo =
          OrgRepositoriesInfo(
            o1.repositoriesAndStars |+| o2.repositoriesAndStars,
            "",
            o1.hasNextPage || o2.hasNextPage
          )
        def empty: OrgRepositoriesInfo = OrgRepositoriesInfo(List.empty, "", true)
      }
  }
}
