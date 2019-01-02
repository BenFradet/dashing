package dashing.server

import cats.kernel.laws.discipline.MonoidTests
import org.typelevel.discipline.specs2.Discipline
import org.scalacheck.{Arbitrary, Gen}
import org.specs2.Specification

import model.Repo

class RepoLawSpec extends Specification with Discipline { def is = {
    import RepoLawSpec._
    checkAll("Repo.MonoidLaws", MonoidTests[Repo].monoid)
  }
}

object RepoLawSpec {
  implicit def arbFoo: Arbitrary[Repo] = Arbitrary(for {
    name <- Gen.alphaStr
    timeline <- Gen.mapOf(Gen.zip(Gen.alphaStr, Gen.chooseNum(0d, 100d)))
    stars <- Gen.chooseNum(1, 1000)
  } yield Repo(name, timeline, stars))
}
