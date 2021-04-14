package com.wolenjeMerchantCore.service.payment.schedule

import spray.json.{DefaultJsonProtocol, JsNumber, JsObject, JsString, JsValue, RootJsonFormat}

object DisbursementGatewayMessage extends DefaultJsonProtocol {
  case class DisbursementGatewayRequest(
    services: List[DisbursementGatewayEntry]
  )

  case class DisbursementGatewayEntry(
     product_name: String,
     amount: BigDecimal,
     ref: String,
     phone: String,
     req_id: String
  )
  case class DisbursementGatewayResponse(
    transactions: TransactionGatewayEntity,
    services: List[TransactionServiceEntity]
  ) {
    def isSuccessful(transactionId: String): Boolean =  {
      val result = services.filter(_.req_id == transactionId).head.status
      result == "TRX_OK" || result == "TRX_ASYNC"
    }
  }

  case class TransactionGatewayEntity(
     id: String,
     created_on: String,
     created_by: String,
     created_by_id: String,
     auth_ac_id: String,
     auth_ac_uname: String,
     auth_phone: String,
     auth_email: String,
     narration: String,
     service_tot_amount: String,
     service_tot_amount_ok: String,
     service_count: String,
     service_count_ok: String,
     service_count_sched: String,
     service_count_fail: String
  )

  case class TransactionServiceEntity(
      id: String,
      created_on: String,
      created_by: String,
      created_by_id: String,
      req_id: String,
      trx_id: String,
      auth_ac_id: String,
      auth_ac_uname: String,
      auth_phone: String,
      auth_email: String,
      product_id: String,
      product_name: String,
      product_ve: String,
      supplier_ac_id: String,
      supplier_ac_uname: String,
      ref: String,
      phone: String,
      email: String,
      amount: BigDecimal,
      fee: BigDecimal,
      narration: String,
      activity_count: String,
      activity_ids: String,
      activity_id_last: String,
      receipt_id: String,
      status: String
  )

  implicit object PaymentConfirmationEntity extends RootJsonFormat[TransactionServiceEntity] {
    override def read(json: JsValue): TransactionServiceEntity = {
      val result = json.asJsObject
      new TransactionServiceEntity(
        id                = result.getFields("id").head.toString.replace("\"", ""),
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

    override def write(entity: TransactionServiceEntity) = JsObject(
      "id"                -> JsString(entity.id),
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

  implicit val disbursementEntry    = jsonFormat5(DisbursementGatewayEntry)
  implicit val disbursementRequest  = jsonFormat1(DisbursementGatewayRequest)
  implicit val responeEntryFormat   = jsonFormat15(TransactionGatewayEntity)
  implicit val disbursementResponse = jsonFormat2(DisbursementGatewayResponse)

}
