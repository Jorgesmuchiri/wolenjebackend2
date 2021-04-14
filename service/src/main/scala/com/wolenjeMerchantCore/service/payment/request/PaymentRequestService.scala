package com.wolenjeMerchantCore.service.payment.request

import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.{ActorRef,Props}
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
import com.wolenjeMerchantCore.core.util.WolenjeEnum._
import com.wolenjeMerchantCore.core.util.{WolenjeJsonProtocol, WolenjeUtil}
import com.wolenjeMerchantCore.service.payment.schedule.DisbursementGatewayMessage
import com.wolenjeMerchantCore.service.util.WalletT

import scala.concurrent.Future
import scala.util.{Failure, Success}

import PostgresqlDbService._
import WolenjeJsonProtocol._
import DisbursementGatewayMessage._
import PaymentRequestService._

object PaymentRequestService {
  //Payment Disbursement Request
  case class PaymentServiceRequest(
    userId: Int,
    requests: List[PaymentRequest]
  )
  case class PaymentRequest(
     clientAccount: String,
     amount: BigDecimal,
     provider: Provider.Value,
     reason: Option[String],
     month: Int,
     hour: Int,
     day: Int
  )
  case class PaymentServiceResponse(
    status: TransactionStatus.Value,
    reason: Option[String]
  )

  //Delete Payment
  case class PaymentDeleteRequest(
    paymentIds: List[Int],
    userId: Int
  )

  //Edit Payment Request
  case class PaymentEditRequest(
    paymentId: Int,
    client: Option[String],
    amount: Option[BigDecimal],
    provider: Option[Provider.Value],
    reason: Option[String],
    day: Option[Int],
    month: Option[Int],
    hour: Option[Int]
  )

  case class PaymentUpdateResponse(
    status: TransactionStatus.Value,
    description: String
  )

  //Wallet Transfer
  case class WalletTransferRequest(
    recipientName: String,
    userId: Int,
    amount: BigDecimal
  )
  case class WalletTransferResponse(
    status: TransactionStatus.Value,
    description: Option[String]
  )
  //Withdrawal to Paybill or Bank Acc
  case class WithdrawalRequest(
    userId: Int,
    amount: BigDecimal
  )

  case class WithdrawalResponse(
    status: TransactionStatus.Value,
    reason: Option[String]
  )
}

class PaymentRequestService extends WolenjeHttpClientT with WalletT {

  implicit val timeout = Timeout(WolenjeConfig.httpRequestTimeout)

  def sqlDbService                                       = context.actorOf(Props[PostgresqlDbService])
  def tokenService                                       = context.actorOf(Props[TokenService])
  def getMerchantDbCache: MerchantDbCacheT               = MerchantDbCache

  override def receive: Receive = {
    case req: PaymentServiceRequest =>
      log.info(s"Processing $req")
      val currentSender = sender()
      paymentServiceRequest(req, currentSender)

    case req: PaymentDeleteRequest =>
      log.info(s"Processing $req")
      val currentSender = sender()

      req.paymentIds foreach { paymentId => sqlDbService ! MerchantPaymentDeleteDbQuery(paymentId, req.userId)}

      currentSender ! PaymentUpdateResponse(
        status      = TransactionStatus.Success,
        description = "Payment Delete Request Accepted"
      )

    /*case req: PaymentEditRequest =>
      log.info(s"Processing $req")
      val currentSender = sender()
      editPayment(req, currentSender)*/

    case req: WalletTransferRequest =>
      log.info(s"Processing $req")
      val currentSender = sender()
      walletTransferRequest(req, currentSender)

    case req: WithdrawalRequest =>
      log.info(s"Processing $req")
      val currentSender = sender()
      withdrawalRequest(req, currentSender)
  }

  protected  def addTransaction(
    transactionId: String, 
    userId: Int,
    currency: String, 
    amount: BigDecimal,
    reason: Option[String],
    processingFee: Option[BigDecimal],
    providerRefId: String,
    transactionType: TransactionType.Value,
    status: TransactionStatus.Value
  ) = {
    sqlDbService ! TransactionRowObject(
      transactionId   = transactionId,
      userId          = userId,
      currency        = currency,
      amount          = amount,
      transactionType = transactionType,
      status          = status,
      providerRefId   = providerRefId,
      processingFee   = processingFee,
      reason          = reason
    )
  }

