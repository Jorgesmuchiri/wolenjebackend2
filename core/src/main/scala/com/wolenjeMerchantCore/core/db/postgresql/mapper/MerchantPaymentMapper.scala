package com.wolenjeMerchantCore.core.db.postgresql.mapper

import com.wolenjeMerchantCore.core.db.postgresql.service.PostgresqlDbService.{MerchantPaymentRowObject, MerchantPaymentEntryDbUpdate, MerchantPaymentInsertDbRequest, PostgresqlQueryDbResponse}
import com.wolenjeMerchantCore.core.util.WolenjeEnum.Provider
import slick.lifted.ProvenShape
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.joda.time.DateTime

case class MerchantPaymentRow(
  id: Int,
  userId: Int,
  amount: BigDecimal,
  provider: Byte,
  client: String,
  reason: String,
  day: Int,
  month: Int,
  hour: Int
) {
  def toRowObject: MerchantPaymentRowObject = MerchantPaymentRowObject(
    id       = id,
    userId   = userId,
    amount   = amount,
    provider = Provider.apply(provider),
    client   = client,
    reason   = reason match {
      case "" => None
      case x  => Some(x)
    },
    day      = day,
    month    = month,
    hour     = hour
  )
}

private[mapper] trait MerchantPaymentMappingT {

  this: DbComponentT =>

  import driver.api._

  class MerchantPaymentMapping(tag: Tag) extends Table[MerchantPaymentRow](tag, "merchant_payment") {
    def id: Rep[Int]            = column[Int]("id")
    def userId: Rep[Int]        = column[Int]("user_id")
    def amount: Rep[BigDecimal] = column[BigDecimal]("amount")
    def provider: Rep[Byte]     = column[Byte]("provider")
    def client: Rep[String]     = column[String]("client")
    def reason: Rep[String]     = column[String]("reason")
    def day: Rep[Int]           = column[Int]("day")
    def month: Rep[Int]         = column[Int]("month")
    def hour: Rep[Int]          = column[Int]("hour")

    def * : ProvenShape[MerchantPaymentRow] = (
      id,
      userId,
      amount,
      provider,
      client,
      reason,
      day,
      month,
      hour
    ) <> (MerchantPaymentRow.tupled, MerchantPaymentRow.unapply)
  }

  val merchantPaymentInfo: TableQuery[MerchantPaymentMapping] = TableQuery[MerchantPaymentMapping]
}

private[mapper] trait MerchantPaymentMapperT extends MerchantPaymentMappingT {
  this: DbComponentT =>

  import driver.api._

  def findById(paymentId: Int): Future[Option[MerchantPaymentRowObject]] = 
  db.run(merchantPaymentInfo.filter(x => x.id === paymentId).result.headOption).map(result =>
    result match {
      case None    => None
      case Some(x) => Some(x.toRowObject)
    }
  )

  def fetchRecords: Future[List[MerchantPaymentRowObject]] = {
    val today = DateTime.now
    db.run(merchantPaymentInfo.filter(
      x => x.day === today.getDayOfMonth && x.hour === today.getHourOfDay && (x.month === today.getMonthOfYear || x.month === 0)
    ).to[List].result).map(result => result.map(_.toRowObject))
  }

  def insertNewRecord(req: MerchantPaymentInsertDbRequest): Future[PostgresqlQueryDbResponse] = {
    db.run(merchantPaymentInfo += MerchantPaymentRow(
      id       = 0,
      userId   = req.userId,
      amount   = req.amount,
      provider = req.provider.id.toByte,
      client   = req.client,
      reason   = req.reason.getOrElse(""),
      day      = req.day,
      month    = req.month,
      hour     = req.hour
    )) map  { rowsAffected => PostgresqlQueryDbResponse(rowsAffected == 1) }
  }

  def deleteRecord(paymentId: Int, userId: Int): Future[PostgresqlQueryDbResponse] =
    db.run(merchantPaymentInfo.filter(x => x.id === paymentId && x.userId === userId).delete) map  { rowsAffected => PostgresqlQueryDbResponse(rowsAffected == 1) }

  def updateRecord(req: MerchantPaymentEntryDbUpdate): Future[PostgresqlQueryDbResponse] = {
    val q = for { x <- merchantPaymentInfo if x.id === req.paymentId }
      yield (x.amount, x.provider, x.client, x.reason, x.day, x.month, x.hour)

   db.run(q.update(req.amount, req.provider.id.toByte, req.client, req.reason.getOrElse(""), req.day, req.month, req.hour)) map { rowsAffected => PostgresqlQueryDbResponse(rowsAffected == 1) }
  }
}

object MerchantPaymentMapper extends MerchantPaymentMapperT with PostgresDbConnection
