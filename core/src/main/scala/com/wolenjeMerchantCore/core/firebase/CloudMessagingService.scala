package com.wolenjeMerchantCore.core.firebase

import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, MessageEntity, Uri}
import com.wolenjeMerchantCore.core.http.WolenjeHttpClientT

import spray.json._

import scala.util.{Failure, Success}
import com.wolenjeMerchantCore.core.config.WolenjeConfig
import com.wolenjeMerchantCore.core.db.postgresql.cache.UserTokenDbCacheT
import com.wolenjeMerchantCore.core.db.postgresql.service.PostgresqlDbService
import PostgresqlDbService.UserTokenRowObject

object CloudMessagingService {
  case class SendAccountTopupNotification(
   userId: Int,
   newBalance: BigDecimal,
   topupAmount: BigDecimal,
   currency: String,
   retryNum: Int = 0
  )
}

class CloudMessagingService extends WolenjeHttpClientT {

  import Message._
  import CloudMessageJsonProtocol._
  import CloudMessagingService._

  object UserTokenDbCache extends UserTokenDbCacheT {
    implicit val system  = context.system
    implicit val timeout = Timeout(WolenjeConfig.httpRequestTimeout)
  }

  //Get client's registration token
  override def receive = {
    case req: SendAccountTopupNotification =>
      log.info(s"Processing $req")
      val cloudMessagingUrl = WolenjeConfig.firebaseCloudMessageUrl
      val serviceAccount    = WolenjeConfig.firebaseServiceAccount
 
      UserTokenDbCache.findByUserId(req.userId).mapTo[Option[UserTokenRowObject]] onComplete {
        case Failure(exception)        => log.error(s"Error while processing $req: ", Some(exception))
        case Success(None)             => log.warning(s"User does not have a registration token for notification")
        case Success(Some(tokenEntry)) =>
          val newBalanceAmount = req.currency + " " + req.newBalance
          val topupAmount      = req.currency + " " + req.topupAmount

          val sendFut = for {
            entity  <- Marshal(
              CloudMessageRequest(
                CloudMessageEntity(
                  notification = Map(
                    "title" -> "Top up Notification",
                    "body"  -> s"You have topped up ${topupAmount}. Your new balance is $newBalanceAmount"
                  ),
                  token = tokenEntry.token
                )
              )
            ).to[MessageEntity]
            request <- sendHttpRequest(
              HttpRequest(
                method = HttpMethods.POST,
                entity = entity,
                uri    = Uri(cloudMessagingUrl)
              ).withHeaders(RawHeader("GOOGLE_APPLICATION_CREDENTIALS",serviceAccount))
            )
          } yield request

          sendFut onComplete {
            case Failure(exception) => log.error(s"Error encountered when trying to send $req", Some(exception))
            exponentialBackoff(req)
            case Success(value) =>
              if (value.status.isSuccess) {
                try {
                  val x = value.data.parseJson.convertTo[CloudMessageResponse]
                  log.info(s"$req completed: $x")
                } catch {
                  case ex: Throwable => log.error(s"Error Encountered while processing $req: ", Some(ex))
                }
              } else exponentialBackoff(req)
          }
      }
  }

  private def exponentialBackoff(req: SendAccountTopupNotification) = {
    if (req.retryNum < WolenjeConfig.firebaseExponentialBackoffMaxRetryNum) {
      val delay = req.retryNum * WolenjeConfig.firebaseExponentialBackoffDuration;
      context.system.scheduler.scheduleOnce(delay seconds, self, req.copy(retryNum = req.retryNum + 1))
    } else log.error(s"Unable to complete $req: Max Retry Number reached")
  }
}