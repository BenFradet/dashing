package dashing.client

import org.scalajs.dom.ext.Ajax

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.JSON

import model.{Repo, DataPoint}

object Api {

  def fetchHeroRepoStars: Future[Repo] =
    Ajax.get("/api/stars/hero-repo")
      .map(xhr => JSON.parse(xhr.responseText).asInstanceOf[Repo])

  def fetchTopNStars: Future[List[Repo]] =
    Ajax.get("/api/stars/top-n")
      .map(xhr => JSON.parse(xhr.responseText).asInstanceOf[js.Array[Repo]])
      .map(_.toList)

  def fetchPRs: Future[List[DataPoint]] =
    Ajax.get("/api/prs")
      .map(xhr => JSON.parse(xhr.responseText).asInstanceOf[js.Array[DataPoint]])
      .map(_.toList)
}
