package dashing.server

import cats.Monoid
import cats.instances.all._
import cats.syntax.semigroup._
import github4s.free.domain.Stargazer

object model {
  final case class Repo(name: String, starsTimeline: Map[String, Int], stars: Int)

  object Repo {
    def apply(repoName: String, stargazers: List[Stargazer]): Repo = {
      val (starsTimeline, stars) = stargazers
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
      Repo(repoName, starsTimeline, stars)
    }

    implicit val repoMonoid: Monoid[Repo] = new Monoid[Repo] {
      def combine(r1: Repo, r2: Repo): Repo =
        Repo(r1.name, r1.starsTimeline |+| r2.starsTimeline, r1.stars |+| r2.stars)

      def empty: Repo = Repo("", Map.empty, 0)
    }
  }

  case class GHObject(author: String, created: String)
  case class DataPoint(label: String, value: Double)
  case class Timeline(members: List[DataPoint], nonMembers: List[DataPoint])
}