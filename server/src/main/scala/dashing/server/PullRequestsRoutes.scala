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

class PullRequestsRoutes[F[_]: Effect: Timer: Parallel1] extends Http4sDsl[F] {
  import PullRequestsRoutes._

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
  def getMonthlyPRs[F[_]: Sync: Clock: Parallel1](
    cache: Cache[F, String, PageInfo],
    graphQL: GraphQL[F],
    orgs: List[String],
    lookback: FiniteDuration,
    peopleToIgnore: Set[String],
  ): F[Map[String, Map[String, Double]]] = orgs.parTraverse { org =>
    for {
      prs <- utils.lookupOrInsert(cache)(s"prs-$org", getPRs(cache, graphQL, org, peopleToIgnore))
      monthlyPRs = utils.computeMonthlyTimeline(prs.pullRequests.map(_.timestamp.take(7)), lookback)
    } yield org -> monthlyPRs
  }.map(_.toMap)

  def getQuarterlyPRs[F[_]: Sync: Clock: Parallel1](
    cache: Cache[F, String, PageInfo],
    graphQL: GraphQL[F],
    orgs: List[String],
    lookback: FiniteDuration,
    peopleToIgnore: Set[String],
  ): F[Map[String, Map[String, Double]]] = orgs.parTraverse { org =>
    for {
      prs <- utils.lookupOrInsert(cache)(s"prs-$org", getPRs(cache, graphQL, org, peopleToIgnore))
      monthlyPRs = utils
        .computeQuarterlyTimeline(prs.pullRequests.map(_.timestamp.take(7)), lookback)
    } yield org -> monthlyPRs
  }.map(_.toMap)

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
