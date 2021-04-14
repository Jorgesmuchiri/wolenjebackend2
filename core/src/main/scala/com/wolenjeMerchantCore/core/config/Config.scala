package com.wolenjeMerchantCore.core.config

import com.typesafe.config.ConfigFactory
import com.wolenjeMerchantCore.core.util.WolenjeUtil

object WolenjeConfig extends WolenjeConfigT

private[config] trait WolenjeConfigT {
  val config = ConfigFactory.load()
  config.checkValid(ConfigFactory.defaultReference)

  //Web=================================================================================
  val webInterface = config.getString("wolenje.web.public.interface")
  val webPort      = config.getInt("wolenje.web.public.port")

  //Mysql===============================================================================
  val mysqlInterface = config.getString("wolenje.postgresql.web-interface")
  val mysqlPort      = config.getInt("wolenje.postgresql.web-port")
  val mysqlUser      = config.getString("wolenje.postgresql.user")
  val mysqlPass      = config.getString("wolenje.postgresql.pass")
  val mysqlDbName    = config.getString("wolenje.postgresql.db-name")

  val mysqlDbPoolMaxIdle      = config.getInt("wolenje.postgresql.pool.max-idle")
  val mysqlDbPoolMaxObject    = config.getInt("wolenje.postgresql.pool.max-object")
  val mysqlDbPoolMaxQueueSize = config.getInt("wolenje.postgresql.pool.max-queue-size")

  //Redis==============================================================================
  val redisInterface = config.getString("wolenje.redis.db.interface")
  val redisPort      = config.getInt("wolenje.redis.db.port")

  //Amazon==============================================================================
  val amazonVerifiedEmail = config.getString("wolenje.amazon.email")
  val amazonAccessKeyId   = config.getString("wolenje.amazon.access-key-id")
  val amazonSignature     = config.getString("wolenje.amazon.signature")

  //Email==============================================================================
  val wolenjeEmailContactAddress = config.getString("wolenje.email.contact-address")

  //Gateway Variables===================================================================
  val sendOTPUrl         = config.getString("wolenje.internal.send-otp-url")
  val verifyOTPUrl       = config.getString("wolenje.internal.verify-otp-url")
  val tokenUrl           = config.getString("wolenje.internal.token-url")
  val disburseGatewayUrl = config.getString("wolenje.internal.disburse-gateway-url")
  val freeForexUrl       = config.getString("wolenje.freeforex.gateway-url")

  //General Configs====================================================================
  val defaultCurrency                        = config.getString("wolenje.general.default-currency")
  val exchangeCurrency                       = config.getString("wolenje.general.exchange-currency")
  val defaultMinTransactionValue: BigDecimal = config.getInt("wolenje.general.min-value")
  val defaultMaxTransactionValue: BigDecimal = config.getInt("wolenje.general.max-value")
  val mysqlMaxRetryNum                       = config.getInt("wolenje.retry-policy.mysql-max-num")
  val currencyPairs                          = config.getString("wolenje.general.currency-pairs")
  val tokenLifetime = WolenjeUtil.parseFiniteDuration(config.getString("wolenje.general.token-lifetime")).get

  //Auth===============================================================================
  val authDateExpiryMin           = config.getInt("wolenje.auth.date-expiry-min")
  val newPasswordRedisOtpPrefox   = config.getString("wolenje.auth.new-password.redis.otp-prefix")
  val newPasswordRedisOTPLifetime = WolenjeUtil.parseFiniteDuration(config.getString("wolenje.auth.new-password.redis.otp-lifetime")).get

  //Timeouts
  val httpRequestTimeout  = WolenjeUtil.parseFiniteDuration(config.getString("wolenje.timeout.http")).get
  val postgresqlDbTimeout = WolenjeUtil.parseFiniteDuration(config.getString("wolenje.timeout.postgre")).get

  //Update Freq
  val currencyUpdateFrequency        = WolenjeUtil.parseFiniteDuration(config.getString("wolenje.postgresql.update-frequency.currency")).get
  val merchantUpdateFrequency        = WolenjeUtil.parseFiniteDuration(config.getString("wolenje.postgresql.update-frequency.merchant")).get
  val merchantPaymentUpdateFrequency = WolenjeUtil.parseFiniteDuration(config.getString("wolenje.postgresql.update-frequency.merchant-payment")).get
  val merchantWalletUpdateFrequency  = WolenjeUtil.parseFiniteDuration(config.getString("wolenje.postgresql.update-frequency.merchant-wallet")).get
  val userTokenUpdateFrequency       = WolenjeUtil.parseFiniteDuration(config.getString("wolenje.postgresql.update-frequency.user-token")).get

  //Firebase Cloud Message
  val firebaseServiceAccount                = config.getString("wolenje.firebase.service-account.json")
  val firebaseCloudMessageUrl               = config.getString("wolenje.firebase.cloud-message.request-url")
  val firebaseExponentialBackoffDuration    = config.getDouble("wolenje.firebase.exponential-backoff.duration")
  val firebaseExponentialBackoffMaxRetryNum = config.getInt("wolenje.firebase.exponential-backoff.max-retry-num")
}
