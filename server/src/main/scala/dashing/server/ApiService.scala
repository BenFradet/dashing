package dashing.server

import cats.effect.IO
import github4s.Github
import github4s.Github._
import github4s.GithubResponses._
import github4s.cats.effect.jvm.Implicits._
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl._
import scalaj.http.HttpResponse

object ApiService extends Service {

  type Repo = String
  type Organization = String

  val gh = Github(sys.env.get("GITHUB4S_ACCESS_TOKEN"))
  val org: Organization = "snowplow"

  override val service = HttpService[IO] {
    case GET -> Root / "stars" / repo =>
      getStars(org, repo)
        .flatMap(_.fold(ex => NotFound(ex.getMessage), m => Ok(m.asJson.noSpaces)))
  }

  def getRepos(org: Organization): IO[Either[GHException, List[Repo]]] = for {
    r <- gh.repos.listOrgRepos(org, Some("sources")).exec[IO, HttpResponse[String]]()
    l = r.map(_.result.map(_.name))
  } yield l

  def getStars(org: Organization, repo: Repo): IO[Either[GHException, Map[String, Int]]] = for {
    r <- gh.activities.listStargazers(org, repo, true).exec[IO, HttpResponse[String]]()
    d = (for {
      res <- r
      stars = res.result
        .map(_.starred_at)
        .flatten
        .sorted
        .foldLeft((Map.empty[String, Int], 0)) {
          case ((m, c), dt) =>
            val cnt = m.getOrElse(dt, 1) + c
            (m + (dt -> cnt), cnt)
        }._1
    } yield stars)
  } yield d
}