  protected def disbursementHttpRequest(
    token: String,
    transactionId: String,
    req: WithdrawalRequest,
    currentSender: ActorRef,
    moneyToDeduct: BigDecimal,
    merchant: MerchantRowObject,
    processingFee: Option[BigDecimal],
    merchantWallet: MerchantWalletRowObject
  ) = {
    val responseFut = for {
      entity <- Marshal(DisbursementGatewayRequest(
        services = List(DisbursementGatewayEntry(
          req_id       = transactionId,
          amount       = req.amount,
          ref          = merchant.accountNumber,
          phone        = merchant.accountNumber,
          product_name = merchant.accountProvider match {
            case Provider.Mpesa  => "MPESA_B2C"
            case Provider.Telkom => "TELKOM_B2C"
          }
        ))
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

    responseFut.mapTo[HttpResponse].map {x =>
      completeWithdrawalRequest(
        currentSender  = currentSender,
        req            = req,
        transactionId  = transactionId,
        merchantWallet = merchantWallet,
        moneyToDeduct  = moneyToDeduct,
        processingFee  = processingFee,
        value          = x
      )
    }
  }

  protected def editPayment(req: PaymentEditRequest, currentSender: ActorRef) = {
    (sqlDbService ? MerchantPaymentFindDbQuery(req.paymentId))
    .mapTo[Option[MerchantPaymentRowObject]] onComplete {
      case Failure(ex) => 
        log.error("Error while processing $req: ", ex)
        currentSender ! PaymentUpdateResponse(
          status      = TransactionStatus.Failed,
          description = "Internal Server Error"
        )

      case Success(None) =>
        currentSender ! PaymentUpdateResponse(
          status      = TransactionStatus.Failed,
          description = "Invalid Payment Id Provided"
        )
      case Success(Some(scheduledPayment)) =>
        if(isRequestEmpty(req)) {
          currentSender ! PaymentUpdateResponse(
            status      = TransactionStatus.Failed,
            description = "Can't process empty request"
          )
        } else {
          sqlDbService ! MerchantPaymentEntryDbUpdate(
            paymentId = req.paymentId,
            amount    = req.amount.getOrElse(scheduledPayment.amount),
            client    = req.client.getOrElse(scheduledPayment.client),
            provider  = req.provider.getOrElse(scheduledPayment.provider),
            reason    = req.reason match {
              case Some(reason) => Some(reason)
              case None         => scheduledPayment.reason
            },
            day       = req.day.getOrElse(scheduledPayment.day),
            month     = req.month.getOrElse(scheduledPayment.month),
            hour      = req.hour.getOrElse(scheduledPayment.hour)
          )

          currentSender ! PaymentUpdateResponse (
            status      = TransactionStatus.Success,
            description = "Transaction(s) accepted for updating"
          )
        }
    }
  }

  protected def paymentServiceRequest(
    req: PaymentServiceRequest,
    currentSender: ActorRef
  ) = {
    try {
      getMerchantDbCache.findByUserId(req.userId) match {
        case None           => currentSender ! PaymentServiceResponse(TransactionStatus.Failed, Some("Invalid User Id provided"))
        case Some(merchant) =>
          getMerchantWallet(req.userId) onComplete {
            case Failure(exception)    => log.error(s"Error Encountered while processing $req: {}", Some(exception))
              currentSender ! PaymentServiceResponse(TransactionStatus.ApplicationError, Some("Internal Server Error"))
            case Success(None)         => currentSender ! PaymentServiceResponse(TransactionStatus.Failed, Some("Cannot add payments without a wallet. Top up to create a wallet"))
            case Success(Some(wallet)) =>
              if (merchant.accountStatus.equals(AccountStatus.Active)) {
                val totalRequestValue: BigDecimal= req.requests.map(_.amount).sum
                val totalCost = totalRequestValue + WolenjeUtil.getProcessingFee(wallet.currency, totalRequestValue).getOrElse(0)

                if (wallet.amount >= totalCost) {
                  req.requests foreach { singleRequest =>
                    sqlDbService ! MerchantPaymentInsertDbRequest(req.userId, singleRequest.clientAccount, singleRequest.amount, singleRequest.provider, singleRequest.reason, singleRequest.month, singleRequest.day, singleRequest.hour)
                  }
                  currentSender ! PaymentServiceResponse(TransactionStatus.Completed, None)
                } else currentSender ! PaymentServiceResponse(TransactionStatus.Failed, Some("Insufficient Balance. Please top-up to ensure successful scheduling of transactions"))
              } else currentSender ! PaymentServiceResponse(TransactionStatus.Failed, Some("Non-Active accounts cannot schedule transactions"))
          }
      }
    } catch {
      case ex: Throwable => log.error(s"Error encountered with request $req: {}", Some(ex))
        currentSender ! PaymentServiceResponse(TransactionStatus.ApplicationError, Some("Internal Gateway Error"))
    }
  }

  protected def walletTransferRequest(
    req: WalletTransferRequest,
    currentSender: ActorRef
  ) = {
    try {
      getMerchantDbCache.findByUserId(req.userId) match {
        case None                 => currentSender ! WalletTransferResponse(TransactionStatus.Failed, Some("Invalid User Id Provided"))
        case Some(senderMerchant) =>
          if(!senderMerchant.accountStatus.equals(AccountStatus.Active)) {
            currentSender ! WalletTransferResponse(TransactionStatus.Failed, Some("Non-active account cannot transfer money"))
          } else {
            getMerchantWallet(req.userId) onComplete {
              case Failure(exception) =>
                log.error(s"Error Encountered while processing $req: {}", Some(exception))
                currentSender ! WalletTransferResponse(TransactionStatus.ApplicationError, Some("Internal Server Error"))
              
              case Success(None)               => currentSender ! WalletTransferResponse(TransactionStatus.Failed, Some("No wallet attached to merchant account"))
              case Success(Some(senderWallet)) =>
                val processingFee: BigDecimal = WolenjeUtil.getProcessingFee(senderWallet.currency, req.amount).getOrElse(0)
                if (senderWallet.amount < (req.amount + processingFee)) {
                  currentSender ! WalletTransferResponse(TransactionStatus.Failed, Some("Insufficient funds for requested transfer"))
                } else {
                  getMerchantDbCache.findByUsername(req.recipientName) match {
                    case None                    => currentSender ! WalletTransferResponse(TransactionStatus.Failed, Some(s"Recipient ${req.recipientName} Doesn't Exist. Please confirm and try again"))
                    case Some(recipientMerchant) =>
                      getMerchantWallet(recipientMerchant.id) onComplete {
                        case Failure(exception) =>
                          log.error(s"Error Encountered while processing $req: {}", Some(exception))
                          currentSender ! WalletTransferResponse(TransactionStatus.Failed, Some("Internal Server Error"))
                        case Success(None)                  => currentSender ! WalletTransferResponse(TransactionStatus.Failed, Some("Recipient has no wallet"))
                        case Success(Some(recipientWallet)) =>
                          val transactionId = WolenjeUtil.generateTransactionId
                          //Credit Recipient & Add Transaction Record
                          val receivedAmount = WolenjeUtil.convertAmount(amount = req.amount, currency = senderWallet.currency, givenCurrency = recipientWallet.currency)
                          sqlDbService ! MerchantWalletDbUpdate(recipientMerchant.id, recipientWallet.amount + receivedAmount)
                          addTransaction(transactionId, recipientMerchant.id, senderWallet.currency, req.amount, Some("Received funds from Wallet Transfer"), None, "", TransactionType.Inbound, TransactionStatus.Success)

                          //Debit Sender and Add Transaction
                          sqlDbService ! MerchantWalletDbUpdate(req.userId, senderWallet.amount - (req.amount + processingFee))
                          addTransaction(transactionId, req.userId, senderWallet.currency, req.amount, Some("Initiated Wallet Transfer"), Some(processingFee), "", TransactionType.Outbound, TransactionStatus.Success)
                          
                          currentSender ! WalletTransferResponse(TransactionStatus.Success, None)
                      }
                  }
                }
            }
          }
      }
    } catch {
      case ex: Throwable => log.error(s"Error processing request: $req. Error Encountered", Some(ex))
        currentSender ! WalletTransferResponse(TransactionStatus.ApplicationError, Some("Internal Gateway Error"))
    }
  }

  protected def withdrawalRequest(
    req: WithdrawalRequest,
    currentSender: ActorRef
  ) = {
    getMerchantDbCache.findByUserId(req.userId) match {
      case None           => currentSender ! WithdrawalResponse(TransactionStatus.Failed, Some("Invalid User Id Provided"))
      case Some(merchant) =>
        if (!merchant.accountStatus.equals(AccountStatus.Active)) {
          currentSender ! WithdrawalResponse(TransactionStatus.Failed, Some("Non-Active Account cannot move money out of wallet"))
        } else {
          try {
            getMerchantWallet(req.userId) onComplete {
              case Failure(exception) => log.error(s"Error Encountered while processing $req: {}", Some(exception))
                currentSender ! WithdrawalResponse(TransactionStatus.ApplicationError, Some("Internal Service Error"))

              case Success(None)                 => currentSender ! WithdrawalResponse(TransactionStatus.Failed, Some("No wallet attached to merchant"))
              case Success(Some(merchantWallet)) =>
                val processingFee  = WolenjeUtil.getProcessingFee(merchantWallet.currency, req.amount)
                val moneyToDeduct = req.amount + processingFee.getOrElse(0)
                if (moneyToDeduct <= merchantWallet.amount) {
                  //Process
                  (tokenService ? GetMerchantTokenByNumberRequest(merchant.accountNumber))
                    .mapTo[GetMerchantTokenByNumberResponse] onComplete {
                    case Failure(exception) => log.error(s"Error getting token for $req: ", Some(exception))
                      currentSender ! WithdrawalResponse(TransactionStatus.Failed, Some("Internal Server Error"))

                    case Success(tokenResponse: GetMerchantTokenByNumberResponse) =>
                      tokenResponse.token match {
                        case None        => currentSender ! WithdrawalResponse(TransactionStatus.Failed, Some("Internal Server Error"))
                        case Some(token) =>
                        val transactionId = WolenjeUtil.generateTransactionId
                        disbursementHttpRequest(token, transactionId, req, currentSender, moneyToDeduct, merchant, processingFee, merchantWallet)
                      }
                  }
                } else currentSender ! WithdrawalResponse(TransactionStatus.Failed, Some("Insufficient Funds"))
            }
          } catch {
            case ex: Throwable =>
              log.error(s"Error encountered when processing $req: ", Some(ex))
              currentSender ! WithdrawalResponse(TransactionStatus.ApplicationError, Some("Internal Gateway Error"))
          }
        }
    }
  }

  protected def completeWithdrawalRequest(
    value: HttpResponse, 
    req: WithdrawalRequest,
    merchantWallet: MerchantWalletRowObject,
    processingFee: Option[BigDecimal], 
    moneyToDeduct: BigDecimal,
    transactionId: String,
    currentSender: ActorRef
  ) = {
    value.status.isSuccess match {
      case false =>
        log.error(s"Error encountered with sending request for transaction: $req ---response: $value")
        currentSender ! WithdrawalResponse(TransactionStatus.Failed, Some("Error Communicating with Gateway. Please try again"))
      
        case true =>
        val responseData = value.data.parseJson.convertTo[DisbursementGatewayResponse]
        val status = responseData.transactions.service_count_ok == "1" match {
          case false => TransactionStatus.Failed
          case true  => TransactionStatus.PendingConfirmation
        }
        status match {
          case TransactionStatus.PendingConfirmation =>
            val pendingElement = TransactionRowObject(
              transactionId   = transactionId,
              userId          = req.userId,
              currency        = merchantWallet.currency,
              amount          = req.amount,
              transactionType = TransactionType.Outbound,
              status          = TransactionStatus.PendingConfirmation,
              providerRefId   = "",
              processingFee   = processingFee,
              reason          = Some("Withdrawal to Account")
            )
            WolenjeRedisClient.addElement(key = transactionId, value = pendingElement.toJson.toString, lifetime = WolenjeConfig.currencyUpdateFrequency)

            //Deduct from Wallet
            sqlDbService ! MerchantWalletDbUpdate(userId = merchantWallet.userId, amount = merchantWallet.amount - moneyToDeduct)

          case TransactionStatus.Failed =>
            val reason = Some(s"Withdrawal to Account Failed.")
            addTransaction(transactionId, req.userId, merchantWallet.currency, req.amount, reason, None, "", TransactionType.Outbound, TransactionStatus.Failed)
        }
        currentSender ! WithdrawalResponse(status, None)
    }
  }

  private def isRequestEmpty(request: PaymentEditRequest): Boolean = {
    request.amount.isEmpty && request.client.isEmpty && request.provider.isEmpty && request.reason.isEmpty && request.day.isEmpty
  }
}
