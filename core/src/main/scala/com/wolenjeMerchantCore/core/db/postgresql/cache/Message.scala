package com.wolenjeMerchantCore.core.db.postgresql.cache

import spray.json.DefaultJsonProtocol

object Message extends DefaultJsonProtocol {
  case class CurrencyGatewayResponse(
    rates: Map[String, CurrencyGatewayEntry],
    code: Int
  )

  case class CurrencyGatewayEntry(
    rate: BigDecimal,
    timestamp: Long
  )

  implicit val currencyEntry           = jsonFormat2(CurrencyGatewayEntry)
  implicit val currencyGatewayResponse = jsonFormat2(CurrencyGatewayResponse)
}
