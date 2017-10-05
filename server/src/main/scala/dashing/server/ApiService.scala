package dashing
package server

import cats.data.EitherT
import cats.effect.IO
import cats.implicits._
import github4s.Github
import github4s.Github._
import github4s.GithubResponses._
import github4s.free.domain.Pagination
import github4s.cats.effect.jvm.Implicits._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl._
import scalaj.http.HttpResponse

import shared._

object ApiService extends Service {

  val gh = Github(sys.env.get("GITHUB4S_ACCESS_TOKEN"))
  val org = "snowplow"
  val pagOpt = Some(Pagination(1, 1000))

  override val service = HttpService[IO] {
    case GET -> Root / "stars" =>
      getAllStars(org).flatMap(_.fold(ex => NotFound(ex.getMessage), l => Ok(l.asJson.noSpaces)))
  }

  def getAllStars(org: String): IO[Either[GHException, List[Repo]]] = (for {
    repos <- EitherT(getRepos(org))
    stars <- EitherT(getStars(org, repos))
  } yield stars).value

  def getRepos(org: String): IO[Either[GHException, List[String]]] = (for {
    repos <- EitherT(
      gh.repos.listOrgRepos(org, Some("sources"), pagOpt).exec[IO, HttpResponse[String]]())
    repoNames = repos.result.map(_.name)
  } yield repoNames).value

  def getStars(org: String, repoNames: List[String]): IO[Either[GHException, List[Repo]]] =
    repoNames.traverse(r => getStars(org, r))
      .map(_.sequence)

  def getStars(org: String, repoName: String): IO[Either[GHException, Repo]] =
    (for {
      stargazers <- EitherT(
        gh.activities.listStargazers(org, repoName, true, pagOpt).exec[IO, HttpResponse[String]]())
      stars = stargazers.result
        .map(_.starred_at)
        .flatten
        // we keep only YYYY-MM
        .map(_.take(7))
        .sorted
        .foldLeft((Map.empty[String, Int], 0)) {
          case ((m, c), month) =>
            val cnt = m.getOrElse(month, c) + 1
            (m + (month -> cnt), cnt)
        }
    } yield Repo(repoName, stars._1, stars._2)).value

  def buildRepo(repoName: String): Repo = ???
}