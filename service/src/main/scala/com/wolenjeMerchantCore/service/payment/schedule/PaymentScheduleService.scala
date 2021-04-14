package com.wolenjeMerchantCore.service.payment.schedule

import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.Props
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, MessageEntity}
import akka.pattern.ask
import akka.util.Timeout
import com.wolenjeMerchantCore.core.config.WolenjeConfig
import spray.json._
import com.wolenjeMerchantCore.core.db.postgresql.cache._
import com.wolenjeMerchantCore.core.db.postgresql.service.PostgresqlDbService
import com.wolenjeMerchantCore.core.db.redis.WolenjeRedisClient
import com.wolenjeMerchantCore.core.http.WolenjeHttpClientT
import com.wolenjeMerchantCore.core.token.TokenService
import com.wolenjeMerchantCore.core.token.TokenService.{GetMerchantTokenByNumberRequest, GetMerchantTokenByNumberResponse}
import com.wolenjeMerchantCore.core.util.WolenjeEnum.{Provider, TransactionStatus, TransactionType}
import com.wolenjeMerchantCore.core.util.{WolenjeJsonProtocol, WolenjeUtil}
import com.wolenjeMerchantCore.service.util.WalletT

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

import WolenjeJsonProtocol._
import PostgresqlDbService._
import PaymentScheduleService._
import DisbursementGatewayMessage._

object PaymentScheduleService {
  case object Process

  case class PaymentConfirmationEntity(
    service_id: String,
    created_on: String,
    created_by: String,
    created_by_id: String,
    trx_id: String,
    req_id: String,
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

  case class PaymentRequest(
    id: Int,
    client: String,
    amount: BigDecimal,
    provider: Provider.Value,
    reason: Option[String],
    month: Int,
    transactionId: String
  )
}

class PaymentScheduleService extends WolenjeHttpClientT with WalletT {

  def tokenService                                       = context.actorOf(Props[TokenService])
  def sqlDbService                                       = context.actorOf(Props[PostgresqlDbService])
  def getMerchantDbCache: MerchantDbCacheT               = MerchantDbCache

  implicit val timeout = Timeout(WolenjeConfig.postgresqlDbTimeout)

  override def preStart() {
    context.system.scheduler.scheduleOnce(
      FiniteDuration.apply(1,"min"),
      self,
      Process
    )
  }

  override def receive: Receive = {
    case Process =>
      val scheduledPayments = fetchTransactions onComplete {
        case Failure(e) => log.error(s"Error Processing Scheduled Transactions: ", e)
        case Success(r) =>
          if (r.nonEmpty) {
            groupByUserId(r) foreach { case (userId, userPaymentRequests) =>
              processTransaction(userPaymentRequests, userId)
            }
            context.system.scheduler.scheduleOnce(FiniteDuration.apply(1,"h"), self, Process)
          }
      }

    case req: PaymentConfirmationEntity =>
      log.info(s"Processing $req")
      //Find transaction using transactionId
      val transactionId = req.req_id

      WolenjeRedisClient.fetchElement(transactionId) match {
        case None    => log.error("Manual Intervention Required. Couldn't find element")
        case Some(x) =>
          val pendingElement                  = x.parseJson.convertTo[TransactionRowObject]
          val status: TransactionStatus.Value = req.status == "TRX_OK" match {
            case true  => TransactionStatus.Success
            case false =>
              if (req.status == "TRX_DUPLICATE") TransactionStatus.Failed
              else processFailedStatus(req, pendingElement)
          }

          sqlDbService ! TransactionRowObject(
            transactionId   = transactionId,
            providerRefId   = req.receipt_id,
            status          = status,
            amount          = pendingElement.amount,
            currency        = pendingElement.currency,
            userId          = pendingElement.userId,
            transactionType = pendingElement.transactionType,
            processingFee   = status match {
              case TransactionStatus.Success => pendingElement.processingFee
              case                          _=> None
            },
            reason          = pendingElement.reason
          )
      }
  }

