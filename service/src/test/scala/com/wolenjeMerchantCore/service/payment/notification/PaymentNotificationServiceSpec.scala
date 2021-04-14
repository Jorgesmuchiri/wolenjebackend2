package com.wolenjeMerchantCore.service.payment.notification

import scala.concurrent.Future
import scala.concurrent.duration._
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.wolenjeMerchantCore.core.db.postgresql.cache.{MerchantDbCacheT}
import com.wolenjeMerchantCore.core.db.postgresql.service.PostgresqlDbService
import com.wolenjeMerchantCore.core.db.postgresql.service.PostgresqlDbService._
import com.wolenjeMerchantCore.core.util.WolenjeEnum.{AccountStatus, Provider, TransactionStatus, TransactionType}
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.wolenjeMerchantCore.core.email.EmailRequestService.{EmailServiceRequest, EmailServiceResponse}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import PaymentNotificationService._

class PaymentNotificationServiceSpec extends TestKit(ActorSystem("MySpec"))
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  val emailServiceTestProbe = TestProbe()
  val mysqlDbServiceTestProbe = TestProbe()

  object TestMerchantDbCache extends MerchantDbCacheT {

    override def findByUsername(username: String): Option[MerchantRowObject] =
      if(username.equals("test_merchant")) {
        Some(
          MerchantRowObject(
            id              = 1,
            name            = "test_merchant",
            accountNumber   = "12345",
            accountStatus   = AccountStatus.Active,
            emailAddress    = "sample@gmail.com",
            accountProvider = Provider.Mpesa,
            password        = "0000"
          )
        )
      } else None

    override def findByNumber(account: String) : Option[MerchantRowObject] =
      if(account.equals("12345")) {
        Some(
          MerchantRowObject(
            id              = 1,
            name            = "test_merchant",
            accountNumber   = "12345",
            accountStatus   = AccountStatus.Active,
            emailAddress    = "sample@gmail.com",
            accountProvider = Provider.Mpesa,
            password        = "0000"
          )
        )
      } else None
  }

  val paymentNotificationService = system.actorOf(Props(new PaymentNotificationService {
    //override def createMysqlDbService               = mysqlDbServiceTestProbe.ref
    override def createEmailService                   = emailServiceTestProbe.ref
    override def getMerchantDbCache: MerchantDbCacheT = TestMerchantDbCache
    override def getMerchantWallet(userId: Int)       = Future.successful({
      if(userId.equals(1)) {
        Some(MerchantWalletRowObject(
          userId    = 1,
          currency  = "KES",
          amount    = 500
        ))
      } else None
    })
  }))

  val validNotification = PaymentNotificationRequest(
    account       = "test_merchant",
    amount        = 10000,
    currency      = "KES",
    providerRefId = "MPES13"
  )

  /*"Payment Notification Service" must {
    "handle notifications with an invalid clientAccount" in {
      paymentNotificationService ! validNotification.copy(account = "InvalidMerchant")
      mysqlDbServiceTestProbe.expectNoMessage(200 millis)
    }

    "handle notifications with a username based clientAccount" in {
      paymentNotificationService ! validNotification
      mysqlDbServiceTestProbe.expectMsg(
        MerchantWalletDbUpdate(
          userId = 1,
          amount = 10490
        )
      )

      val transaction = mysqlDbServiceTestProbe.expectMsgType[TransactionDbEntry]
      transaction shouldBe TransactionDbEntry(
        transactionId   = transaction.transactionId,
        userId          = 1,
        currency        = "KES",
        amount          = 10000,
        transactionType = TransactionType.Inbound,
        providerRefId   = validNotification.providerRefId,
        status          = TransactionStatus.Completed,
        processingFee   = Some(10),
        reason          = Some("Wallet Topup")
      )

      mysqlDbServiceTestProbe.expectNoMessage(200 millis)
    }

    "handle notifications with an account based client Account" in {
      paymentNotificationService ! validNotification.copy(account = "12345")
      mysqlDbServiceTestProbe.expectMsg(
        MerchantWalletDbUpdate(
          userId = 1,
          amount = 10490
        )
      )

      val transaction = mysqlDbServiceTestProbe.expectMsgType[TransactionDbEntry]
      transaction shouldBe TransactionDbEntry(
        transactionId   = transaction.transactionId,
        userId          = 1,
        currency        = "KES",
        amount          = 10000,
        transactionType = TransactionType.Inbound,
        providerRefId   = validNotification.providerRefId,
        status          = TransactionStatus.Completed,
        processingFee   = Some(10),
        reason          = Some("Wallet Topup")
      )

      val emailReq = EmailServiceRequest(
        recipient = "sample@gmail.com",
        subject   = "Wolenje Merchant: Topup Notification",
        message   = "Hi test_merchant, you have topped up KES 10000. New balance is KES 10490 \n Thank you"
      )
      emailServiceTestProbe.expectMsg(emailReq)

      emailServiceTestProbe.reply(EmailServiceResponse(
        request = emailReq,
        status  = TransactionStatus.Success
      ))

      emailServiceTestProbe.expectNoMessage(200 millis)
      mysqlDbServiceTestProbe.expectNoMessage(200 millis)
    }

  }*/
}
