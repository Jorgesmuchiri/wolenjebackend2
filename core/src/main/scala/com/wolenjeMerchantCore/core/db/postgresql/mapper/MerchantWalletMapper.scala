package com.wolenjeMerchantCore.core.db.postgresql.mapper

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import slick.lifted.ProvenShape

import com.wolenjeMerchantCore.core.db.postgresql.service.PostgresqlDbService.{MerchantWalletRowObject, MerchantWalletDbUpdate, MerchantWalletInsertDbRequest, PostgresqlQueryDbResponse}

private[mapper] trait MerchantWalletMappingT {

  this: DbComponentT =>

  import driver.api._

  class MerchantWalletMapping(tag: Tag) extends Table[MerchantWalletRowObject](tag, "merchant_wallet") {
    def userId: Rep[Int]        = column[Int]("user_id")
    def amount: Rep[BigDecimal] = column[BigDecimal]("amount")
    def currency: Rep[String]   = column[String]("currency")

    def * : ProvenShape[MerchantWalletRowObject] = (
      userId,
      currency,
      amount
    ) <> (MerchantWalletRowObject.tupled, MerchantWalletRowObject.unapply)
  }

  val merchantWalletInfo: TableQuery[MerchantWalletMapping] = TableQuery[MerchantWalletMapping]
}

private[mapper] trait MerchantWalletMapperT extends MerchantWalletMappingT {
  this: DbComponentT =>

  import driver.api._

  def fetchAll: Future[List[MerchantWalletRowObject]] = db.run(merchantWalletInfo.to[List].result)

  def findWallet(userId: Int): Future[Option[MerchantWalletRowObject]] =
    db.run(merchantWalletInfo.filter(_.userId === userId).result.headOption)

  def insertNewRecord(req: MerchantWalletInsertDbRequest): Future[PostgresqlQueryDbResponse] =
    db.run(merchantWalletInfo += MerchantWalletRowObject(
      userId = req.userId,
      currency = req.currency,
      amount = req.amount
    )).map(rowsAffected => PostgresqlQueryDbResponse(rowsAffected == 1))

  def updateExistingRecord(req: MerchantWalletDbUpdate): Future[PostgresqlQueryDbResponse] = {
    db.run(merchantWalletInfo.filter(_.userId === req.userId).map(_.amount).update(req.amount)) map { rowsAffected =>
      PostgresqlQueryDbResponse(rowsAffected == 1)
    }
  }
}

object MerchantWalletMapper extends MerchantWalletMapperT with PostgresDbConnection
