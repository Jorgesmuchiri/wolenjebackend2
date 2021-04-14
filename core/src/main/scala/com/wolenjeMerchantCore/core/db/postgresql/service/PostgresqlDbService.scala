package com.wolenjeMerchantCore.core.db.postgresql.service

import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.{Actor, ActorLogging}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.pattern.pipe
import com.wolenjeMerchantCore.core.config.WolenjeConfig
import com.wolenjeMerchantCore.core.db.postgresql.mapper._
import com.wolenjeMerchantCore.core.util.WolenjeEnum.{AccountStatus, AuthEnvironment, Provider, TransactionStatus, TransactionType}
import com.wolenjeMerchantCore.core.util.WolenjeJsonProtocol
import spray.json.DefaultJsonProtocol

import scala.util.{Failure, Success}

object PostgresqlDbService extends DefaultJsonProtocol with SprayJsonSupport {
  case class AuthRowObject(
    apiKey: String,
    environment: AuthEnvironment.Value
  )
  case object AuthFetchDbQuery

  case class CurrencyDbEntry(
    rate: BigDecimal,
    currency: String
  )

  case class MerchantRowObject(
    id: Int,
    name: String,
    accountNumber: String /*Mpesa Paybill or Bank Account*/,
    emailAddress: String,
    accountStatus: AccountStatus.Value,
    accountProvider: Provider.Value,
    password: String
  )
  case class MerchantInsertDbRequest(
    name: String,
    accountNumber: String /*Mpesa Paybill or Bank Account*/,
    emailAddress: String,
    accountProvider: Provider.Value,
    numRetries: Int = 0
  )

  case class MerchantUpdateDbQuery(
    merchantId: Int,
    password: String,
    numRetries: Int = 0
  )
  case object MerchantFetchDbQuery

  case class MerchantPaymentInsertDbRequest(
    userId: Int,
    client/*Phone Number or Account*/: String,
    amount: BigDecimal,
    provider: Provider.Value,
    reason: Option[String],
    month: Int,
    day: Int,
    hour: Int,
    numRetries: Int = 0
  )

  case class MerchantPaymentDeleteDbQuery(
    paymentId: Int,
    userId: Int,
    numRetries: Int = 0
  )
  case class MerchantPaymentEntryDbUpdate(
    paymentId: Int,
    client: String,
    amount: BigDecimal,
    provider: Provider.Value,
    reason: Option[String],
    month: Int,
    day: Int,
    hour: Int,
    numRetries: Int = 0
  )

  case class MerchantPaymentRowObject(
    id: Int,
    userId: Int,
    client: String,
    amount: BigDecimal,
    provider: Provider.Value,
    reason: Option[String],
    month: Int,
    hour: Int,
    day: Int
  )
  case class MerchantPaymentFindDbQuery(paymentId: Int)
  case object MerchantPaymentFetchDbQuery

  case class MerchantWalletInsertDbRequest(
    userId: Int,
    amount: BigDecimal,
    currency: String,
    numRetries: Int = 0
  )
  case class MerchantWalletRowObject(
    userId: Int,
    currency: String,
    amount: BigDecimal
  )
  case class MerchantWalletDbUpdate(
    userId: Int,
    amount: BigDecimal,
    numRetries: Int = 0
  )
  case class MerchantWalletFindDbQuery(userId: Int)
  case object MerchantWalletFetchDbQuery

  case class TransactionRowObject(
    userId: Int,
    currency: String,
    amount: BigDecimal,
    transactionType: TransactionType.Value,
    transactionId: String,
    status: TransactionStatus.Value,
    providerRefId: String,
    processingFee: Option[BigDecimal],
    reason: Option[String],
    numRetries: Int = 0
  )

  case class UserTokenCreateDbQuery(
    id: Int,
    userId: Int,
    token: String,
    numRetries: Int = 0
  )
  case class UserTokenRowObject(
    id: Int,
    userId: Int,
    token: String
  )
  case class UserTokenFindDbQuery(userId: Int)

  private[postgresql] case class PostgresqlQueryDbResponse(success: Boolean)
}

class PostgresqlDbService extends Actor with ActorLogging {

  val maxNumRetries: Int = WolenjeConfig.mysqlMaxRetryNum

