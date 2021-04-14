package com.wolenjeMerchantCore.core.db.postgresql.cache

import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.wolenjeMerchantCore.core.config.WolenjeConfig
import com.wolenjeMerchantCore.core.db.postgresql.service.PostgresqlDbService
import com.wolenjeMerchantCore.core.db.postgresql.service.PostgresqlDbService.{AuthRowObject, AuthFetchDbQuery}
import com.wolenjeMerchantCore.core.util.WolenjeUtil.UpdateCacheRequest

import scala.util.{Failure, Success}

object AuthDbCache extends AuthDbCacheT

trait AuthDbCacheT {

  def exists(apiKey: String): Boolean = entriesMap.contains(apiKey)

  private var entriesMap:Map[String, AuthRowObject] = Map()

  def setEntries(x: List[AuthRowObject]) = {
    entriesMap = x.foldLeft(Map[String, AuthRowObject]()) {
      case (map, entry) =>
        map.updated(entry.apiKey, entry)
    }
  }
}

class AuthDbCache extends Actor with ActorLogging {

  implicit val timeout = Timeout(WolenjeConfig.postgresqlDbTimeout)
  val mysqlDbService   = context.actorOf(Props[PostgresqlDbService])

  override def preStart() {
    self ! UpdateCacheRequest
  }

  override def receive = {
    case UpdateCacheRequest =>
      (mysqlDbService ? AuthFetchDbQuery).mapTo[List[AuthRowObject]] onComplete {
        case Failure(exception) => log.error("Error encountered: {}", Some(exception))
        case Success(entries)   => AuthDbCache.setEntries(entries)
      }
      context.system.scheduler.scheduleOnce(
        WolenjeConfig.merchantUpdateFrequency,
        self,
        UpdateCacheRequest
      )
  }
}