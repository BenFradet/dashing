package dashing.server

import scala.concurrent.ExecutionContext.Implicits.global

import cats.effect.{ContextShift, IO}
import org.http4s.client.JavaNetClientBuilder
import org.http4s.testing.IOMatchers
import org.specs2.mutable.Specification

class GraphQLSpec extends Specification with IOMatchers {
  args(skipAll = sys.env.get("GITHUB_ACCESS_TOKEN").isEmpty)

  implicit val timer = IO.timer(global)
  implicit val cs: ContextShift[IO] = IO.contextShift(global)

  val client = JavaNetClientBuilder[IO](global).create
  val graphQL = new GraphQL(client, sys.env.getOrElse("GITHUB_ACCESS_TOKEN", ""))

  "GraphQL" should {
    "get all PRs of a particular repo with pagination" in {
      val prs = graphQL.getPRs("snowplow", "snowplow").unsafeRunSync
      prs.size must be_>(100)
    }

    "list all stargazers of a particular repo with pagination" in {
      val stars = graphQL.listStargazers("snowplow", "snowplow").unsafeRunSync
      stars.size must be_>(100)
    }

    "get org members of a particular org with pagination" in {
      val members = graphQL.getOrgMembers("snowplow").unsafeRunSync
      members.size must be_>(10)
    }
  }
}
