package com.wolenjeMerchantCore.web

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}
import akka.actor.{ActorRefFactory, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.pattern.ask
import akka.util.Timeout
import akka.stream.Materializer
import com.wolenjeMerchantCore.core.config.WolenjeConfig
import com.wolenjeMerchantCore.core.db.postgresql.service.PostgresqlDbService
import PostgresqlDbService.{AuthFetchDbQuery, AuthRowObject}

object Main extends App {

  implicit val system       = ActorSystem()
  implicit val materializer = Materializer
  implicit val timeout      = Timeout(WolenjeConfig.httpRequestTimeout)

  val sqlDbService = system.actorOf(Props[PostgresqlDbService])
  
  (sqlDbService ? PostgresqlDbService.AuthFetchDbQuery)
  .mapTo[List[AuthRowObject]] onComplete {
    case Success(x: List[AuthRowObject]) =>
      Try(Http().bindAndHandle(
        handler   = new WolenjeWebServiceT {
          override def actorRefFactory: ActorRefFactory = system
          override def authList = x
        }.route,
        interface = WolenjeConfig.webInterface,
        port      = WolenjeConfig.webPort
      ))

    case x => println(s"Could not start service. Error response from initializer: $x")
  }

}