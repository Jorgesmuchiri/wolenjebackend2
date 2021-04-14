package com.wolenjeMerchantCore.core
package db.postgresql.mapper

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import slick.lifted.ProvenShape

import com.wolenjeMerchantCore.core.db.postgresql.service.PostgresqlDbService.AuthRowObject
import com.wolenjeMerchantCore.core.util.WolenjeEnum.AuthEnvironment

case class AuthRow(
  apiKey: String,
  environment: Byte
) {
  def toRowObject: AuthRowObject = AuthRowObject(
    apiKey      = apiKey,
    environment = AuthEnvironment.apply(environment)
  )
}

private[mapper] trait AuthMappingT {

  this: DbComponentT =>

  import driver.api._

  class AuthMapping(tag: Tag) extends Table[AuthRow](tag, "auth") {
    def apiKey: Rep[String]    = column[String]("api_key")
    def environment: Rep[Byte] = column[Byte]("auth_env")
    def * : ProvenShape[AuthRow] = (
      apiKey,
      environment
    ) <> (AuthRow.tupled, AuthRow.unapply)
  }

  val authInfo: TableQuery[AuthMapping] = TableQuery[AuthMapping]
}

private[mapper] trait AuthMapperT extends AuthMappingT {
  this: DbComponentT =>

  import driver.api._

  def fetchAll: Future[List[AuthRowObject]] = db.run(authInfo.to[List].result).map(x => x.map(_.toRowObject))
}

object AuthMapper extends AuthMapperT with PostgresDbConnection
