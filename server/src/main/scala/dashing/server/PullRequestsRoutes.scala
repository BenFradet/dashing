package dashing.server

import scala.concurrent.duration.FiniteDuration

import cats.Monoid
import cats.effect.{Clock, Effect, Sync, Timer}
import cats.implicits._
import io.chrisdavenport.mules.Cache
import io.circe.syntax._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

import model._
import Parallel1.parallelFromParallel1

class PullRequestsRoutes[F[_]: Effect: Timer: Parallel1](
  implicit C: YearMonthClock[F]
) extends Http4sDsl[F] {
  import PullRequestsRoutes._

  /**
   * Pull request routes, exposing:
   * - /prs-monthly, for pull requests opened by non members grouped monthly
   * - /prs-quarterly, for pull requests opened by non members grouped quarterly
   * @param cache for already requested pull request information
   * @param graphQL to interact with the github graphql API
   * @param config pull request dashboards configuration
   * @return http routes
   */
  def routes(
    cache: Cache[F, String, PageInfo],
    graphQL: GraphQL[F],
    config: PRDashboardsConfig,
  ): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case GET -> Root / "prs-monthly" => for {
        prs <-
          getMonthlyPRs(cache, graphQL, config.orgs, config.lookback, config.peopleToIgnore.toSet)
            .map(_.asJson.noSpaces)
            .attempt
        res <- prs.fold(ex => InternalServerError(ex.getMessage), j => Ok(j))
      } yield res
      case GET -> Root / "prs-quarterly" => for {
        prs <-
          getQuarterlyPRs(cache, graphQL, config.orgs, config.lookback, config.peopleToIgnore.toSet)
            .map(_.asJson.noSpaces)
            .attempt
        res <- prs.fold(ex => InternalServerError(ex.getMessage), j => Ok(j))
      } yield res
    }
}

object PullRequestsRoutes {
  /**
   * Get pull requests grouped by month
   * @param cache for already requested pull request information
   * @param graphQL to interact with the github graphql API
   * @param orgs list of github organizations to retrieve the pull requests from
   * @param lookback how far in time to look back for pull requests
   * @param peopleToIgnore set of github logins to ignore when computing pull request counts
   * @return a map of github organization to a map of month to number of pull requests in F
   */
  def getMonthlyPRs[F[_]: Sync: Clock: Parallel1](
    cache: Cache[F, String, PageInfo],
    graphQL: GraphQL[F],
    orgs: List[String],
    lookback: FiniteDuration,
    peopleToIgnore: Set[String],
  )(implicit C: YearMonthClock[F]): F[Map[String, Map[String, Double]]] = orgs.parTraverse { org =>
    for {
      prs <- utils.lookupOrInsert(cache)(s"prs-$org", getPRs(cache, graphQL, org, peopleToIgnore))
      monthlyPRs <- utils.computeMonthlyTimeline[F](
        prs.pullRequests.map(_.timestamp.take(7)), lookback)
    } yield org -> monthlyPRs
  }.map(_.toMap)

  /**
   * Get pull requests grouped by quarter
   * @param cache for already requested pull request information
   * @param graphQL to interact with the github graphql API
   * @param orgs list of github organizations to retrieve the pull requests from
   * @param lookback how far in time to look back for pull requests
   * @param peopleToIgnore set of github logins to ignore when computing pull request counts
   * @return a map of github organization to a map of quarter to number of pull requests in F
   */
  def getQuarterlyPRs[F[_]: Sync: Clock: Parallel1](
    cache: Cache[F, String, PageInfo],
    graphQL: GraphQL[F],
    orgs: List[String],
    lookback: FiniteDuration,
    peopleToIgnore: Set[String],
  )(implicit C: YearMonthClock[F]): F[Map[String, Map[String, Double]]] = orgs.parTraverse { org =>
    for {
      prs <- utils.lookupOrInsert(cache)(s"prs-$org", getPRs(cache, graphQL, org, peopleToIgnore))
      monthlyPRs <- utils
        .computeQuarterlyTimeline[F](prs.pullRequests.map(_.timestamp.take(7)), lookback)
    } yield org -> monthlyPRs
  }.map(_.toMap)

  /**
   * Get pull requests for an organization by traversing every repositories for an organization
   * @param cache for already requested pull request information
   * @param graphQL to interact with the github graphql API
   * @param org github organization to retrieve the pull requests from
   * @param peopleToIgnore set of github logins to ignore when computing pull request counts
   * @return pull requests information for the specified organization in F
   */
  def getPRs[F[_]: Sync: Clock: Parallel1](
    cache: Cache[F, String, PageInfo],
    graphQL: GraphQL[F],
    org: String,
    peopleToIgnore: Set[String],
  ): F[PullRequestsInfo] = for {
    repos <- utils.lookupOrInsert(cache)(s"repos-$org", graphQL.getOrgRepositories(org))
    prs <- repos.repositoriesAndStars.parTraverse { rs =>
      utils.lookupOrInsert(cache)(s"prs-$org-${rs.repository}", graphQL.getPRs(org, rs.repository))
    }
    members <- utils.lookupOrInsert(cache)(s"members-$org", graphQL.getOrgMembers(org))
    allPRs = Monoid.combineAll(prs)
    filteredPRs = allPRs.pullRequests.filterNot { pr =>
      pr.author
        .map(a => members.members.toSet.contains(a) || peopleToIgnore.contains(a))
        .getOrElse(false)
    }
  } yield PullRequestsInfo(filteredPRs, None, false)
}
