package com.wolenjeMerchantCore.web.marshalling

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.wolenjeMerchantCore.core.query.QueryService.{TransactionFetchQueryResponse, WalletBalanceQueryResponse}
import com.wolenjeMerchantCore.service.merchant.MerchantRequestService._
import com.wolenjeMerchantCore.service.payment.notification.PaymentNotificationService.PaymentNotificationRequest
import com.wolenjeMerchantCore.service.payment.request.PaymentRequestService._
import com.wolenjeMerchantCore.service.payment.schedule.PaymentScheduleService.PaymentConfirmationEntity
import com.wolenjeMerchantCore.web.Auth.{LoginRequest, LoginResponse}
import spray.json.{DefaultJsonProtocol, JsNumber, JsObject, JsString, JsValue, RootJsonFormat}

import com.wolenjeMerchantCore.core.util.WolenjeJsonProtocol._

trait WebJsonSupportT extends DefaultJsonProtocol with SprayJsonSupport {

  implicit val loginRequestFormat  = jsonFormat2(LoginRequest)
  implicit val loginResponseFormat = jsonFormat2(LoginResponse)

  implicit val notificationRequestFormat = jsonFormat4(PaymentNotificationRequest)

  implicit val newMerchantRequestFormat     = jsonFormat4(MerchantNewRequest)
  implicit val merchantVerifyRequestFormat  = jsonFormat3(MerchantVerifyRequest)
  implicit val merchantPasswordOTPFormat    = jsonFormat1(MerchantPasswordOTPRequest)
  implicit val merchantNewPasswordFormat    = jsonFormat3(MerchantNewPasswordRequest)

  implicit val merchantRequestResponseFormat = jsonFormat2(MerchantRequestResponse)

  implicit val paymentDeleteRequestFormat  = jsonFormat2(PaymentDeleteRequest)
  implicit val paymentEditRequestFormat    = jsonFormat8(PaymentEditRequest)
  implicit val paymentUpdateResponseFormat = jsonFormat2(PaymentUpdateResponse)


  implicit val merchantWalletResponseFormat = jsonFormat3(WalletBalanceQueryResponse)

  implicit val transactionResponseFormat = jsonFormat2(TransactionFetchQueryResponse)

  implicit val paymentFormat          = jsonFormat7(PaymentRequest)
  implicit val paymentRequestFormat   = jsonFormat2(PaymentServiceRequest)
  implicit val paymentResponseFormat  = jsonFormat2(PaymentServiceResponse)

  implicit val userTokenCreateRequestFormat  = jsonFormat2(UserTokenCreateRequest)
  implicit val UserTokenCreateResponseFormat = jsonFormat2(UserTokenCreateResponse)

  implicit object PaymentConfirmationEntity extends RootJsonFormat[PaymentConfirmationEntity] {
    override def read(json: JsValue): PaymentConfirmationEntity = {
      val result = json.asJsObject
        new PaymentConfirmationEntity(
          service_id        = result.getFields("service_id").head.toString.replace("\"", ""),
          created_on        = result.getFields("created_on").head.toString.replace("\"", ""),
          created_by        = result.getFields("created_by").head.toString.replace("\"", ""),
          created_by_id     = result.getFields("created_by_id").head.toString.replace("\"", ""),
          trx_id            = result.getFields("trx_id").head.toString.replace("\"", ""),
          req_id            = result.getFields("req_id").head.toString.replace("\"", ""),
          auth_ac_id        = result.getFields("auth_ac_id").head.toString.replace("\"", ""),
          auth_ac_uname     = result.getFields("auth_ac_uname").head.toString.replace("\"", ""),
          auth_phone        = result.getFields("auth_phone").head.toString.replace("\"", ""),
          auth_email        = result.getFields("auth_email").head.toString.replace("\"", ""),
          product_id        = result.getFields("product_id").head.toString.replace("\"", ""),
          product_name      = result.getFields("product_name").head.toString.replace("\"", ""),
          product_ve        = result.getFields("product_ve").head.toString.replace("\"", ""),
          ref               = result.getFields("ref").head.toString.replace("\"", ""),
          phone             = result.getFields("phone").head.toString.replace("\"", ""),
          email             = result.getFields("email").head.toString.replace("\"", ""),
          amount            = result.getFields("amount").head.convertTo[BigDecimal],
          fee               = result.getFields("fee").head.convertTo[BigDecimal],
          narration         = result.getFields("narration").head.toString.replace("\"", ""),
          activity_count    = result.getFields("activity_count").head.toString.replace("\"", ""),
          activity_ids      = result.getFields("activity_ids").head.toString.replace("\"", ""),
          activity_id_last  = result.getFields("activity_id_last").head.toString.replace("\"", ""),
          status            = result.getFields("status").head.toString.replace("\"", ""),
          supplier_ac_id    = result.getFields("supplier_ac_id").head.toString.replace("\"", ""),
          supplier_ac_uname = result.getFields("supplier_ac_uname").head.toString.replace("\"", ""),
          receipt_id        = result.getFields("receipt_id").head.toString.replace("\"", "")
        )
    }

    override def write(entity: PaymentConfirmationEntity) = JsObject(
      "service_id"        -> JsString(entity.service_id),
      "created_on"        -> JsString(entity.created_on),
      "created_by"        -> JsString(entity.created_by),
      "created_by_id"     -> JsString(entity.created_by_id),
      "trx_id"            -> JsString(entity.trx_id),
      "req_id"            -> JsString(entity.req_id),
      "auth_ac_id"        -> JsString(entity.auth_ac_id),
      "auth_ac_uname"     -> JsString(entity.auth_ac_uname),
      "auth_phone"        -> JsString(entity.auth_phone),
      "auth_email"        -> JsString(entity.auth_email),
      "product_id"        -> JsString(entity.product_id),
      "product_name"      -> JsString(entity.product_name),
      "product_ve"        -> JsString(entity.product_ve),
      "supplier_ac_id"    -> JsString(entity.supplier_ac_id),
      "supplier_ac_uname" -> JsString(entity.supplier_ac_uname),
      "ref"               -> JsString(entity.ref),
      "phone"             -> JsString(entity.phone),
      "email"             -> JsString(entity.email),
      "amount"            -> JsNumber(entity.amount),
      "fee"               -> JsNumber(entity.fee),
      "narration"         -> JsString(entity.narration),
      "activity_count"    -> JsString(entity.activity_count),
      "activity_ids"      -> JsString(entity.activity_ids),
      "activity_id_last"  -> JsString(entity.activity_id_last),
      "receipt_id"        -> JsString(entity.receipt_id),
      "status"            -> JsString(entity.status)
    )
  }

  implicit val walletRequestFormat  = jsonFormat3(WalletTransferRequest)
  implicit val walletResponseFormat = jsonFormat2(WalletTransferResponse)
}
