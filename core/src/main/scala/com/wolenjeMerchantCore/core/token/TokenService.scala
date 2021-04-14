package com.wolenjeMerchantCore.core.token

import java.nio.charset.StandardCharsets

import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.ActorRef
import spray.json._
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.RawHeader
import akka.util.Timeout
import com.wolenjeMerchantCore.core.config.WolenjeConfig
import com.wolenjeMerchantCore.core.db.postgresql.cache.{MerchantDbCache, MerchantDbCacheT}
import com.wolenjeMerchantCore.core.db.redis.WolenjeRedisClient
import com.wolenjeMerchantCore.core.http.WolenjeHttpClientT

import scala.util.{Failure, Success}

import Message._
import TokenService._

/* Specifically Request Token Service */
object TokenService {
  case class RegisterPhoneNumber(number: String)
  case class GetMerchantTokenByNumberRequest(number: String)
  case class GetMerchantTokenByNumberResponse(token: Option[String])
}

class TokenService extends WolenjeHttpClientT {

  def getMerchantDbCache: MerchantDbCacheT = MerchantDbCache
  implicit val timeout = Timeout(WolenjeConfig.httpRequestTimeout)

  override def receive: Receive = {
    case req: RegisterPhoneNumber =>
      log.info(s"Processing $req")
      getMerchantDbCache.findByNumber(req.number) match {
        case None           => log.error(s"Can't find merchant for $req")
        case Some(merchant) =>
          val encodedString = java.util.Base64.getEncoder.encodeToString(s"${req.number}:${merchant.password}".getBytes(StandardCharsets.UTF_8))
          sendHttpRequest(HttpRequest(uri = WolenjeConfig.tokenUrl).withHeaders(RawHeader("Authorization", "Basic " + encodedString))) onComplete {
            case Failure(exception) =>
              log.error(s"Error requesting initial token for ${req.number}: {}", Some(exception))
              self ! req
            case Success(response)  =>
              response.status.isSuccess match {
                case false =>
                  log.error(s"Error requesting initial token for ${req.number}: $response")
                  self ! req
                case true  =>
                  val responseObject = response.data.parseJson.convertTo[TokenResponse]
                  updateToken(req, responseObject.session.session_token, self)
              }
          }
      }

    case req: GetMerchantTokenByNumberRequest =>
      log.info(s"Processing $req")
      val currentSender = sender()

    WolenjeRedisClient.fetchElement(req.number) match {
      case None        => getMerchantDbCache.findByNumber(req.number) match {
        case None           => log.error(s"Can't find merchant for $req")
        case Some(merchant) =>
          val encodedString = java.util.Base64.getEncoder.encodeToString(s"${req.number}:${merchant.password}".getBytes(StandardCharsets.UTF_8))
          sendHttpRequest(HttpRequest(uri = WolenjeConfig.tokenUrl).withHeaders(RawHeader("Authorization", "Basic " + encodedString))) onComplete {
            case Failure(exception) =>
              log.error(s"Error requesting initial token for ${req.number}: ", Some(exception))
              currentSender ! GetMerchantTokenByNumberResponse(None)
            case Success(response)  =>
              response.status.isSuccess match {
                case false =>
                  log.error(s"Error requesting initial token for ${req.number} : $response")
                  currentSender ! GetMerchantTokenByNumberResponse(None)
                case true  =>
                  val responseObject = response.data.parseJson.convertTo[TokenResponse]
                  val token          = responseObject.session.session_token
                  currentSender ! GetMerchantTokenByNumberResponse(Some(token))
                  updateToken(RegisterPhoneNumber(req.number), token, self)
              }
          }
      }
      case Some(token) => currentSender ! GetMerchantTokenByNumberResponse(Some(token))
    }
  }

  private def updateToken(
    req: RegisterPhoneNumber,
    token: String,
    actorRef: ActorRef
  ): Unit = {
    WolenjeRedisClient.addElement(
      key      = req.number,
      value    = token,
      lifetime = WolenjeConfig.tokenLifetime
    )
    context.system.scheduler.scheduleOnce(
      WolenjeConfig.tokenLifetime,
      actorRef,
      req
    )
  }
}
