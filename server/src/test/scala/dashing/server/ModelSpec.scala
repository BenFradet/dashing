package dashing.server

import cats.kernel.laws.discipline.MonoidTests
import io.circe.parser._
import org.typelevel.discipline.specs2.Discipline
import org.scalacheck.{Arbitrary, Gen}
import org.specs2.Specification
import org.specs2.matcher._

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
    endCursor <- Gen.option(Gen.alphaStr)
    hasNextPage <- Gen.oneOf(true, false)
  } yield StarsInfo(timeline, endCursor, hasNextPage))
}

class PullRequestsInfoLawSpec extends Specification with Discipline { def is = {
  import PullRequestsInfoLawSpec._
  checkAll("PullRequestsInfo.MonoidLaws", MonoidTests[PullRequestsInfo].monoid)
} }
object PullRequestsInfoLawSpec {
  implicit def arbPullRequestsInfo: Arbitrary[PullRequestsInfo] = Arbitrary(for {
    author <- Gen.option(Gen.alphaStr)
    timestamp <- Gen.alphaStr
    authorAndTimestamp <- Gen.zip(author, timestamp)
      .map { case (author, timestamp) => AuthorAndTimestamp(author, timestamp) }
    pullRequests <- Gen.listOf(authorAndTimestamp)
    endCursor <- Gen.option(Gen.alphaStr)
    hasNextPage <- Gen.oneOf(true, false)
  } yield PullRequestsInfo(pullRequests, endCursor, hasNextPage))
}

class OrgMembersInfoLawSpec extends Specification with Discipline { def is = {
  import OrgMembersInfoLawSpec._
  checkAll("OrgMembersInfo.MonoidLaws", MonoidTests[OrgMembersInfo].monoid)
} }
object OrgMembersInfoLawSpec {
  implicit def arbOrgMembersInfo: Arbitrary[OrgMembersInfo] = Arbitrary(for {
    member <- Gen.alphaStr
    members <- Gen.listOf(member)
    endCursor <- Gen.option(Gen.alphaStr)
    hasNextPage <- Gen.oneOf(true, false)
  } yield OrgMembersInfo(members, endCursor, hasNextPage))
}

class OrgRepositoriesInfoLawSpec extends Specification with Discipline { def is = {
  import OrgRepositoriesInfoLawSpec._
  checkAll("OrgRepositoriesInfo.MonoidLaws", MonoidTests[OrgRepositoriesInfo].monoid)
} }
object OrgRepositoriesInfoLawSpec {
  implicit def arbOrgRepositoriesInfo: Arbitrary[OrgRepositoriesInfo] = Arbitrary(for {
    repository <- Gen.alphaStr
    stars <- Gen.chooseNum(1, 1000)
    repositoryAndStars <- Gen.zip(repository, stars)
      .map { case(repository, stars) => RepositoryAndStars(repository, stars) }
    repositoriesAndStars <- Gen.listOf(repositoryAndStars)
    endCursor <- Gen.option(Gen.alphaStr)
    hasNextPage <- Gen.oneOf(true, false)
  } yield OrgRepositoriesInfo(repositoriesAndStars, endCursor, hasNextPage))
}

class ModelSpec extends org.specs2.mutable.Specification with Matchers {
  "model" should {
    "provide a decoder for StarsInfo" in {
      decode[StarsInfo]("""
      {
        "data": {
          "repository": {
            "stargazers": {
              "edges": [
                {
                  "starredAt": "2018-09-24T17:54:55Z"
                }
              ],
              "pageInfo": {
                "endCursor": "Y3Vyc29yOnYyOpIAzghSYkc=",
                "hasNextPage": false
              }
            }
          }
        }
      }""").isRight must beTrue
    }
    "provide a decoder for AuthorAndTimestamp" in {
      decode[AuthorAndTimestamp]("""
      {
        "node": {
          "author": {
            "login": "BenFradet"
          },
          "createdAt": "2016-11-28T20:41:21Z"
        }
      }""").isRight must beTrue

      decode[AuthorAndTimestamp]("""
      {
        "node": {
          "author": null,
          "createdAt": "2016-11-28T20:41:21Z"
        }
      }""").isRight must beTrue
    }
    "provide a decoder for PullRequestsInfo" in {
      decode[PullRequestsInfo]("""
      {
        "data": {
          "repository": {
            "pullRequests": {
              "edges": [
                {
                  "node": {
                    "author": {
                      "login": "BenFradet"
                    },
                    "createdAt": "2016-11-28T20:41:21Z"
                  }
                }
              ],
              "pageInfo": {
                "endCursor": "Y3Vyc29yOnYyOpHOBbJSng==",
                "hasNextPage": false
              }
            }
          }
        }
      }""").isRight must beTrue
    }
    "provide a decoder for OrgMembersInfo" in {
      decode[OrgMembersInfo]("""
      {
        "data": {
          "organization": {
            "membersWithRole": {
              "nodes": [
                {
                  "login": "BenFradet"
                }
              ],
              "pageInfo": {
                "endCursor": "Y3Vyc29yOnYyOpHOABqB-w==",
                "hasNextPage": false
              }
            }
          }
        }
      }""").isRight must beTrue
    }
    "provide a decoder for RepositoryAndStars" in {
      decode[RepositoryAndStars]("""
      {
        "name": "igwp",
        "stargazers": {
          "totalCount": 1
        }
      }""").isRight must beTrue
    }
    "provide a decoder for OrgRepositoriesInfo" in {
      decode[OrgRepositoriesInfo]("""
      {
        "data": {
          "organization": {
            "repositories": {
              "nodes": [
                {
                  "name": "igwp",
                  "stargazers": {
                    "totalCount": 1
                  }
                }
              ],
              "pageInfo": {
                "endCursor": "NA",
                "hasNextPage": false
              }
            }
          }
        }
      }""").isRight must beTrue
    }
  }
}