  private def processFailedStatus(
    req: PaymentConfirmationEntity,
    pendingElement: TransactionRowObject
  ): TransactionStatus.Value = {
    try {
      getMerchantWallet(pendingElement.userId) onComplete {
        case Failure(exception)            => log.error(s"Error Encountered while processing $req: {}", Some(exception))
        case Success(None)                 => log.error(s"No wallet attached to merchant")
        case Success(Some(merchantWallet)) =>
          val amount = WolenjeUtil.convertAmount(
            amount        = pendingElement.amount + pendingElement.processingFee.getOrElse(0),
            currency      = pendingElement.currency,
            givenCurrency = merchantWallet.currency
          )
          sqlDbService ! MerchantWalletDbUpdate(
            userId = pendingElement.userId,
            amount = merchantWallet.amount + amount
          )
      }
    } catch {
      case ex: Throwable =>
        log.error(s"Error refunding Wallet.. Manual Intervention Needed", Some(ex))
    }
    TransactionStatus.Failed
  }


  private def processTransaction(paymentRequests: List[PaymentRequest], merchantId: Int) = {
    getMerchantWallet(merchantId) onComplete {
      case Failure(exception)            => log.error(s"Error Encountered while processing $paymentRequests: {}", Some(exception))
      case Success(None)                 => log.error(s"User Wallet Couldn't be found for transaction: $paymentRequests")
      case Success(Some(merchantWallet)) =>
        val amount        = paymentRequests.foldLeft(BigDecimal(0.0))((total, paymentRequest) => total + paymentRequest.amount)
        val processingFee = WolenjeUtil.getProcessingFee(merchantWallet.currency, amount)
        val moneyToDeduct = WolenjeUtil.convertAmount(amount = amount, currency = merchantWallet.currency, givenCurrency = merchantWallet.currency) + processingFee.getOrElse(0)

        if (moneyToDeduct > merchantWallet.amount) {
          paymentRequests foreach { request =>
            failTransaction(
              transactionId  = request.transactionId,
              merchantWallet = merchantWallet,
              amount         = request.amount,
              reason         = "Insufficient Funds"
            )
          }
        } else {
          getMerchantDbCache.findByUserId(merchantId) match {
            case None           => log.error(s"User couldn't be found for transaction: $paymentRequests")
            case Some(merchant) =>
              (tokenService ? GetMerchantTokenByNumberRequest(merchant.accountNumber))
                .mapTo[GetMerchantTokenByNumberResponse] onComplete {
                case Failure(exception) => log.error(s"Error getting token for $paymentRequests ", Some(exception))
                  failPaymentRequests(paymentRequests, merchantWallet, "Internal Server Error")
                case Success(tokenResponse: GetMerchantTokenByNumberResponse) =>
                  tokenResponse.token match {
                    case None        => failPaymentRequests(paymentRequests, merchantWallet, "Internal Server Error")
                    case Some(token) => disbursementHttpRequest(paymentRequests, merchantWallet, processingFee, token)
                  }
              }
          }
        }
    }
  }


  private def completeDisbursement(
    merchantWallet: MerchantWalletRowObject,
    data: String,
    processingFee: Option[BigDecimal],
    paymentRequests: List[PaymentRequest]
  ) = {
    val responseData = data.parseJson.convertTo[DisbursementGatewayResponse]
    paymentRequests foreach { paymentRequest =>
      responseData.isSuccessful(paymentRequest.transactionId) match {
        case false =>
          failTransaction(
            transactionId  = paymentRequest.transactionId,
            merchantWallet = merchantWallet,
            amount         = paymentRequest.amount,
            reason         = s"Transaction Failed"
          )

        case true =>
          val pendingElement = TransactionRowObject(
            transactionId   = paymentRequest.transactionId,
            amount          = paymentRequest.amount,
            currency        = merchantWallet.currency,
            userId          = merchantWallet.userId,
            transactionType = TransactionType.Outbound,
            status          = TransactionStatus.PendingConfirmation,
            providerRefId   = "",
            processingFee   = processingFee,
            reason          = paymentRequest.reason
          ).toJson.toString
          WolenjeRedisClient.addElement(paymentRequest.transactionId, pendingElement, WolenjeConfig.currencyUpdateFrequency)

          //Deduct from Wallet
          sqlDbService ! MerchantWalletDbUpdate(
            userId = merchantWallet.userId,
            amount = merchantWallet.amount - paymentRequest.amount
          )

          if (paymentRequest.month != 0) sqlDbService ! MerchantPaymentDeleteDbQuery(paymentRequest.id, merchantWallet.userId)
      }
    }
  }


