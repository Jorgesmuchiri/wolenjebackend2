package com.wolenjeMerchantCore.core.query

import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.{Actor, ActorRef, ActorLogging}
import spray.json._
import com.wolenjeMerchantCore.core.db.postgresql.mapper.{MerchantWalletMapper, TransactionMapper}
import com.wolenjeMerchantCore.core.db.postgresql.service.PostgresqlDbService.{MerchantWalletRowObject, TransactionRowObject}

import scala.util.{Failure, Success}

object QueryService {
  case class TransactionFetchQueryRequest(userId: Int, limit: Int)
  case class TransactionFetchQueryResponse(
    transactions: List[TransactionRowObject],
    errorMessage: Option[String]
  )

  case class WalletBalanceQueryRequest(userId: Int)
  case class WalletBalanceQueryResponse(
   amount: Option[BigDecimal],
   currency: Option[String],
   errorMessage: Option[String]
 )

}

class QueryService extends Actor with ActorLogging {
  import QueryService._
  override def receive: Receive = {
    case req: TransactionFetchQueryRequest =>
      log.info(s"processing $req")
      val currentSender = sender()
      fetchTransactions(
        userId        = req.userId,
        limit         = req.limit,
        currentSender = currentSender
      )

    case req: WalletBalanceQueryRequest =>
      log.info(s"processing $req")
      val currentSender = sender()
      getWalletBalance(req.userId, currentSender)

  }

  private def fetchTransactions(userId: Int, limit: Int, currentSender: ActorRef) = {
    TransactionMapper.fetchTransactions(userId = userId, limit = limit)
      .mapTo[List[TransactionRowObject]] onComplete {
      case Failure(exception) =>
        log.error(s"Error while processing transaction fetch for $userId with $limit: {}", Some(exception))
        currentSender ! TransactionFetchQueryResponse(
          transactions = Nil,
          errorMessage = Some("Internal Server Error")
        )

      case Success(transactions) =>
        log.info(s"Fetched transactions: $transactions")
        transactions.isEmpty match {
          case true =>
            currentSender ! TransactionFetchQueryResponse(
              transactions = Nil,
              errorMessage = Some("No Transactions Found")
            )
          case false =>
            currentSender ! TransactionFetchQueryResponse(
              transactions = transactions,
              errorMessage = None
            )
        }
    }
  }

  private def getWalletBalance(userId: Int, currentSender: ActorRef) = {
    MerchantWalletMapper.findWallet(userId)
      .mapTo[Option[MerchantWalletRowObject]] onComplete {
      case Failure(exception) =>
        log.error(s"Error while processing balance query for $userId: {}", Some(exception))
        currentSender ! WalletBalanceQueryResponse(
          amount       = None,
          currency     = None,
          errorMessage = Some("Internal Server Error")
        )
      case Success(value) =>
        value match {
          case Some(balance) =>
            currentSender ! WalletBalanceQueryResponse(
              amount       = Some(balance.amount),
              currency     = Some(balance.currency),
              errorMessage = None
            )
          case None          =>
            currentSender ! WalletBalanceQueryResponse(
              amount       = None,
              currency     = None,
              errorMessage = Some("Merchant Wallet Not Found")
            )
        }
    }
  }
}
