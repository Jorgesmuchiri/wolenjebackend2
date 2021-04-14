package com.wolenjeMerchantCore.core.token

import spray.json.DefaultJsonProtocol

object Message extends DefaultJsonProtocol {
  case class TokenResponse(session: SessionObject)
  case class SessionObject(
    session_token: String,
    id: String,
    user_name: String,
    role: String,
    agentno: String
  )

  implicit val sessionObject   = jsonFormat5(SessionObject)
  implicit val initialResponse = jsonFormat1(TokenResponse)
}
