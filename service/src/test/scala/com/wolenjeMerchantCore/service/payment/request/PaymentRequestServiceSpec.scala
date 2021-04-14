package com.wolenjeMerchantCore.service.payment.request

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.model.{HttpRequest, StatusCodes}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.wolenjeMerchantCore.core.db.postgresql.cache.MerchantDbCacheT
import com.wolenjeMerchantCore.core.db.postgresql.service.PostgresqlDbService
import com.wolenjeMerchantCore.core.db.postgresql.service.PostgresqlDbService._
import com.wolenjeMerchantCore.core.util.WolenjeEnum.{AccountStatus, Provider}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.Future

class PaymentRequestServiceSpec extends TestKit(ActorSystem("MySpec"))
with ImplicitSender
with WordSpecLike
with Matchers
with BeforeAndAfterAll {

  val mysqlDbProbe = TestProbe()

  object TestMerchantDbCache extends MerchantDbCacheT {
    override def findByUserId(userId: Int): Option[PostgresqlDbService.MerchantRowObject] =
      if(userId == 24) {
        Some(
          MerchantRowObject(
            id              = 24,
            name            = "KTO industries",
            accountNumber   = "123456",
            accountStatus   = AccountStatus.Active,
            emailAddress    = "",
            accountProvider = Provider.Mpesa,
            password        = "0000"
          )
        )
      }  else None

    override def findByUsername(username: String): Option[MerchantRowObject] =
      username match {
        case "Recipient" =>
          Some(
            MerchantRowObject(
              id              = 40,
              name            = "Recipient",
              accountNumber   = "987654",
              emailAddress    = "",
              accountStatus   = AccountStatus.Active,
              accountProvider = Provider.Mpesa,
              password        = "0000"
            )
          )

        case  _=> None
      }
  }

  val paymentService = system.actorOf(Props(new PaymentRequestService{
    override def sqlDbService: ActorRef         = mysqlDbProbe.ref
    override def getMerchantDbCache             = TestMerchantDbCache
    override def getMerchantWallet(userId: Int) = Future.successful(
      userId match {
        case 24 =>
          Some(
            MerchantWalletRowObject(
              userId    = userId,
              currency  = "KES",
              amount    = 94000
            )
          )
        case 40 =>
          Some(
            MerchantWalletRowObject(
              userId    = userId,
              currency  = "NGN",
              amount    = 5000
            )
          )

        case _ => None
      }
    )

    override def sendHttpRequest(req: HttpRequest): Future[HttpResponse] = Future successful HttpResponse(
      status = StatusCodes.OK,
      data   = """{"status":"PendingConfirmation","requestType":"B2B","description":"Successful"}"""
    )
  }))

  /*"Payment Request Service" must {
    "Process Disbursement List" in {
      val paymentList = PaymentServiceRequest(
        userId = 24,
        requests = List(
          PaymentRequest(
            clientAccount = "+254712345678",
            amount        = 70000,
            provider      = Provider.Mpesa,
            reason        = Some("Paying Employee 1"),
            repeat        = true
          )
        )
      )

      paymentService ! paymentList

      mysqlDbProbe.expectMsg(
        MerchantPaymentInsertDbRequest(
          userId      = 24,
          client      = "+254712345678",
          amount      = 70000,
          currency    = "KES",
          provider    = Provider.Mpesa,
          repeat      = true,
          reason      = Some("Paying Employee 1")
        )
      )

      expectMsg(
        PaymentServiceResponse(
          status         = TransactionStatus.Completed,
          reason         = None,
          invalidEntries = None
        )
      )

      mysqlDbProbe.expectNoMessage(200 millis)
    }
    "Edit an existing payment" in {
      val paymentEditRequest = PaymentEditRequest(
        paymentId = 360,
        client    = None,
        amount    = Some(90000),
        provider  = None,
        reason    = None,
        repeat    = None
      )

      paymentService ! paymentEditRequest

      mysqlDbProbe.expectMsg(
        MerchantPaymentEntryDbUpdate(
          paymentId = 360,
          amount    = 90000,
          client    = "+254712345678",
          provider  = Provider.Mpesa,
          reason    = Some(""),
          repeat    = true
        )
      )

      expectMsg(
        PaymentEditResponse (
          status      = TransactionStatus.Success,
          description = "Transaction(s) accepted for updating"
        )
      )

      mysqlDbProbe.expectNoMessage(200 millis)
    }
    "Complete a wallet Transfer" in {
      val walletRequest = WalletTransferRequest(
        recipientName = "Recipient",
        userId        = 24,
        amount        = 20000
      )

      paymentService ! walletRequest

      mysqlDbProbe.expectMsg(
        MerchantWalletDbUpdate(
          userId = 40,
          amount = 75000
        )
      )

      mysqlDbProbe.expectMsg(
        MerchantWalletDbUpdate(
          userId = 24,
          amount = 74000
        )
      )

      val transactionEntry = mysqlDbProbe.expectMsgType[TransactionDbEntry]

      transactionEntry should be (
        TransactionDbEntry(
          userId          = 24,
          transactionId   = transactionEntry.transactionId,
          transactionType = TransactionType.Outbound,
          amount          = 20000,
          processingFee   = Some(0),
          currency        = "KES",
          status          = TransactionStatus.Success,
          providerRefId   = "",
          reason          = Some("Wallet Transfer")
        )
      )

      mysqlDbProbe.expectMsg(
        TransactionDbEntry(
          userId          = 40,
          transactionId   = transactionEntry.transactionId,
          transactionType = TransactionType.Inbound,
          amount          = 70000,
          processingFee   = None,
          currency        = "NGN",
          status          = TransactionStatus.Success,
          providerRefId   = "",
          reason          = Some("Wallet Transfer")
        )
      )

      expectMsg(
        WalletTransferResponse(
          status      = TransactionStatus.Success,
          description = None
        )
      )
      mysqlDbProbe.expectNoMessage(200 millis)
    }
    "Withdraw funds successfully" in {
      val request = WithdrawalRequest(
        userId = 24,
        amount = 23990
      )

      paymentService ! request
      val transactionEntry = mysqlDbProbe.expectMsgType[TransactionDbEntry]

      transactionEntry shouldBe TransactionDbEntry(
        transactionId   = transactionEntry.transactionId,
        userId          = 24,
        currency        = "KES",
        amount          = 23990,
        transactionType = TransactionType.Outbound,
        status          = TransactionStatus.PendingConfirmation,
        providerRefId   = "",
        processingFee   = Some(10),
        reason          = Some("Withdrawal to Account")
      )

      mysqlDbProbe.expectMsg(
        MerchantWalletDbUpdate(
          userId = 24,
          amount = 70000
        )
      )

      expectMsg(
        WithdrawalResponse(
          status = TransactionStatus.PendingConfirmation,
          reason = Some("Successful")
        )
      )

      mysqlDbProbe.expectNoMessage(200 millis)
    }
  }*/
}
