package com.wolenjeMerchantCore.web

import scala.concurrent.duration._
import akka.actor.{ActorRefFactory, Props}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.util.Timeout
import com.wolenjeMerchantCore.core.db.postgresql.cache.AuthDbCache
import com.wolenjeMerchantCore.core.query.QueryService
import com.wolenjeMerchantCore.core.util.WolenjeCoreServiceT
import com.wolenjeMerchantCore.service.merchant.MerchantRequestService
import com.wolenjeMerchantCore.service.payment.notification.PaymentNotificationService
import com.wolenjeMerchantCore.service.payment.request.PaymentRequestService
import com.wolenjeMerchantCore.service.payment.schedule.PaymentScheduleService
import com.wolenjeMerchantCore.web.Auth.LoginRequest
import com.wolenjeMerchantCore.web.marshalling.WebJsonSupportT
import com.wolenjeMerchantCore.core.db.postgresql.service.PostgresqlDbService

import PostgresqlDbService.AuthRowObject

trait WolenjeWebServiceT extends WebJsonSupportT with WolenjeCoreServiceT {


  def actorRefFactory: ActorRefFactory
  def authList: List[AuthRowObject]

  val notificationService = actorRefFactory.actorOf(Props[PaymentNotificationService])
  val authDbService       = actorRefFactory.actorOf(Props[AuthDbCache])
  val merchantService     = actorRefFactory.actorOf(Props[MerchantRequestService])
  val scheduleService     = actorRefFactory.actorOf(Props[PaymentScheduleService])
  val paymentRequestService = actorRefFactory.actorOf(Props[PaymentRequestService])
  val queryService        = actorRefFactory.actorOf(Props[QueryService])

  implicit val timeout = Timeout(15 seconds)

  val authorizationKey = "authorization_key".toLowerCase

  import MerchantRequestService._
  import PaymentNotificationService._
  import PaymentRequestService._
  import PaymentScheduleService._
  import QueryService._
  lazy val route = headerValueByName(authorizationKey) { authKey =>
    authorize(authList.exists(_.apiKey == authKey)) {
      path("merchant"/"new") {
        logRequestResult("merchant:new") {
          post {
            entity(as[MerchantNewRequest]) { request =>
              complete {
                (merchantService ? request).mapTo[MerchantRequestResponse]
              }
            }
          }
        }
      } ~
      path("merchant"/"verify") {
        logRequestResult("merchant:verify") {
          post {
            entity(as[MerchantVerifyRequest]) { request =>
              complete {
                (merchantService ? request).mapTo[MerchantRequestResponse]
              }
            }
          }
        }
      } ~
      path("merchant"/"login") {
        logRequestResult("merchant:login") {
          post {
            entity(as[LoginRequest]) { request =>
              complete (Auth.login(request))
            }
          }
        }
      } ~
      path("merchant"/"password"/"forgot") {
        logRequestResult("merchant:password:forgot") {
          get {
            parameter('emailAddress.as[String]) { emailAddress =>
              complete {
                (merchantService ? MerchantPasswordOTPRequest(emailAddress)).mapTo[MerchantRequestResponse]
              }
            }
          }
        }
      } ~
      path("merchant"/"password"/"new") {
        logRequestResult("merchant:password:new") {
          post {
            entity(as[MerchantNewPasswordRequest]) { request =>
              complete {
                (merchantService ? request).mapTo[MerchantRequestResponse]
              }
            }
          }
        }
      } ~
      path("merchant" / "token" / "add") {
        logRequestResult("merchant:token:add") {
          post {
            entity(as[UserTokenCreateRequest]) { request =>
              complete {
                (merchantService ? request).mapTo[UserTokenCreateResponse]
              }
            }
          }
        }
      } ~
      path("merchant" / "balance") {
        logRequestResult("merchant:balance") {
          get {
            parameter('userId.as[Int]) { userId =>
              complete {
                (queryService ? WalletBalanceQueryRequest(userId))
                  .mapTo[WalletBalanceQueryResponse]
              }
            }
          }
        }
      } ~
      path("payment"/"delete") {
        logRequestResult("payment:delete") {
          post {
            entity(as[PaymentDeleteRequest]) { editRequest =>
              complete {
                (paymentRequestService ? editRequest)
                  .mapTo[PaymentUpdateResponse]
              }
            }
          }
        }
      } ~
      /*path("payment"/"edit") {
        logRequestResult("payment:edit") {
          post {
            entity(as[PaymentEditRequest]) { editRequest =>
              complete {
                (paymentRequestService ? editRequest)
                  .mapTo[PaymentUpdateResponse]
              }
            }
          }
        }
      } ~*/
      path("payment"/"notification") {
        logRequestResult("payment:notification") {
          post {
            entity(as[PaymentConfirmationEntity]) { request =>
              notificationService ! PaymentNotificationRequest(
                account       = request.ref,
                amount        = request.amount,
                currency      = "KES", //For now - let's find a way to change this eventually
                providerRefId = request.ref
              )
              complete(StatusCodes.OK)
            }
          }
        }
      } ~
      path("payment"/"request") {
        logRequestResult("payment:request") {
          post {
            entity(as[PaymentServiceRequest]) { request =>
              complete {
                (paymentRequestService ? request)
                  .mapTo[PaymentServiceResponse]
              }
            }
          }
        }
      } ~
      path("payment"/"result") {
        logRequestResult("payment:result") {
          post {
            entity(as[PaymentConfirmationEntity]) { result =>
              complete {
                scheduleService ! result
                StatusCodes.OK
              }
            }
          }
        }
      } ~
      path("payment" / "transactions") {
        logRequestResult("payment:transactions") {
          get {
            parameters('userId.as[Int], 'limit.as[Int]) {
              (userId, limit) =>
                complete {
                  (queryService ? TransactionFetchQueryRequest(userId = userId, limit = limit))
                    .mapTo[TransactionFetchQueryResponse]
                }
            }
          }
        }
      } ~
      path("wallet"/"transfer") {
        logRequestResult("wallet:transfer") {
          post {
            entity(as[WalletTransferRequest]) { request =>
              complete {
                (paymentRequestService ? request)
                  .mapTo[WalletTransferResponse]
              }
            }
          }
        }
      }
    }
  }
}
