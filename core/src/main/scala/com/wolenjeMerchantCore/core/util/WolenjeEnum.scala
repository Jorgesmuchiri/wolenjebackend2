package com.wolenjeMerchantCore.core.util

object WolenjeEnum {

  object AccountStatus extends Enumeration {
    val PendingValidation = Value(1)
    val Active            = Value(2)
    val Suspended         = Value(3)
  }

  object AuthEnvironment extends Enumeration {
    val Sandbox    = Value(1)
    val Production = Value(2)
  }

  object Provider extends Enumeration {
    val Mpesa  = Value(1)
    val Telkom = Value(2)
    val Airtel = Value(3)
  }

  object ProviderTelco extends Enumeration {
    val MPESA_B2C     = Value(1)
    val TELKOM_B2C    = Value(2)
    val AIRTEL_B2C    = Value(3)
    val COOP_IFT      = Value(4)
    val COOP_INF      = Value(5)
    val COOP_PESALINK = Value(6)
  }

  object RequestType extends Enumeration {
    val B2B  = Value(1)
    val B2C  = Value(2)
  }

  object TransactionType extends Enumeration {
    val Inbound   = Value(1)
    val Outbound  = Value(2)
  }

  object TransactionStatus extends Enumeration {
    val Completed           = Value(1)
    val Failed              = Value(2)
    val PendingConfirmation = Value(3)
    val ApplicationError    = Value(4)
    val Success             = Value(5)
  }

}
