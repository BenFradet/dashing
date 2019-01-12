package dashing.server

import scala.concurrent.duration._

import cats.data.EitherT
import cats.effect.{Effect, Sync, Timer}
import cats.implicits._
import github4s.Github
import github4s.Github._
import github4s.GithubResponses._
import github4s.free.domain._
import github4s.cats.effect.jvm.Implicits._
import io.chrisdavenport.mules.Cache
import io.circe.syntax._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import scalaj.http.HttpResponse

import model.{GHObject, PRDashboardsConfig}

class PullRequestsRoutes[F[_]: Effect: Timer] extends Http4sDsl[F] {
  import PullRequestsRoutes._

  def routes(
    cache: Cache[F, String, String],
    token: String,
    config: PRDashboardsConfig,
  ): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case GET -> Root / "prs-quarterly" => for {
        prs <- utils.lookupOrInsert(cache)("prs-quarterly",
          getPRsForOrgs(Github(Some(token)), config.orgs,
            getQuarterlyPRs(_, _, config.lookback, config.peopleToIgnore.toSet))
              .value.map(_.map(_.asJson.noSpaces)))
        res <- prs.fold(ex => NotFound(ex.getMessage), t => Ok(t))
      } yield res
      case GET -> Root / "prs-monthly" => for {
        prs <- utils.lookupOrInsert(cache)("prs-monthly",
          getPRsForOrgs(Github(Some(token)), config.orgs,
            getMonthlyPRs(_, _, config.lookback, config.peopleToIgnore.toSet))
              .value.map(_.map(_.asJson.noSpaces)))
        res <- prs.fold(ex => NotFound(ex.getMessage), t => Ok(t))
      } yield res
    }
}

object PullRequestsRoutes {

  def getPRsForOrgs[F[_]: Sync](
    gh: Github,
    orgs: List[String],
    byOrg: (Github, String) => EitherT[F, GHException, Map[String, Double]]
  ): EitherT[F, GHException, Map[String, Map[String, Double]]] =
    orgs.map(o => (EitherT.rightT[F, GHException](o), byOrg(gh, o)).tupled)
      .sequence
      .map(_.toMap)

  def getMonthlyPRs[F[_]: Sync](
    gh: Github,
    org: String,
    lookback: FiniteDuration,
    peopleToIgnore: Set[String],
  ): EitherT[F, GHException, Map[String, Double]] = for {
    prs <- getPRs(gh, org, peopleToIgnore)
    monthlyPRs = utils.computeMonthlyTimeline(prs.map(_.created.take(7)), lookback)
  } yield monthlyPRs

  def getQuarterlyPRs[F[_]: Sync](
    gh: Github,
    org: String,
    lookback: FiniteDuration,
    peopleToIgnore: Set[String],
  ): EitherT[F, GHException, Map[String, Double]] =
    for {
      prs <- getPRs(gh, org, peopleToIgnore)
      quarterlyPRs = utils.computeQuarterlyTimeline(prs.map(_.created.take(7)), lookback)
    } yield quarterlyPRs

  def getPRs[F[_]: Sync](
    gh: Github,
    org: String,
    peopleToIgnore: Set[String],
  ): EitherT[F, GHException, List[GHObject]] = for {
    repos <- utils.getRepos[F](gh, org)
    repoNames = repos.map(_.name)
    prs <- getPRs(gh, org, repoNames)
    members <- utils.getOrgMembers[F](gh, org)
    prsByNonMember = prs.filterNot(pr =>
      members.toSet.contains(pr.author) || peopleToIgnore.toSet.contains(pr.author))
    _ = println(prsByNonMember)
  } yield prsByNonMember

  def getPRs[F[_]: Sync](
    gh: Github,
    org: String,
    repoNames: List[String]
  ): EitherT[F, GHException, List[GHObject]] = for {
    nested <- repoNames.traverse(getPRs(gh, org, _))
    flattened = nested.flatten
  } yield flattened

  def getPRs[F[_]: Sync](
      gh: Github, org: String, repoName: String): EitherT[F, GHException, List[GHObject]] =
    for {
      prs <- utils.autoPaginate(p => listPRs(gh, org, repoName, Some(p)))
      pullRequests = prs
        .map(pr => (pr.user.map(_.login), pr.created_at.some).bisequence)
        .flatten
        .map(pr => GHObject(pr._1, pr._2))
    } yield pullRequests

  def listPRs[F[_]: Sync](
    gh: Github,
    org: String,
    repoName: String,
    page: Option[Pagination]
  ): F[Either[GHException, GHResult[List[PullRequest]]]] =
    gh.pullRequests.list(org, repoName, List(PRFilterAll), page)
      .exec[F, HttpResponse[String]]()
}
