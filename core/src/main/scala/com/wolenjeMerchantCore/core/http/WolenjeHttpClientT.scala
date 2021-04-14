package com.wolenjeMerchantCore.core.http

import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.{Actor, ActorLogging}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, StatusCode}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer

trait WolenjeHttpClientT extends Actor with ActorLogging {

  case class HttpResponse(
    status: StatusCode,
    data: String
  )

  implicit val system = context.system
  implicit val mat    = ActorMaterializer()
  def sendHttpRequest(req: HttpRequest) = for {
    response <- Http().singleRequest(req)
    data     <- Unmarshal(response.entity).to[String]
  } yield HttpResponse (
    status = response.status,
    data   = data
  )
}
