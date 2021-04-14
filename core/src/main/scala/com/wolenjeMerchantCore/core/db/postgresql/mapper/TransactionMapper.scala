package com.wolenjeMerchantCore.core.db.postgresql.mapper

import scala.concurrent.Future
import com.wolenjeMerchantCore.core.db.postgresql.service.PostgresqlDbService
import PostgresqlDbService.{ PostgresqlQueryDbResponse, TransactionRowObject}
import com.wolenjeMerchantCore.core.util.WolenjeEnum.{TransactionStatus, TransactionType}
import slick.lifted.ProvenShape
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

case class TransactionRow(
  transactionId: String,
  userId: Int,
  currency: String,
  amount: BigDecimal,
  transactionType: Byte,
  status: Byte,
  providerRefId: String,
  processingFee: BigDecimal,
  reason: String
) {
  def toRowObject: TransactionRowObject = TransactionRowObject(
    transactionId   = transactionId,
    userId          = userId,
    currency        = currency,
    amount          = amount,
    transactionType = TransactionType.apply(transactionType),
    status          = TransactionStatus.apply(status),
    providerRefId   = providerRefId,
    processingFee   = Try(processingFee).toOption,
    reason          = Try(reason).toOption
  )
}

private[mapper] trait TransactionMappingT {

  this: DbComponentT =>

  import driver.api._

  class TransactionMapping(tag: Tag) extends Table[TransactionRow](tag, "transaction") {
    def transactionId: Rep[String]     = column[String]("transaction")
    def userId: Rep[Int]               = column[Int]("user_id")
    def currency: Rep[String]          = column[String]("currency")
    def amount: Rep[BigDecimal]        = column[BigDecimal]("amount")
    def transactionType: Rep[Byte]     = column[Byte]("transaction_type")
    def status: Rep[Byte]              = column[Byte]("status")
    def providerRefId: Rep[String]     = column[String]("provider_ref_id")
    def processingFee: Rep[BigDecimal] = column[BigDecimal]("processing_fee")
    def reason: Rep[String]            = column[String]("reason")

    def * : ProvenShape[TransactionRow] = (
      transactionId,
      userId,
      currency,
      amount,
      transactionType,
      status,
      providerRefId,
      processingFee,
      reason
    ) <> (TransactionRow.tupled, TransactionRow.unapply)
  }

  val transactionInfo: TableQuery[TransactionMapping] = TableQuery[TransactionMapping]
}

private[mapper] trait TransactionMapperT extends TransactionMappingT {
  this: DbComponentT =>

  import driver.api._

  def fetchAll: Future[List[TransactionRowObject]] = db.run(transactionInfo.to[List].result).map(x => x.map(_.toRowObject))

  def fetchTransactions(userId: Int, limit: Int):Future[List[TransactionRowObject]] = {
    db.run(transactionInfo.filter(_.userId === userId).take(limit).to[List].result).map(x => x.map(_.toRowObject))
  }

  def insertNewRecord(req: TransactionRowObject): Future[PostgresqlQueryDbResponse] = {
    db.run(transactionInfo += TransactionRow(
      transactionId   = req.transactionId,
      userId          = req.userId,
      currency        = req.currency,
      amount          = req.amount,
      transactionType = req.transactionType.id.toByte,
      status          = req.status.id.toByte,
      providerRefId   = req.providerRefId,
      processingFee   = req.processingFee.getOrElse(0),
      reason          = req.reason.getOrElse("")
    )) map  { rowsAffected => PostgresqlQueryDbResponse(rowsAffected == 1)}
  }

}

object TransactionMapper extends TransactionMapperT with PostgresDbConnection
