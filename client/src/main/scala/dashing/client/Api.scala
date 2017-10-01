package dashing.client

import org.scalajs.dom.ext.Ajax

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.JSON

object Api {

  def fetchStars(repo: String): Future[Map[String, Int]] =
    Ajax.get(starsUrl(repo))
      .map(xhr => JSON.parse(xhr.responseText).asInstanceOf[js.Dictionary[Int]].toMap)

  val starsUrl = (repo: String) => s"/api/stars/$repo"
}