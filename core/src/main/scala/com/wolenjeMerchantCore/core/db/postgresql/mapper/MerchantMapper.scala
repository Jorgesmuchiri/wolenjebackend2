package com.wolenjeMerchantCore.core
package db.postgresql.mapper

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import com.wolenjeMerchantCore.core.db.postgresql.service.PostgresqlDbService.{MerchantRowObject, MerchantInsertDbRequest, PostgresqlQueryDbResponse}
import com.wolenjeMerchantCore.core.util.WolenjeEnum.{AccountStatus, Provider}
import slick.lifted.ProvenShape

case class MerchantRow(
   id: Int,
   name: String,
   accountNumber: String,
   emailAddress: String,
   accountStatus: Byte,
   accountProvider: Byte,
   password: String
) {
  def toRowObject: MerchantRowObject = MerchantRowObject(
    id              = id,
    name            = name,
    accountNumber   = accountNumber,
    emailAddress    = emailAddress,
    accountStatus   = AccountStatus.apply(accountStatus),
    accountProvider = Provider.apply(accountProvider),
    password        = password
  )
}

private[mapper] trait MerchantMappingT {

  this: DbComponentT =>

  import driver.api._

  class MerchantMapping(tag: Tag) extends Table[MerchantRow](tag, "merchant") {
    def id: Rep[Int]               = column[Int]("id")
    def name: Rep[String]          = column[String]("name")
    def accountNumber: Rep[String] = column[String]("account_number")
    def emailAddress: Rep[String]  = column[String]("email_address")
    def accountStatus: Rep[Byte]   = column[Byte]("account_status")
    def accountProvider: Rep[Byte] = column[Byte]("provider")
    def password: Rep[String]      = column[String]("password")

    def * : ProvenShape[MerchantRow] = (
      id,
      name,
      accountNumber,
      emailAddress,
      accountStatus,
      accountProvider,
      password
    ) <> (MerchantRow.tupled, MerchantRow.unapply)
  }

  val merchantInfo: TableQuery[MerchantMapping] = TableQuery[MerchantMapping]
}

private[mapper] trait MerchantMapperT extends MerchantMappingT {
  this: DbComponentT =>

  import driver.api._

  def fetchAll: Future[List[MerchantRowObject]] = db.run(merchantInfo.to[List].result).map(x => x.map(_.toRowObject))

  def addMerchant(merchant: MerchantInsertDbRequest): Future[PostgresqlQueryDbResponse] = {
    db.run(merchantInfo += MerchantRow(
      id              = 0,
      name            = merchant.name,
      accountNumber   = merchant.accountNumber,
      emailAddress    = merchant.emailAddress,
      accountStatus   = 0,
      accountProvider = merchant.accountProvider.id.toByte,
      password        = "0"
    )).map { rowsAffected => PostgresqlQueryDbResponse(rowsAffected == 1)}
  }

  def updateMerchant(password: String, merchantId: Int): Future[PostgresqlQueryDbResponse] = {
    db.run(merchantInfo.filter(_.id === merchantId).map(_.password).update(password)) map
      { rowsAffected => PostgresqlQueryDbResponse(rowsAffected == 1)}
  }
}

object MerchantMapper extends MerchantMapperT with PostgresDbConnection