  import PostgresqlDbService._

  override def receive: Receive = {
    case AuthFetchDbQuery =>
      log.info(s"Processing Auth Fetch Db Query")
      AuthMapper.fetchAll.mapTo[List[AuthRowObject]] pipeTo sender

    case MerchantFetchDbQuery =>
      log.info(s"Processing Merchant Fetch DB Query")
      MerchantMapper.fetchAll.mapTo[List[MerchantRowObject]] pipeTo sender

    case MerchantPaymentFetchDbQuery =>
      log.info(s"Processing Merchant Payment Fetch DB Query")
      MerchantPaymentMapper.fetchRecords.mapTo[List[MerchantPaymentRowObject]] pipeTo sender

    case MerchantWalletFetchDbQuery =>
      log.info(s"Processing Merchant Wallet Fetch DB Query")
      MerchantWalletMapper.fetchAll.mapTo[List[MerchantWalletRowObject]] pipeTo sender

    case req: MerchantWalletFindDbQuery =>
      log.info(s"Processing $req")
      MerchantWalletMapper.findWallet(req.userId).mapTo[Option[MerchantWalletRowObject]] pipeTo sender

    case req: MerchantInsertDbRequest =>
      log.info(s"Processing $req")
      if (req.numRetries <= maxNumRetries) {
        MerchantMapper.addMerchant(req).mapTo[PostgresqlQueryDbResponse] onComplete {
          case Failure(exception) => log.error(s"Retry: ${req.numRetries} for merchant $req. Error occurred: ", Some(exception))
          case Success(value)     =>
            if(!value.success) {
              self ! req.copy(numRetries = req.numRetries + 1)
            } else {
              log.info(s"Merchant $req added successfully after ${req.numRetries} retries")
            }
        }
      }

    case req: MerchantUpdateDbQuery =>
      log.info(s"Processing $req")
      if (req.numRetries <= maxNumRetries) {
        MerchantMapper.updateMerchant(req.password, req.merchantId).mapTo[PostgresqlQueryDbResponse] onComplete {
          case Failure(exception) => log.error(s"Retry: ${req.numRetries} for merchant $req. Error occurred: ", Some(exception))
          case Success(value)     =>
            if(!value.success) {
              self ! req.copy(numRetries = req.numRetries + 1)
            } else {
              log.info(s"Merchant $req updated successfully after ${req.numRetries} retries")
            }
        }
      }

    case req: MerchantPaymentInsertDbRequest =>
      log.info(s"Processing $req")
      if (req.numRetries <= maxNumRetries) {
        MerchantPaymentMapper.insertNewRecord(req).mapTo[PostgresqlQueryDbResponse] onComplete {
          case Failure(exception) => log.error(s"Retry: ${req.numRetries} for transaction $req. Error occurred: ", Some(exception))
          case Success(value)     =>
            if(!value.success) {
              self ! req.copy(numRetries = req.numRetries + 1)
            } else {
              log.info(s"Record $req added successfully after ${req.numRetries} retries")
            }
        }
      }

    case req: MerchantPaymentEntryDbUpdate =>
      log.info(s"Processing $req")
      if (req.numRetries <= maxNumRetries) {
        MerchantPaymentMapper.updateRecord(req).mapTo[PostgresqlQueryDbResponse] onComplete {
          case Failure(exception) => log.error(s"Retry: ${req.numRetries} for transaction $req. Error occurred: ", Some(exception))
          case Success(value)     =>
            if(!value.success) {
              self ! req.copy(numRetries = req.numRetries + 1)
            } else {
              log.info(s"Record $req added successfully after ${req.numRetries} retries")
            }
        }
      }

    case req: MerchantPaymentDeleteDbQuery =>
      log.info(s"Processing $req")
      if (req.numRetries <= maxNumRetries) {
        MerchantPaymentMapper.deleteRecord(paymentId = req.paymentId, userId = req.userId).mapTo[PostgresqlQueryDbResponse] onComplete {
          case Failure(exception) => log.error(s"Retry: ${req.numRetries} for transaction $req. Error occurred: ", Some(exception))
          case Success(value)     =>
            if(!value.success) {
              self ! req.copy(numRetries = req.numRetries + 1)
            } else {
              log.info(s"Record $req added successfully after ${req.numRetries} retries")
            }
        }
      }

    case req: MerchantWalletInsertDbRequest =>
      log.info(s"Processing $req")
      if (req.numRetries <= maxNumRetries) {
        MerchantWalletMapper.insertNewRecord(req).mapTo[PostgresqlQueryDbResponse] onComplete {
          case Failure(exception) => log.error(s"Retry: ${req.numRetries} for transaction $req. Error occurred: ", Some(exception))
          case Success(value)     =>
            if(!value.success) {
              self ! req.copy(numRetries = req.numRetries + 1)
            } else {
              log.info(s"Record $req added successfully after ${req.numRetries} retries")
            }
        }
      }

    case req: MerchantPaymentDeleteDbQuery =>
      log.info(s"Processing $req")
      if (req.numRetries <= maxNumRetries) {
        MerchantPaymentMapper.deleteRecord(req.paymentId, req.userId).mapTo[PostgresqlQueryDbResponse] onComplete {
          case Failure(exception) => log.error(s"Retry: ${req.numRetries} for transaction $req. Error occurred: ", Some(exception))
          case Success(value)     =>
            if(!value.success) {
              self ! req.copy(numRetries = req.numRetries + 1)
            } else {
              log.info(s"Record $req added successfully after ${req.numRetries} retries")
            }
        }
      }

    case req: MerchantPaymentEntryDbUpdate =>
      log.info(s"Processing $req")
      if (req.numRetries <= maxNumRetries) {
        MerchantPaymentMapper.updateRecord(req).mapTo[PostgresqlQueryDbResponse] onComplete {
          case Failure(exception) => log.error(s"Retry: ${req.numRetries} for transaction $req. Error occurred: ", Some(exception))
          case Success(value)     =>
            if(!value.success) {
              self ! req.copy(numRetries = req.numRetries + 1)
            } else {
              log.info(s"Record $req added successfully after ${req.numRetries} retries")
            }
        }
      }

    case req: MerchantWalletDbUpdate =>
      log.info(s"Processing $req")
      if (req.numRetries <= maxNumRetries) {
        MerchantWalletMapper.updateExistingRecord(req).mapTo[PostgresqlQueryDbResponse] onComplete {
          case Failure(exception) => log.error(s"Retry: ${req.numRetries} for transaction $req. Error occurred: ", Some(exception))
          case Success(value)     =>
            if(!value.success) {
              self ! req.copy(numRetries = req.numRetries + 1)
            } else {
              log.info(s"Record $req added successfully after ${req.numRetries} retries")
            }
        }
      }

    case req: TransactionRowObject =>
      log.info(s"Processing $req")
      if (req.numRetries <= maxNumRetries) {
        TransactionMapper.insertNewRecord(req).mapTo[PostgresqlQueryDbResponse] onComplete {
          case Failure(exception) => log.error(s"Retry: ${req.numRetries} for transaction $req. Error occurred: ", Some(exception))
          case Success(value)     =>
            if(!value.success) {
              self ! req.copy(numRetries = req.numRetries + 1)
            } else log.info(s"Record $req added successfully after ${req.numRetries} retries")
        }
      }

    case req: UserTokenCreateDbQuery =>
      if (req.numRetries <= maxNumRetries) {
        UserTokenMapper.insertNewRecord(req).mapTo[PostgresqlQueryDbResponse] onComplete {
          case Failure(exception) => log.error(s"Retry: ${req.numRetries} for transaction $req. Error occurred: ", Some(exception))
          case Success(value)     =>
            if(!value.success) {
              self ! req.copy(numRetries = req.numRetries + 1)
            } else log.info(s"Record $req added successfully after ${req.numRetries} retries")
        }
      }

    case req: MerchantPaymentFindDbQuery =>
      log.info(s"Processing $req")
      MerchantPaymentMapper.findById(req.paymentId).mapTo[Option[MerchantPaymentRowObject]] pipeTo sender

    case req: UserTokenFindDbQuery =>
      log.info(s"Processing $req")
      UserTokenMapper.findOne(req.userId).mapTo[Option[UserTokenRowObject]] pipeTo sender
  }
}
