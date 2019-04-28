package dashing.server

import scala.concurrent.duration.FiniteDuration

import cats.{Eq, Monoid}
import cats.instances.all._
import cats.syntax.semigroup._
import io.circe.Decoder

object model {
  /**
   * Configuration for the star dashboards
   * @param org github organization from which we want to retrieve stars numbers
   * @param heroRepo name of the repository which should be considered the main one, it will have
   * its own dedicated star dashboards
   * @param topNRepos number of repositories which should be detailed in the dashboards relating
   * star numbers for the repositories other than the hero repository
   */
  final case class StarDashboardsConfig(
    org: String,
    heroRepo: String,
    topNRepos: Int,
  )
  /**
   * Configuration for the pull request dashboards
   * @param orgs list of the github organizations to look through when counting external pull
   * requests
   * @param peopleToIgnore github logins which should be ignored when counting pull requests from
   * people external to the organizations (e.g. past interns, bots, contractors, etc.)
   * @param lookback how far, in time, to look for pull request numbers
   */
  final case class PRDashboardsConfig(
    orgs: List[String],
    peopleToIgnore: List[String],
    lookback: FiniteDuration,
  )
  /**
   * Configuration for the whole application
   * @param ghToken github API access token
   * @param prDashboards configuration for the dashboards counting pull requests
   * @param starDashboards configuration for the dashboards counting stars
   * @param cacheDuration for how long should the data be cached
   * @param host to bind to
   * @param port to bind to
   */
  final case class DashingConfig(
    ghToken: String,
    prDashboards: PRDashboardsConfig,
    starDashboards: StarDashboardsConfig,
    cacheDuration: FiniteDuration,
    host: String,
    port: Int,
  )

  /** Case class modeling a quarter */
  final case class Quarter(year: Int, quarter: Int) {
    override def toString: String = s"$year Q$quarter"
  }

  /**
   * Case class representing a repository star information
   * @param name of the repository
   * @param starsTimeline a timeline of timestamps associated with number of stars
   * @param stars current number of stars
   */
  final case class Repo(name: String, starsTimeline: Map[String, Double], stars: Int)
  /** Repo companion object defining [[Eq]] and [[Monoid]] instances */
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
  /** Case class wrapping multiple [[Repo]] */
  final case class Repos(repos: List[Repo])

  /**
   * Trait defining pagination information:
   * - an optional cursor to the next page of data
   * - whether there is a next page of data
   */
  sealed trait PageInfo {
    def endCursor: Option[String]
    def hasNextPage: Boolean
  }

  /**
   * Case class modeling stars information as returned by the github graphql API
   * @param starsTimeline a list of timestamps for each starring event
   * @param endCursor an optional cursor to the next page of data
   * @param hasNextPage whether there is a next page of data
   */
  final case class StarsInfo(
    starsTimeline: List[String],
    endCursor: Option[String],
    hasNextPage: Boolean
  ) extends PageInfo
  /** StarsInfo companiong object defining [[Decoder]], [[Eq]] and [[Monoid]] instances */
  object StarsInfo {
    implicit val decoder: Decoder[StarsInfo] = Decoder.instance { c =>
      for {
        rawStars <- c.get[List[Map[String, String]]]("edges")
        starsTimeline = rawStars.map(_.values).flatten
        pageInfoCursor = c.downField("pageInfo")
        endCursor <- pageInfoCursor.get[Option[String]]("endCursor")
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
      def empty: StarsInfo = StarsInfo(List.empty, None, true)
    }
  }

  /**
   * Case class modeling a github action perfomed by an optional author at a specific timestamp
   * (limited to opening pull requests for now)
   * @param author optional author of the github action
   * @param timestamp at which the github action occurred
   */
  final case class AuthorAndTimestamp(
    author: Option[String],
    timestamp: String
  )
  /** AuthorAndTimestamp companion object defining a [[Decoder]] instance */
  object AuthorAndTimestamp {
    final case class Author(login: String)
    import io.circe.generic.auto._
    implicit val decoder: Decoder[AuthorAndTimestamp] = Decoder.instance { c =>
      for {
        author <- c.get[Option[Author]]("author")
        timestamp <- c.get[String]("createdAt")
      } yield AuthorAndTimestamp(author.map(_.login), timestamp)
    }.prepare(_.downField("node"))
  }
  /**
   * Case class modeling pull requests information as returned by the github graphql API
   * @param pullRequests list of pull requests defined as their optional author and creation
   * timestamp
   * @param endCursor an optional cursor to the next page of data
   * @param hasNextPage whether there is a next page of data
   */
  final case class PullRequestsInfo(
    pullRequests: List[AuthorAndTimestamp],
    endCursor: Option[String],
    hasNextPage: Boolean
  ) extends PageInfo
  /** PullRequestsInfo companion object defining [[Decoder]], [[Eq]] and [[Monoid]] instances */
  object PullRequestsInfo {
    implicit val decoder: Decoder[PullRequestsInfo] = Decoder.instance { c =>
      for {
        prs <- c.get[List[AuthorAndTimestamp]]("edges")
        pageInfoCursor = c.downField("pageInfo")
        endCursor <- pageInfoCursor.get[Option[String]]("endCursor")
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
      def empty: PullRequestsInfo = PullRequestsInfo(List.empty, None, true)
    }
  }

