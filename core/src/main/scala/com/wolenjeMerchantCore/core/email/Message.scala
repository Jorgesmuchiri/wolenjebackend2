package com.wolenjeMerchantCore.core.email

import spray.json.DefaultJsonProtocol

class Message {

  case class SendGridEmailRequest(
    api_key: String,
    api_user: String,
    to: String,
    toname: String,
    subject: String,
    text: String,
    from: String
  )

  case class SendGridEmailResponse(
    message: String
  )

  object SendGridEmailJsonMarshalling extends DefaultJsonProtocol
}
