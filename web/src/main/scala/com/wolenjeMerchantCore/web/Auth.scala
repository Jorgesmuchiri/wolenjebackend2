package com.wolenjeMerchantCore.web

import com.wolenjeMerchantCore.core.db.postgresql.cache.{MerchantDbCache}

object Auth {
  case class LoginRequest(
    username: String,
    pin: String
 )

  case class LoginResponse(
    userId: Option[Int],
    errorMessage: Option[String]
  )

  def login(req: LoginRequest): LoginResponse = {
    MerchantDbCache.findByUsername(req.username) match {
      case None           =>
        LoginResponse(
          userId       = None,
          errorMessage = Some("Username not found")
        )
      case Some(merchant) =>
        if (merchant.password.equals(req.pin)) {
          LoginResponse(
            userId       = Some(merchant.id),
            errorMessage = None
          )
        } else LoginResponse(
          userId       = None,
          errorMessage = Some("Invalid pin provided")
        )
    }
  }
}