  /**
   * Case class modeling github organization members information as returned by the github graphql
   * API
   * @param members list of github logins members of a specific organization
   * @param endCursor an optional cursor to the next page of data
   * @param hasNextPage whether there is a next page of data
   */
  final case class OrgMembersInfo(
    members: List[String],
    endCursor: Option[String],
    hasNextPage: Boolean
  ) extends PageInfo
  /** OrgMembersInfo companion object defining [[Decoder]], [[Eq]] and [[Monoid]] instances */
  object OrgMembersInfo {
    implicit val decoder: Decoder[OrgMembersInfo] = Decoder.instance { c =>
      for {
        rawMembers <- c.get[List[Map[String, String]]]("nodes")
        members = rawMembers.map(_.values).flatten
        pageInfoCursor = c.downField("pageInfo")
        endCursor <- pageInfoCursor.get[Option[String]]("endCursor")
        hasNextPage <- pageInfoCursor.get[Boolean]("hasNextPage")
      } yield OrgMembersInfo(members, endCursor, hasNextPage)
    }.prepare(_.downField("data").downField("organization").downField("membersWithRole"))
    implicit val orgMembersInfoEq: Eq[OrgMembersInfo] = Eq.fromUniversalEquals
    implicit val orgMembersInfoMonoid: Monoid[OrgMembersInfo] = new Monoid[OrgMembersInfo] {
      def combine(o1: OrgMembersInfo, o2: OrgMembersInfo): OrgMembersInfo =
        OrgMembersInfo(
          o1.members |+| o2.members,
          o1.endCursor |+| o2.endCursor,
          o1.hasNextPage && o2.hasNextPage
        )
      def empty: OrgMembersInfo = OrgMembersInfo(List.empty, None, true)
    }
  }

  /**
   * Case class modeling a repository and its number of stars (<= 100)
   * It is used to filter out repositories that are below a minimum star threshold, it isn't worth
   * computing a stars timeline for a repository which has 5 stars
   * @param repository name
   * @param firstHunderedStars
   */
  final case class RepositoryAndStars(
    repository: String,
    firstHundredStars: Int
  )
  /** RepositoryAndStars companion object defining a [[Decoder]] instance */
  object RepositoryAndStars {
    implicit val decoder: Decoder[RepositoryAndStars] = Decoder.instance { c =>
      for {
        name <- c.get[String]("name")
        firstHundredStars <- c.downField("stargazers").get[Int]("totalCount")
      } yield RepositoryAndStars(name, firstHundredStars)
    }
  }
  /**
   * Case class modeling github organization repositories information as returned by the github
   * graphql API
   * @param repositoriesAndStars list of github repositories (and accompanying number of stars <=
   * 100) of a specific organization
   * @param endCursor an optional cursor to the next page of data
   * @param hasNextPage whether there is a next page of data
   */
  final case class OrgRepositoriesInfo(
    repositoriesAndStars: List[RepositoryAndStars],
    endCursor: Option[String],
    hasNextPage: Boolean
  ) extends PageInfo
  /** OrgRepositoriesInfo companion object defining [[Decoder]], [[Eq]] and [[Monoid]] instances */
  object OrgRepositoriesInfo {
    implicit val decoder: Decoder[OrgRepositoriesInfo] = Decoder.instance { c =>
      for {
        repositoriesAndStars <- c.get[List[RepositoryAndStars]]("nodes")
        pageInfoCursor = c.downField("pageInfo")
        endCursor <- pageInfoCursor.get[Option[String]]("endCursor")
        hasNextPage <- pageInfoCursor.get[Boolean]("hasNextPage")
      } yield OrgRepositoriesInfo(repositoriesAndStars, endCursor, hasNextPage)
    }.prepare(_.downField("data").downField("organization").downField("repositories"))
    implicit val orgRepositoriesInfoEq: Eq[OrgRepositoriesInfo] = Eq.fromUniversalEquals
    implicit val orgRepositoriesInfoMonoid: Monoid[OrgRepositoriesInfo] =
      new Monoid[OrgRepositoriesInfo] {
        def combine(o1: OrgRepositoriesInfo, o2: OrgRepositoriesInfo): OrgRepositoriesInfo =
          OrgRepositoriesInfo(
            o1.repositoriesAndStars |+| o2.repositoriesAndStars,
            o1.endCursor |+| o2.endCursor,
            o1.hasNextPage && o2.hasNextPage
          )
        def empty: OrgRepositoriesInfo = OrgRepositoriesInfo(List.empty, None, true)
      }
  }
}
