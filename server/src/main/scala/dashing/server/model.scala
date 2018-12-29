package dashing.server

import scala.concurrent.duration.FiniteDuration

import cats.Monoid
import cats.instances.all._
import cats.syntax.semigroup._

object model {
  final case class DashingConfig(
    ghToken: String,
    orgs: List[String],
    heroRepo: String,
    topNRepos: Int,
    cacheDuration: FiniteDuration,
    host: String,
    port: Int
  )

  final case class Quarter(year: Int, quarter: Int) {
    override def toString: String = s"Q$quarter $year"
  }

  final case class GHObject(author: String, created: String)

  final case class Repo(name: String, starsTimeline: Map[String, Double], stars: Int)
  object Repo {
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
  final case class Repos(repos: List[Repo])
}
