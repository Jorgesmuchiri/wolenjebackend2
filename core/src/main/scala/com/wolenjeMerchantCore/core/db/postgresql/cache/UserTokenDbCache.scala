package com.wolenjeMerchantCore.core.db.postgresql.cache

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import akka.actor.{ActorSystem, Props}
import akka.http.caching.scaladsl.Cache
import akka.pattern.ask
import akka.util.Timeout
import com.wolenjeMerchantCore.core.config.WolenjeConfig
import com.wolenjeMerchantCore.core.db.postgresql.service.PostgresqlDbService
import com.wolenjeMerchantCore.core.db.postgresql.service.PostgresqlDbService.{UserTokenRowObject, UserTokenFindDbQuery}

import akka.http.caching.LfuCache

trait UserTokenDbCacheT {
  implicit val system: ActorSystem
  implicit val timeout: Timeout
  
  def sqlDbService       = system.actorOf(Props[PostgresqlDbService])
  def TOKEN_KEY(id: Int) = "token:"

  val cache = LfuCache[String, Option[UserTokenRowObject]]
  
  def findByUserId(userId: Int): Future[Option[UserTokenRowObject]] = cache.getOrLoad(TOKEN_KEY(userId), _ => {
    (sqlDbService ? UserTokenFindDbQuery(userId)).mapTo[Option[UserTokenRowObject]]
  })
}