package com.wolenjeMerchantCore.core.firebase

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

object Message {

  case class CloudMessageRequest(message: CloudMessageEntity)
  case class CloudMessageResponse(name: String)

  case class CloudMessageEntity(notification: Map[String, String], token: String)

  object CloudMessageJsonProtocol extends DefaultJsonProtocol with SprayJsonSupport {
    implicit val messageEntity   = jsonFormat2(CloudMessageEntity)
    implicit val messageRequest  = jsonFormat1(CloudMessageRequest)
    implicit val messageResponse = jsonFormat1(CloudMessageResponse)
  }
}
