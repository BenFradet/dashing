package dashing.server

import scala.concurrent.ExecutionContext

import cats.data.EitherT
import cats.effect.{Effect, Sync, Timer}
import cats.implicits._
import github4s.Github
import github4s.Github._
import github4s.GithubResponses._
import github4s.free.domain._
import github4s.cats.effect.jvm.Implicits._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.HttpService
import org.http4s.dsl.Http4sDsl
import scalaj.http.HttpResponse

import model.{GHObject, Timeline}

class PullRequestsService[F[_]: Effect: Timer] extends Http4sDsl[F] {
  import PullRequestsService._

  def service(
    cache: Cache[F, String, String],
    token: String,
    org: String
  )(implicit ec: ExecutionContext): HttpService[F] =
    HttpService[F] {
      case GET -> Root / "prs-quarterly" => for {
        prs <- cache.lookupOrInsert("prs-quarterly",
          getQuarterlyPRs(Github(Some(token)), org).value.map(_.map(_.asJson.noSpaces)))
        res <- prs.fold(ex => NotFound(ex.getMessage), t => Ok(t))
      } yield res
      case GET -> Root / "prs-monthly" => for {
        prs <- cache.lookupOrInsert("prs-monthly",
          getMonthlyPRs(Github(Some(token)), org).value.map(_.map(_.asJson.noSpaces)))
        res <- prs.fold(ex => NotFound(ex.getMessage), t => Ok(t))
      } yield res
    }
}

object PullRequestsService {
  def getMonthlyPRs[F[_]: Sync](gh: Github, org: String): EitherT[F, GHException, Timeline] = for {
    prs <- getPRs(gh, org)
    monthlyPRs = utils.computeMonthlyTimeline(prs.map(_.created.take(7)))._1
  } yield monthlyPRs

  def getQuarterlyPRs[F[_]: Sync](gh: Github, org: String): EitherT[F, GHException, Timeline] = for {
    prs <- getPRs(gh, org)
    quarterlyPRs = utils.computeQuarterlyTimeline(prs.map(_.created.take(7)))
  } yield quarterlyPRs

  def getPRs[F[_]: Sync](gh: Github, org: String): EitherT[F, GHException, List[GHObject]] = for {
    repos <- utils.getRepos[F](gh, org)
    repoNames = repos.map(_.name)
    prs <- getPRs(gh, org, repoNames)
    members <- utils.getOrgMembers[F](gh, org)
    prsByNonMember = prs.filterNot(pr => members.toSet.contains(pr.author))
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
    gh.pullRequests.list(org, repoName, Nil, page).exec[F, HttpResponse[String]]()
}
