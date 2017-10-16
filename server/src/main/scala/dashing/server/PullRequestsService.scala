package dashing.server

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

import model.{GHObject, Timeline}

object PullRequestsService extends Service {

  // config
  val gh = Github(sys.env.get("GITHUB4S_ACCESS_TOKEN"))
  val org = "snowplow"

  override val service = HttpService[IO] {
    case GET -> Root / "prs" => getPRs(org)
      .flatMap(_.fold(ex => NotFound(ex.getMessage), t => Ok(t.asJson.noSpaces)))
  }

  def getPRs(org: String): IO[Either[GHException, Timeline]] = (for {
    repos <- EitherT(utils.getRepos(gh, org))
    repoNames = repos.map(_.name)
    prs <- EitherT(getPRs(org, repoNames))
    members <- EitherT(utils.getOrgMembers(gh, org))
    (prsByMember, prsByNonMember) = prs.partition(pr => members.toSet.contains(pr.author))
    memberPRsCounted = countPRs(prsByMember)
    nonMemberPRsCounted = countPRs(prsByNonMember)
  } yield Timeline(memberPRsCounted, nonMemberPRsCounted)).value

  def countPRs(prs: List[GHObject]): Map[String, Int] = prs
    .map(_.created.take(7))
    .sorted
    .foldLeft((Map.empty[String, Int], 0)) {
      case ((m, c), month) =>
        val cnt = m.getOrElse(month, c) + 1
        (m + (month -> cnt), cnt)
    }._1

  def getPRs(org: String, repoNames: List[String]): IO[Either[GHException, List[GHObject]]] = (for {
    nested <- EitherT(
      repoNames
        .traverse(getPRs(org, _))
        .map(_.sequence)
    )
    flattened = nested.flatten
  } yield flattened).value

  def getPRs(org: String, repoName: String): IO[Either[GHException, List[GHObject]]] = (for {
    prs <- EitherT(utils.autoPaginate(p => listPRs(org, repoName, Some(p))))
    pullRequests = prs
      .map(pr => (pr.user.map(_.login), pr.created_at.some).bisequence)
      .flatten
      .map(pr => GHObject(pr._1, pr._2))
  } yield pullRequests).value

  def listPRs(
    org: String,
    repoName: String,
    page: Option[Pagination]
  ): IO[Either[GHException, GHResult[List[PullRequest]]]] =
    gh.pullRequests.list(org, repoName).exec[IO, HttpResponse[String]]()
}