package com.wolenjeMerchantCore.service.util

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.wolenjeMerchantCore.core.db.postgresql.service.PostgresqlDbService.{MerchantWalletFindDbQuery, MerchantWalletRowObject}

import scala.concurrent.Future

trait WalletT {
  def sqlDbService: ActorRef

  implicit val timeout: Timeout

  protected def getMerchantWallet(userId: Int): Future[Option[MerchantWalletRowObject]] = {
    (sqlDbService ? MerchantWalletFindDbQuery(userId)).mapTo[Option[MerchantWalletRowObject]]
  }
}
