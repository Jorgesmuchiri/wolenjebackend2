package com.wolenjeMerchantCore.core.util

import com.wolenjeMerchantCore.core.config.WolenjeConfig

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.xml.Elem

object WolenjeUtil {

  case object UpdateCacheRequest

  private val currencyMap = Map[String, BigDecimal]("KES" -> 1)

  def convertAmount(
    amount: BigDecimal,
    currency: String,
    givenCurrency:String
  ): BigDecimal = {
    //Convert amount using currency to givenCurrency
    if(currency == givenCurrency) amount
    else {
      val currencyEntry      = currencyMap.get(currency)
      val givenCurrencyEntry = currencyMap.get(givenCurrency)

      (currencyEntry, givenCurrencyEntry) match {
        case (Some(rate), Some(givenCurrencyRate)) => amount / rate * givenCurrencyRate
        case _ => 0
      }
    }
  }

  def generateTransactionId = java.util.UUID.randomUUID + org.joda.time.DateTime.now.hashCode.toString

  def currencyExists(currency: String) = currencyMap.exists(_._1 == currency)

  def validateAmount(amount: BigDecimal, currency: String): Boolean = {
    currencyMap.get(currency) match {
      case None       => false
      case Some(rate) =>
        if (currency == WolenjeConfig.defaultCurrency) {
          amount >= WolenjeConfig.defaultMinTransactionValue && amount <= WolenjeConfig.defaultMaxTransactionValue
        } else {
          val convertedAmount = convertAmount(
            amount        = amount,
            currency      = currency,
            givenCurrency = WolenjeConfig.defaultCurrency
          )
          convertedAmount >= WolenjeConfig.defaultMinTransactionValue && convertedAmount <= WolenjeConfig.defaultMaxTransactionValue
        }
    }
  }

  def getProcessingFee(currency: String, amount: BigDecimal): Option[BigDecimal] = {
    val convertedAmount = convertAmount(
      amount        = amount,
      currency      = currency,
      givenCurrency = WolenjeConfig.defaultCurrency
    )

    convertedAmount match {
      case x if x < 100 => None
      case _ => Some(10)
    }
  }

  def getXMLTextElement(xmlElem: Elem, name: String): Option[String] = {
    val elems = (xmlElem \\ name)
    if (elems.isEmpty) None
    else Some(elems.head.text)
  }

  def toInt(s: String): Option[Int] = scala.util.Try(s.toInt).toOption


    def parseFiniteDuration(str: String): Option[FiniteDuration] =
    try {
      Some(Duration(str)).collect {case d: FiniteDuration => d }
    } catch {
      case ex: NumberFormatException => None
    }
}
