package dashing.client

import org.scalajs.dom.ext.Ajax

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.JSON

import model.Repo

object Api {

  def fetchHeroRepoStars: Future[Repo] =
    Ajax.get("/api/stars/hero-repo")
      .map(xhr => JSON.parse(xhr.responseText).asInstanceOf[Repo])

  def fetchTopNStars: Future[List[Repo]] =
    Ajax.get("/api/stars/top-n")
      .map(xhr => JSON.parse(xhr.responseText).asInstanceOf[js.Array[Repo]])
      .map(_.toList)

  def fetchQuarterlyPRs: Future[Map[String, Map[String, Double]]] =
    Ajax.get("/api/prs-quarterly")
      .map(xhr => JSON.parse(xhr.responseText).asInstanceOf[js.Dictionary[js.Dictionary[Double]]])
      .map(_.mapValues(_.toMap).toMap)

  def fetchMonthlyPRs: Future[Map[String, Map[String, Double]]] =
    Ajax.get("/api/prs-monthly")
      .map(xhr => JSON.parse(xhr.responseText).asInstanceOf[js.Dictionary[js.Dictionary[Double]]])
      .map(_.mapValues(_.toMap).toMap)
}
