package com.wolenjeMerchantCore.core.db.postgresql.cache

import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.wolenjeMerchantCore.core.config.WolenjeConfig
import com.wolenjeMerchantCore.core.db.postgresql.service.PostgresqlDbService
import com.wolenjeMerchantCore.core.db.postgresql.service.PostgresqlDbService.{MerchantRowObject, MerchantFetchDbQuery}
import com.wolenjeMerchantCore.core.util.WolenjeUtil.UpdateCacheRequest

import scala.util.{Failure, Success}

object MerchantDbCache extends MerchantDbCacheT

trait MerchantDbCacheT {

  def findEmailAddress(email: String)  = emailBasedEntriesMap.get(email)
  def findByUserId(userId: Int)        = idBasedEntriesMap.get(userId)
  def findByNumber(account: String)    = numberBasedEntriesMap.get(account)
  def findByUsername(username: String) = nameBasedEntriesMap.get(username)

  private var emailBasedEntriesMap: Map[String, MerchantRowObject] = Map()
  private var numberBasedEntriesMap:Map[String, MerchantRowObject] = Map()
  private var idBasedEntriesMap: Map[Int, MerchantRowObject]       = Map()
  private var nameBasedEntriesMap: Map[String, MerchantRowObject]  = Map()

  def setEntries(x: List[MerchantRowObject]) = {
    emailBasedEntriesMap  = x.foldLeft(Map[String /*Email*/, MerchantRowObject]()) {
      case (map, entry) =>
        map.updated(entry.emailAddress, entry)
    }
    numberBasedEntriesMap = x.foldLeft(Map[String /*Account*/, MerchantRowObject]()) {
      case (map, entry) =>
        map.updated(entry.accountNumber, entry)
    }
    idBasedEntriesMap = x.foldLeft(Map[Int /*Merchant*/, MerchantRowObject]()) {
      case (map, entry) =>
        map.updated(entry.id, entry)
    }
    nameBasedEntriesMap = x.foldLeft(Map[String /*Username*/, MerchantRowObject]()) {
      case (map, entry) =>
        map.updated(entry.name, entry)
    }
  }
}

class MerchantDbCache extends Actor with ActorLogging {

  implicit val timeout = Timeout(WolenjeConfig.postgresqlDbTimeout)
  val mysqlDbService   = context.actorOf(Props[PostgresqlDbService])

  override def preStart() {
    self ! UpdateCacheRequest
  }

  override def receive = {
    case UpdateCacheRequest =>
      (mysqlDbService ? MerchantFetchDbQuery).mapTo[List[MerchantRowObject]] onComplete {
        case Failure(exception) => log.error("Error encountered: ", Some(exception))
        case Success(entries)   => MerchantDbCache.setEntries(entries)
      }
      context.system.scheduler.scheduleOnce(
        WolenjeConfig.merchantUpdateFrequency,
        self,
        UpdateCacheRequest
      )
  }
}
