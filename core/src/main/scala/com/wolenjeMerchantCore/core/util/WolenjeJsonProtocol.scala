package com.wolenjeMerchantCore.core.util

import com.wolenjeMerchantCore.core.db.postgresql.service.PostgresqlDbService.TransactionRowObject
import com.wolenjeMerchantCore.core.util.WolenjeEnum.{Provider, RequestType, TransactionStatus, TransactionType}
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, RootJsonFormat}

object WolenjeJsonProtocol extends DefaultJsonProtocol {
  implicit def enumFormat[T <: Enumeration](implicit enu: T): RootJsonFormat[T#Value] =
    new RootJsonFormat[T#Value] {
      def write(obj: T#Value): JsValue = JsString(obj.toString)
      def read(json: JsValue): T#Value = {
        json match {
          case JsString(txt) => enu.withName(txt)
          case somethingElse => throw DeserializationException(s"Expected a value from enum $enu instead of $somethingElse")
        }
      }
    }

  implicit val requestTypeFormat    = enumFormat(RequestType)
  implicit val providerEnum          = enumFormat(Provider)
  implicit val statusJsonFormat      = enumFormat(TransactionStatus)
  implicit val typeJsonFormat        = enumFormat(TransactionType)
  implicit val transactionJsonFormat = jsonFormat10(TransactionRowObject)
}
