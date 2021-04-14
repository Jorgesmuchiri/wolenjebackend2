package com.wolenjeMerchantCore.core
package db.redis

import com.redis.RedisClient
import com.wolenjeMerchantCore.core.config.WolenjeConfig
import scala.concurrent.duration.FiniteDuration

object WolenjeRedisClient {

  lazy private val client = new RedisClient(WolenjeConfig.redisInterface, WolenjeConfig.redisPort)

  def addElement(key: String, value: String, lifetime: FiniteDuration): Boolean = {
    (client.set(key, value), client.expire(key, lifetime.toSeconds.toInt)) match {
      case (true, _) => true
      case         _ => false
    }
  }

  def fetchElement(key: String): Option[String] = client.get(key)
}
