package dashing.client

import org.scalajs.dom.ext.Ajax

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js.JSON

import model.Repo

object Api {

  def fetchHeroRepoStars: Future[Repo] =
    Ajax.get("/api/stars/hero-repo")
      .map(xhr => JSON.parse(xhr.responseText).asInstanceOf[Repo])
}