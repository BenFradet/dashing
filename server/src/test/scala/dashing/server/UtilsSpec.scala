package dashing.server

import java.time.YearMonth

import cats.effect.IO
import github4s.Github
import org.http4s.testing.Http4sMatchers
import org.specs2.mutable.Specification

import model._

class UtilsSpec extends Specification with Http4sMatchers {

  val gh = Github(sys.env.get("GITHUB4S_ACCESS_TOKEN"))

  "utils.getRepos" should {
    "retrieve the list of repos in an org" in {
      utils.getRepos[IO](gh, "igwp").map(_.size) must returnRight(4)
    }
    "be a left if the org doesn't exist" in {
      utils.getRepos[IO](gh, "notexist")
        .leftMap(_.getMessage.take(10)) must returnLeft("Failed inv")
    }
  }

  "utils.getOrgMembers" should {
    "retrieve the list of repos in an org" in {
      utils.getOrgMembers[IO](gh, "igwp").map(_.size) must returnRight(1)
    }
    "be a left if the org doesn't exist" in {
      utils.getOrgMembers[IO](gh, "notexist")
        .leftMap(_.getMessage.take(10)) must returnLeft("Failed inv")
    }
  }

  "utils.computeMonthlyTimeline" should {
    "compute a monthly cumulative count" in {
      utils.computeMonthlyTimeline(List("2018-01", "2018-05")) must_== List(
        DataPoint("2018-01", 1d),
        DataPoint("2018-02", 1d),
        DataPoint("2018-03", 1d),
        DataPoint("2018-04", 1d),
        DataPoint("2018-05", 2d)
      ) -> 2
    }
    "return an empty list if the timeline doesn't contain YYYY-MM" in {
      utils.computeMonthlyTimeline(List("test", "test2")) must_== Nil -> 0d
    }
  }

  "utils.getSuccessiveMonths" should {
    "list all successive months between two dates" in {
      utils.getSuccessiveMonths(YearMonth.of(2014, 5), YearMonth.of(2014, 6)) must_==
        List(YearMonth.of(2014, 5), YearMonth.of(2014, 6))
    }
    "list all successive months if they aren't in chronological header" in {
      utils.getSuccessiveMonths(YearMonth.of(2014, 6), YearMonth.of(2014, 5)) must_==
        List(YearMonth.of(2014, 5), YearMonth.of(2014, 6))
    }
    "give back a list of one if both months are the same" in {
      val ym = YearMonth.of(2014, 6)
      utils.getSuccessiveMonths(ym, ym) must_== List(ym)
    }
  }

  "utils.getNrPages" should {
    "retrieve the correct number of pages" in {
      utils.getNrPages(Map("Link" ->
        Seq("""<http://github.com?per_page=100&page=20>; rel="last""""))) must_== Some(20)
    }
    "none if no Link headers" in {
      utils.getNrPages(Map("H" -> Seq("abc"))) must_== None
    }
    "none if the Link head doesn't match the regex" in {
      utils.getNrPages(Map("Link" ->
        Seq("""http://github.com?per_page=100&page=20; rel="last""""))) must_== None
    }
    "none if there is not last relation" in {
      utils.getNrPages(Map("Link" ->
        Seq("""<http://github.com?per_page=100&page=20>; rel="next""""))) must_== None
    }
    "none if the link is not a properly formed url" in {
      utils.getNrPages(Map("Link" -> Seq("""<abc>; rel="last""""))) must_== None
    }
    "none if the url doesn't contain a page query param" in {
      utils.getNrPages(Map("Link" ->
        Seq("""<http://github.com?per_page=100>; rel="last""""))) must_== None
    }
    "none if the page query param is not an int" in {
      utils.getNrPages(Map("Link" ->
        Seq("""<http://github.com?per_page=100&page=abc>; rel="last""""))) must_== None
    }
  }
}