  private def disbursementHttpRequest(
    paymentRequests: List[PaymentRequest],
    merchantWallet: MerchantWalletRowObject,
    processingFee: Option[BigDecimal],
    token: String
  ) = {
    val paymentRequestsToHttp = paymentRequests map { x =>
      DisbursementGatewayEntry(
        product_name = x.provider match {
          case Provider.Mpesa  => "MPESA_B2C"
          case Provider.Telkom => "TKASH_B2C"
        },
        amount       = x.amount,
        req_id       = x.transactionId,
        ref          = x.client,
        phone        = x.client
      )
    }
    val httpResponseFut: Future[HttpResponse] = for {
      entity <- Marshal(DisbursementGatewayRequest(
        services = paymentRequestsToHttp
      )).to[MessageEntity]
      response <- sendHttpRequest(
        HttpRequest(
          method = HttpMethods.POST,
          uri    = WolenjeConfig.disburseGatewayUrl,
          entity = entity
        ).withHeaders(RawHeader("Authorization","Bearer " + token))
      )
    } yield {
      log.info(s"Http request with response: $response")
      response
    }

    httpResponseFut onComplete {
      case Failure(exception) =>
        log.error(s"Error communicating with gateway for transaction $paymentRequests", Some(exception))
        failPaymentRequests(paymentRequests, merchantWallet, "Error Communicating with Gateway")

      case Success(value) =>
        value.status.isSuccess match {
          case true  => completeDisbursement(merchantWallet, value.data, processingFee, paymentRequests)
          case false =>
            log.error(s"Error encountered with sending request for transaction: $paymentRequests --response: $httpResponseFut")
            failPaymentRequests(paymentRequests, merchantWallet, "Internal Server Error")
        }
    }
  }


  private def failPaymentRequests(
    paymentRequests: List[PaymentRequest],
    merchantWallet: MerchantWalletRowObject,
    reason: String
  ) = {
    paymentRequests foreach { request => failTransaction(
        transactionId  = request.transactionId,
        merchantWallet = merchantWallet,
        amount         = request.amount,
        reason         = reason
      )
    }
  }


  private def failTransaction(
    merchantWallet: MerchantWalletRowObject,
    transactionId: String,
    amount: BigDecimal,
    reason: String
  ): Unit = {
    sqlDbService ! TransactionRowObject(
      transactionId   = transactionId,
      amount          = amount,
      currency        = merchantWallet.currency,
      userId          = merchantWallet.userId,
      transactionType = TransactionType.Outbound,
      status          = TransactionStatus.Failed,
      providerRefId   = "",
      processingFee   = None,
      reason          = Some(reason)
    )
  }


  private def fetchTransactions = (sqlDbService ? MerchantPaymentFetchDbQuery).mapTo[List[MerchantPaymentRowObject]]

  private def groupByUserId(paymentRequests: List[MerchantPaymentRowObject]): Map[Int, List[PaymentRequest]] = {
    //Filter through list and convert to map using userId
    val resultMap: Map[Int, List[PaymentRequest]] = paymentRequests.foldLeft(Map[Int, List[PaymentRequest]]()) {
      case (map, entry) => map.get(entry.userId) match {
        case None => map.updated(entry.userId, List(PaymentRequest(
          id            = entry.id,
          amount        = entry.amount,
          client        = entry.client,
          provider      = entry.provider,
          reason        = entry.reason,
          month         = entry.month,
          transactionId = java.util.UUID.randomUUID.toString
        )))
        case Some(existingEntry)  =>
          map.updated(entry.userId, existingEntry ++ List(PaymentRequest(
            id            = entry.id,
            amount        = entry.amount,
            client        = entry.client,
            provider      = entry.provider,
            reason        = entry.reason,
            month         = entry.month,
            transactionId = java.util.UUID.randomUUID.toString
          )))
      }
    }
    resultMap
  }
}
