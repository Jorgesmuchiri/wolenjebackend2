package com.wolenjeMerchantCore.service.payment.notification

import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.wolenjeMerchantCore.core.config.WolenjeConfig
import com.wolenjeMerchantCore.core.db.postgresql.cache._
import com.wolenjeMerchantCore.core.db.postgresql.service.PostgresqlDbService
import com.wolenjeMerchantCore.core.email.EmailRequestService
import com.wolenjeMerchantCore.core.firebase.CloudMessagingService
import com.wolenjeMerchantCore.core.firebase.CloudMessagingService.SendAccountTopupNotification
import com.wolenjeMerchantCore.core.util.WolenjeEnum.{TransactionStatus, TransactionType}
import com.wolenjeMerchantCore.core.util.WolenjeUtil
import com.wolenjeMerchantCore.service.util.WalletT

import scala.util.{Failure, Success}

import EmailRequestService._
import PostgresqlDbService._
import PaymentNotificationService._

object PaymentNotificationService {
  case class PaymentNotificationRequest(
     account: String, //Account Name or Paybill/Acc No attached to account
     amount: BigDecimal,
     currency: String,
     providerRefId: String
  )
}

class PaymentNotificationService extends Actor with ActorLogging with WalletT {
  
  def sqlDbService       = context.actorOf(Props[PostgresqlDbService])
  val emailService       = createEmailService
  def createEmailService = context.actorOf(Props[EmailRequestService])

  val cloudMessagingService       = createCloudMessagingService
  def createCloudMessagingService = context.actorOf(Props[CloudMessagingService])

  implicit val timeout   = Timeout(WolenjeConfig.httpRequestTimeout)
  
  def getMerchantDbCache: MerchantDbCacheT = MerchantDbCache

  override def receive = {
    case req: PaymentNotificationRequest =>
      log.info(s"processing $req")
      getMerchantDbCache.findByUsername(req.account) match {
        case None           =>
          getMerchantDbCache.findByNumber(req.account) match {
            case None           => log.error(s"Got a notification for non-existing merchant: ${req.account} --request: $req")
            case Some(merchant) => processNotification(req, merchant)
          }
        case Some(merchant) => processNotification(req, merchant)
      }
  }

  private def processNotification(
    req: PaymentNotificationRequest,
    merchant: MerchantRowObject
  ) = {
    WolenjeUtil.currencyExists(req.currency) match {
      case false => log.error(s"Got invalid currency: ${req.currency} --request: $req")
      case true  =>
        getMerchantWallet(merchant.id) onComplete {
          case Failure(exception)            => log.error(s"Error Encountered while processing $req: {}", Some(exception))
          case Success(None)                 => 
            createMerchantWallet(merchant = merchant, providerRefId = req.providerRefId, currency = req.currency, amount = req.amount)
          
            case Success(Some(merchantWallet)) =>
            val processingFee  = WolenjeUtil.getProcessingFee(merchantWallet.currency, req.amount)
            val newAmount      = WolenjeUtil.convertAmount(
              amount        = req.amount,
              currency      = req.currency,
              givenCurrency = merchantWallet.currency
            )
            //Save it and credit wallet
            val newBalance = merchantWallet.amount + newAmount - processingFee.getOrElse(0)
            sqlDbService ! MerchantWalletDbUpdate(
              userId = merchant.id,
              amount = newBalance
            )
            sqlDbService ! TransactionRowObject(
              transactionId   = WolenjeUtil.generateTransactionId,
              userId          = merchant.id,
              currency        = merchantWallet.currency,
              amount          = req.amount,
              transactionType = TransactionType.Inbound,
              providerRefId   = req.providerRefId,
              status          = TransactionStatus.Completed,
              processingFee   = processingFee,
              reason          = Some("Wallet Topup")
            )

            sendTopUpNotification(
              merchantEmail = merchant.emailAddress,
              merchantName  = merchant.name,
              newBalance    = newBalance,
              userId        = merchant.id,
              topupAmount   = req.amount,
              currency      = merchantWallet.currency
            )
        }
    }
  }

  protected def createMerchantWallet(
    currency: String,
    amount: BigDecimal,
    merchant: MerchantRowObject,
    providerRefId: String
  ) = {
    val processingFee  = WolenjeUtil.getProcessingFee(currency, amount)
    val newAmount      = WolenjeUtil.convertAmount(
      amount        = amount,
      currency      = currency,
      givenCurrency = currency
    )
    //Save it and credit wallet
    val newBalance = newAmount - processingFee.getOrElse(0)
    sqlDbService ! MerchantWalletInsertDbRequest(
      userId   = merchant.id,
      amount   = newBalance,
      currency = currency,
    )
    sqlDbService ! TransactionRowObject(
      transactionId   = WolenjeUtil.generateTransactionId,
      userId          = merchant.id,
      currency        = currency,
      amount          = amount,
      transactionType = TransactionType.Inbound,
      providerRefId   = providerRefId,
      status          = TransactionStatus.Completed,
      processingFee   = processingFee,
      reason          = Some("Wallet Topup")
    )

    sendTopUpNotification(
      merchantEmail = merchant.emailAddress,
      merchantName  = merchant.name,
      userId        = merchant.id,
      newBalance    = newBalance,
      topupAmount   = amount,
      currency      = currency
    )
  }

  protected def sendTopUpNotification(
    merchantName: String,
    merchantEmail: String,
    userId: Int,
    newBalance: BigDecimal,
    topupAmount: BigDecimal,
    currency: String
  ) = {
    //Send Email
    val emailReq = EmailServiceRequest(
      message   = s"Hi ${merchantName}, you have topped up ${currency} ${topupAmount}. New balance is ${currency} ${newBalance} \n Thank you",
      subject   = "Wolenje Merchant: Topup Notification",
      recipient = merchantEmail
    )
    (emailService ? emailReq).mapTo[EmailServiceResponse] map { response =>
      response.status match {
        case TransactionStatus.Success => //Do nothing
        case _                         =>
          log.error(s"Error sending email $emailReq ---response: $response")
      }
    }

    //Send Firebase Cloud Notification
    val cloudMessageRequest = SendAccountTopupNotification(
      userId      = userId,
      newBalance  = newBalance,
      topupAmount = topupAmount,
      currency    = currency
    )

    cloudMessagingService ! cloudMessageRequest
  }
}
