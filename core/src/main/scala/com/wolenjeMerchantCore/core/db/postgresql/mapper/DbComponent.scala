package com.wolenjeMerchantCore.core
package db.postgresql.mapper

import slick.jdbc.{ JdbcProfile, PostgresProfile }

trait DbComponentT {
  val driver: JdbcProfile

  import driver.api._

  val db: Database
}

trait PostgresDbConnection extends DbComponentT {
  val driver = PostgresProfile
  import driver.api.Database

  val db: Database = Database.forConfig("wolenje.postgres.db")
}