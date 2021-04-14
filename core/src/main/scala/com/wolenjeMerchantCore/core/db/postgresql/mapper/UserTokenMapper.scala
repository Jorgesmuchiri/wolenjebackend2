package com.wolenjeMerchantCore.core
package db.postgresql.mapper

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import slick.lifted.ProvenShape

import com.wolenjeMerchantCore.core.db.postgresql.service.PostgresqlDbService.{ UserTokenCreateDbQuery, UserTokenRowObject, PostgresqlQueryDbResponse }

private[mapper] trait UserTokenMappingT {

  this: DbComponentT =>

  import driver.api._

  class UserTokenMapping(tag: Tag) extends Table[UserTokenRowObject](tag, "user_token") {
    def id: Rep[Int]       = column[Int]("id")
    def userId: Rep[Int]   = column[Int]("user_id")
    def token: Rep[String] = column[String]("token")
    def * : ProvenShape[UserTokenRowObject] = (
      id,
      userId,
      token
    ) <> (UserTokenRowObject.tupled, UserTokenRowObject.unapply)
  }

  val userTokenInfo: TableQuery[UserTokenMapping] = TableQuery[UserTokenMapping]
}

private[mapper] trait UserTokenMapperT extends UserTokenMappingT {
  this: DbComponentT =>

  import driver.api._
  
  def findOne(userId: Int): Future[Option[UserTokenRowObject]] 
  = db.run(userTokenInfo.filter(_.userId === userId).result.headOption)

  def insertNewRecord(req: UserTokenCreateDbQuery) = {
      db.run(userTokenInfo += UserTokenRowObject(
          req.id,
          req.userId,
          req.token
      )) map  { rowsAffected => PostgresqlQueryDbResponse(rowsAffected == 1) }
  }
}

object UserTokenMapper extends UserTokenMapperT with PostgresDbConnection