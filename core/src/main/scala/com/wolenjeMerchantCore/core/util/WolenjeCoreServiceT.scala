package com.wolenjeMerchantCore.core.util

import akka.actor.{ActorRefFactory, Props}
import com.wolenjeMerchantCore.core.db.postgresql.cache.{MerchantDbCache}

trait WolenjeCoreServiceT {
  def actorRefFactory: ActorRefFactory

  actorRefFactory.actorOf(Props[MerchantDbCache])
}
