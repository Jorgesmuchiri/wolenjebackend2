package com.wolenjeMerchantCore.service.merchant

import spray.json.DefaultJsonProtocol

object MerchantGatewayMessage extends DefaultJsonProtocol {
  case class SendOTPGatewayRequest(phone: String)
  case class VerifyOTPGatewayRequest(
    otp: String,
    phone: String
  )
  case class VerifyOTPGatewayResponse(
    errors: Option[String],
    session: Option[Map[String, String]]
  )

  case class PasswordGatewayRequest(
    pin: String,
    phone: String
  )

  case class PasswordGatewayResponse(
    status: String,
    msg: String
  )

  implicit val sendOTPRequest       = jsonFormat1(SendOTPGatewayRequest)
  implicit val verifyOTPRequest     = jsonFormat2(VerifyOTPGatewayRequest)
  implicit val verifyResponse       = jsonFormat2(VerifyOTPGatewayResponse)
  implicit val passwordRequest      = jsonFormat2(PasswordGatewayRequest)
  implicit val passwordResponse     = jsonFormat2(PasswordGatewayResponse)
}

