package com.wolenjeMerchantCore.core.email

import java.io.UnsupportedEncodingException
import java.net.URLEncoder

import javax.mail.internet.InternetAddress

import scala.concurrent.ExecutionContext.Implicits.global
import scala.xml.XML
import akka.actor.{Actor, ActorLogging}
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, Uri}
import com.wolenjeMerchantCore.core.config.WolenjeConfig
import com.wolenjeMerchantCore.core.http.WolenjeHttpClientT
import com.wolenjeMerchantCore.core.util.WolenjeEnum.TransactionStatus
import com.wolenjeMerchantCore.core.util.WolenjeUtil

import scala.util.{Failure, Success}
import com.github.jurajburian.mailer._
import scala.concurrent.Future

object EmailRequestService {
  case class EmailServiceRequest(
    message: String,
    subject: String,
    recipient: String
  )
  case class EmailServiceResponse(
    status: TransactionStatus.Value
  )
}

class EmailRequestService extends WolenjeHttpClientT {

  val session = (SmtpAddress("smtp.gmail.com", 587) :: SessionFactory()).session(Some("user@gmail.com"-> "password"))
  val mailer  = Mailer(session)

  import EmailRequestService._
  override def receive = {
    case emailReq: EmailServiceRequest =>
      log.info(s"Processing $emailReq")
      val currentSender = sender()

      val content: Content         = new Content().text("Hi, ").html(s"<p>${emailReq.message}</p>")
      val contentDispositionHeader = ContentDisposition("inline", Map("filename" -> "foobar.txt"))

      val msg = Message(
        from    = new InternetAddress(WolenjeConfig.amazonVerifiedEmail),
        subject = emailReq.subject,
        content = content,
        to      = Seq(new InternetAddress(emailReq.recipient))
      )
      val mailer = Mailer(session)
      try {
        mailer.send(msg)
        log.info(s"Successfully processed $emailReq")
        currentSender ! EmailServiceResponse(TransactionStatus.Success)
      } catch {
        case ex: Throwable =>
        log.error(s"Error while processing $emailReq: ", Some(ex))
          currentSender ! EmailServiceResponse(TransactionStatus.Failed)
      }
  }
}