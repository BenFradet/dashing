package dashing.server

import cats.kernel.laws.discipline.MonoidTests
import org.typelevel.discipline.specs2.Discipline
import org.scalacheck.{Arbitrary, Gen}
import org.specs2.Specification

import model._

class RepoLawSpec extends Specification with Discipline { def is = {
  import RepoLawSpec._
  checkAll("Repo.MonoidLaws", MonoidTests[Repo].monoid)
} }
object RepoLawSpec {
  implicit def arbRepo: Arbitrary[Repo] = Arbitrary(for {
    name <- Gen.alphaStr
    timeline <- Gen.mapOf(Gen.zip(Gen.alphaStr, Gen.chooseNum(0, 100).map(_.toDouble)))
    stars <- Gen.chooseNum(1, 1000)
  } yield Repo(name, timeline, stars))
}

class StarsInfoLawSpec extends Specification with Discipline { def is = {
  import StarsInfoLawSpec._
  checkAll("StarsInfo.MonoidLaws", MonoidTests[StarsInfo].monoid)
} }
object StarsInfoLawSpec {
  implicit def arbStarsInfo: Arbitrary[StarsInfo] = Arbitrary(for {
    timestamp <- Gen.alphaStr
    timeline <- Gen.listOf(timestamp)
    endCursor <- Gen.alphaStr
    hasNextPage <- Gen.oneOf(true, false)
  } yield StarsInfo(timeline, endCursor, hasNextPage))
}

class PullRequestsInfoLawSpec extends Specification with Discipline { def is = {
  import PullRequestsInfoLawSpec._
  checkAll("PullRequestsInfo.MonoidLaws", MonoidTests[PullRequestsInfo].monoid)
} }
object PullRequestsInfoLawSpec {
  implicit def arbPullRequestsInfo: Arbitrary[PullRequestsInfo] = Arbitrary(for {
    author <- Gen.alphaStr
    timestamp <- Gen.alphaStr
    authorAndTimestamp <- Gen.zip(author, timestamp)
      .map { case (author, timestamp) => AuthorAndTimestamp(author, timestamp) }
    pullRequests <- Gen.listOf(authorAndTimestamp)
    endCursor <- Gen.alphaStr
    hasNextPage <- Gen.oneOf(true, false)
  } yield PullRequestsInfo(pullRequests, endCursor, hasNextPage))
}
