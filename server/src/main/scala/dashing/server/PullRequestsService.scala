package dashing.server

import scala.concurrent.ExecutionContext

import cats.data.EitherT
import cats.effect.IO
import cats.implicits._
import github4s.Github
import github4s.Github._
import github4s.GithubResponses._
import github4s.free.domain._
import github4s.cats.effect.jvm.Implicits._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl.io._
import scalaj.http.HttpResponse

import model.{CacheEntry, GHObject, GHObjectTimeline}

object PullRequestsService {

  def service(
    cache: Cache[IO, String, CacheEntry],
    token: String,
    org: String
  )(implicit ec: ExecutionContext): HttpService[IO] =
    HttpService[IO] {
      case GET -> Root / "prs" => for {
        prs <- lookupOrInsertPRs(cache, Github(Some(token)), org)
        res <- prs.fold(ex => NotFound(ex.getMessage), t => Ok(t.asJson.noSpaces))
      } yield res
    }

  def lookupOrInsertPRs(cache: Cache[IO, String, CacheEntry], gh: Github, org: String)(implicit ec: ExecutionContext): IO[Either[GHException, GHObjectTimeline]] = for {
    cached <- cache.lookup("prs")
    timeline <- cached match {
      case Some(tl: GHObjectTimeline) => IO.pure(Right(tl))
      case _ => for {
        res <- getPRs(gh, org)
        _ <- res match {
          case Right(tl) => cache.insert("prs", tl)
          case _ => IO.pure(())
        }
      } yield res
    }
  } yield timeline

  def getPRs(gh: Github, org: String): IO[Either[GHException, GHObjectTimeline]] = (for {
    repos <- EitherT(utils.getRepos(gh, org))
    repoNames = repos.map(_.name)
    prs <- EitherT(getPRs(gh, org, repoNames))
    members <- EitherT(utils.getOrgMembers(gh, org))
    (prsByMember, prsByNonMember) = prs.partition(pr => members.toSet.contains(pr.author))
    memberPRsCounted = utils.computeTimeline(prsByMember.map(_.created.take(7)))._1
    nonMemberPRsCounted = utils.computeTimeline(prsByNonMember.map(_.created.take(7)))._1
  } yield GHObjectTimeline(memberPRsCounted, nonMemberPRsCounted)).value

  def getPRs(
    gh: Github,
    org: String,
    repoNames: List[String]
  ): IO[Either[GHException, List[GHObject]]] = (for {
    nested <- EitherT(
      repoNames
        .traverse(getPRs(gh, org, _))
        .map(_.sequence)
    )
    flattened = nested.flatten
  } yield flattened).value

  def getPRs(gh: Github, org: String, repoName: String): IO[Either[GHException, List[GHObject]]] =
    (for {
      prs <- EitherT(utils.autoPaginate(p => listPRs(gh, org, repoName, Some(p))))
      pullRequests = prs
        .map(pr => (pr.user.map(_.login), pr.created_at.some).bisequence)
        .flatten
        .map(pr => GHObject(pr._1, pr._2))
    } yield pullRequests).value

  def listPRs(
    gh: Github,
    org: String,
    repoName: String,
    page: Option[Pagination]
  ): IO[Either[GHException, GHResult[List[PullRequest]]]] =
    gh.pullRequests.list(org, repoName).exec[IO, HttpResponse[String]]()
}
