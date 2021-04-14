package com.wolenjeMerchantCore.service.merchant

import java.security.SecureRandom
import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.Props
import akka.pattern.ask
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, MessageEntity}
import akka.util.Timeout
import com.wolenjeMerchantCore.core.config.WolenjeConfig
import spray.json._
import com.wolenjeMerchantCore.core.db.postgresql.cache._
import com.wolenjeMerchantCore.core.db.postgresql.service.PostgresqlDbService
import com.wolenjeMerchantCore.core.email.EmailRequestService
import com.wolenjeMerchantCore.core.http.WolenjeHttpClientT
import com.wolenjeMerchantCore.core.token.TokenService
import com.wolenjeMerchantCore.core.token.TokenService.RegisterPhoneNumber
import com.wolenjeMerchantCore.core.util.WolenjeEnum._
import com.wolenjeMerchantCore.core.util.WolenjeUtil

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}
import com.wolenjeMerchantCore.core.db.redis.WolenjeRedisClient

object MerchantRequestService {
  case class MerchantNewRequest(
    name: String,
    phoneNumber: String,
    provider: Provider.Value,
    emailAddress: String
  )

  case class MerchantVerifyRequest(
    phoneNumber: String,
    otp: String,
    password: String
  ) {
    require(password.length==4)
    require(WolenjeUtil.toInt(password).isDefined)
  }

  case class MerchantPasswordOTPRequest(
    emailAddress: String
  )

  case class MerchantNewPasswordRequest(
    otp: String,
    emailAddress: String,
    password: String
  ) { require(password == 4) }

  case class MerchantRequestResponse(
    status: TransactionStatus.Value,
    errorMessage: Option[String]
  )

  case class UserTokenCreateRequest(
    userId: Int,
    token: String
  )
  case class UserTokenCreateResponse(
    status: TransactionStatus.Value,
    errorMessage: Option[String]
  )
}

class MerchantRequestService extends WolenjeHttpClientT {
  import EmailRequestService._
  import PostgresqlDbService._
  import MerchantRequestService._
  import MerchantGatewayMessage._

  implicit val timeout = Timeout(WolenjeConfig.httpRequestTimeout)

  def newPasswordRedisOTPPrefix(email: String) = WolenjeConfig.newPasswordRedisOtpPrefox + email
  def emailRequestService                      = context.actorOf(Props[EmailRequestService])
  def sqlDbService                             = context.actorOf(Props[PostgresqlDbService])
  def tokenService                             = context.actorOf(Props[TokenService])
  def getMerchantDbCache: MerchantDbCacheT     = MerchantDbCache

  override def receive: Receive = {
    case req: MerchantNewRequest    =>
      log.info(s"Processing $req")
      val currentSender = sender()

      sqlDbService ! MerchantInsertDbRequest(
        name            = req.name,
        accountNumber   = req.phoneNumber,
        accountProvider = req.provider,
        emailAddress    = req.emailAddress
      )

      val httpResponseFut: Future[HttpResponse] = for {
        entity <- Marshal(SendOTPGatewayRequest(req.phoneNumber)).to[MessageEntity]
        response <- sendHttpRequest(
          HttpRequest(
            method = HttpMethods.POST,
            uri    = WolenjeConfig.sendOTPUrl,
            entity = entity
          )
        )
      } yield {
        log.info(s"Http request with response: $response")
        response
      }

      httpResponseFut onComplete {
        case Failure(exception) => 
          log.error(s"Unable to complete http request for $req: ", Some(exception))
          currentSender ! MerchantRequestResponse(
            status       = TransactionStatus.ApplicationError,
            errorMessage = Some("Internal Gateway Error")
          )
        case Success(value)     =>
          value.status.isSuccess match {
            case false => 
             log.error(s"Unable to complete http request for $req: $value")
             currentSender ! MerchantRequestResponse(
              status       = TransactionStatus.ApplicationError,
              errorMessage = Some("Internal Gateway Error")
            )

             case true  => log.info(s"Received response: ${value.data}")
             currentSender ! MerchantRequestResponse(
              status       = TransactionStatus.Success,
              errorMessage = None
            )
          }
      }

    case req: MerchantVerifyRequest =>
      log.info(s"Processing $req")
      val currentSender = sender()
      getMerchantDbCache.findByNumber(req.phoneNumber) match {
        case None           =>
          currentSender ! MerchantRequestResponse(
            status       = TransactionStatus.Failed,
            errorMessage = Some("Merchant Doesn't Exist")
          )
        case Some(merchant) =>
          val httpResponseFut: Future[HttpResponse] = for {
            entity <- Marshal(VerifyOTPGatewayRequest(
              phone = req.phoneNumber,
              otp         = req.otp
            )).to[MessageEntity]
            response <- sendHttpRequest(
              HttpRequest(
                method = HttpMethods.POST,
                uri    = WolenjeConfig.verifyOTPUrl,
                entity = entity
              )
            )
          } yield {
            log.info(s"Http request with response: $response")
            response
          }
          httpResponseFut onComplete {
            case Failure(exception) =>
              log.error(s"Unable to complete http request for $req: ", Some(exception))
              currentSender ! MerchantRequestResponse(
                status       = TransactionStatus.ApplicationError,
                errorMessage = Some("Error reaching servers. Kindly retry")
              )

            case Success(value)     =>
              value.status.isSuccess match {
                case false => log.error(s"Unable to complete http request for $req: $value")
                  currentSender ! MerchantRequestResponse(
                    status       = TransactionStatus.ApplicationError,
                    errorMessage = Some("Internal Gateway Error")
                  )
                case true  => log.info(s"Received response: ${value.data}")
                  val x = value.data.parseJson.convertTo[VerifyOTPGatewayResponse]
                  x.session.isDefined match {
                    case false =>
                      currentSender ! MerchantRequestResponse(
                        status       = TransactionStatus.ApplicationError,
                        errorMessage = Some("Internal Server Error")
                      )
                    case true => x.session.get.get("session_token") match {
                      case None        =>
                        currentSender ! MerchantRequestResponse(
                          status       = TransactionStatus.ApplicationError,
                          errorMessage = Some("Internal Server Error")
                        )
                      case Some(token) =>
                        val httpResponseFut2: Future[HttpResponse] = for {
                          entity <- Marshal(PasswordGatewayRequest(
                            phone = req.phoneNumber,
                            pin   = req.password
                          )).to[MessageEntity]
                          response <- sendHttpRequest(
                            HttpRequest(
                              method = HttpMethods.POST,
                              uri    = WolenjeConfig.tokenUrl,
                              entity = entity
                            ).withHeaders(RawHeader("Authorization",s"Bearer $token"))
                          )
                        } yield {
                          log.info(s"Http request with response: $response")
                          response
                        }
                        httpResponseFut2 onComplete {
                          case Failure(exception2) => log.error(s"Unable to complete http request for $req: ", Some(exception2))
                            currentSender ! MerchantRequestResponse(
                              status       = TransactionStatus.ApplicationError,
                              errorMessage = Some("Error reaching servers. Kindly retry")
                            )

                          case Success(value2) =>
                            value.status.isSuccess match {
                              case false =>
                                log.error(s"Unable to complete http request for $req: $value2")
                                currentSender ! MerchantRequestResponse(
                                  status       = TransactionStatus.ApplicationError,
                                  errorMessage = Some("Internal Gateway Error")
                                )
                              case true => log.info(s"Received response: ${value2.data}")
                                val y = value2.data.parseJson.convertTo[PasswordGatewayResponse]
                                y.status match {
                                  case "OK" =>
                                    sqlDbService ! MerchantUpdateDbQuery(
                                      merchantId = merchant.id,
                                      password   = req.password
                                    )
                                    emailRequestService ! EmailServiceRequest(
                                      recipient = merchant.emailAddress,
                                      message   = "Congratulations, your account has been successfully created and verified",
                                      subject   = "Merchant Account Registered"
                                    )

                                    context.system.scheduler.scheduleOnce(
                                      FiniteDuration.apply(1,"min"),
                                      tokenService,
                                      RegisterPhoneNumber(req.phoneNumber)
                                    )
                                    currentSender ! MerchantRequestResponse(
                                      status       = TransactionStatus.Success,
                                      errorMessage = None
                                    )
                                  case _ =>
                                    currentSender ! MerchantRequestResponse(
                                      status       = TransactionStatus.Failed,
                                      errorMessage = Some("Unable to set password")
                                    )
                                }
                            }
                        }
                    }
                  }
              }
          }
    }
    case req: MerchantPasswordOTPRequest =>
      log.info(s"Processing $req")
      val currentSender = sender()
      val key = newPasswordRedisOTPPrefix(req.emailAddress)
      WolenjeRedisClient.fetchElement(key) match {
        case Some(_) =>
        currentSender ! MerchantRequestResponse(
          status       = TransactionStatus.Failed,
          errorMessage = Some("Active Password Recovery Session still Ongoing")
        )
        case None    =>
          getMerchantDbCache.findEmailAddress(req.emailAddress) match {
            case None    =>
              currentSender ! MerchantRequestResponse(
                status       = TransactionStatus.Failed,
                errorMessage = Some("Email Account does not Exist. Please create an account")
              )
            case Some(_) =>
              //Generate Random OTP
              val otp = generateOtp
              WolenjeRedisClient.addElement(
                key      = key,
                value    = otp,
                lifetime = WolenjeConfig.newPasswordRedisOTPLifetime
              )
              val message = s"Hi, Please note that your OTP for resetting your password is: $otp."
              (emailRequestService ? EmailServiceRequest(
                message   = message,
                subject   = "Wolenje Merchant: Password Reset OTP",
                recipient = req.emailAddress
              )).mapTo[EmailServiceResponse] onComplete {
                case Failure(exception) =>
                  currentSender ! MerchantRequestResponse(
                    status       = TransactionStatus.ApplicationError,
                    errorMessage = Some("Internal Service Error")
                  )
                case Success(EmailServiceResponse(TransactionStatus.Success)) =>
                  currentSender ! MerchantRequestResponse(
                    status       = TransactionStatus.Success,
                    errorMessage = None
                  )

                case x =>
                  currentSender ! MerchantRequestResponse(
                    status       = TransactionStatus.ApplicationError,
                    errorMessage = Some("Internal Service Error")
                  )
              }
          }
      }

    case req: MerchantNewPasswordRequest =>
      log.info(s"Processing $req")
      val currentSender = sender()
      
      val key = newPasswordRedisOTPPrefix(req.emailAddress)
      //Check DB to confirm that otp exists where email is x
      WolenjeRedisClient.fetchElement(key) match {
        case None    =>
          currentSender ! MerchantRequestResponse(
            status       = TransactionStatus.Failed,
            errorMessage = Some("OTP doesn't exist for Email Address. Please enter your email")
          )
        case Some(_) =>
          //Update password
          getMerchantDbCache.findEmailAddress(req.emailAddress) match {
            case None    => 
              currentSender ! MerchantRequestResponse(
                status       = TransactionStatus.Failed,
                errorMessage = Some("Internal Service Error. Unable to Retrieve user data")
              )
            case Some(x) =>
              sqlDbService ! MerchantUpdateDbQuery(
                merchantId = x.id,
                password   = req.password
              )

              currentSender ! MerchantRequestResponse(
                status       = TransactionStatus.Success,
                errorMessage = None
              )
          }
      }

    case req: UserTokenCreateRequest =>
      log.info(s"Processing $req")
      val currentSender = sender()
      getMerchantDbCache.findByUserId(req.userId) match {
        case None    =>
          currentSender ! UserTokenCreateResponse(
            status        = TransactionStatus.Failed,
            errorMessage  = Some("User Account could not be found")
          )
        case Some(_) =>
          sqlDbService ! UserTokenCreateDbQuery(
            id     = 0,
            userId = req.userId,
            token  = req.token
          )

          currentSender ! UserTokenCreateResponse(
            status        = TransactionStatus.Success,
            errorMessage  = None
          )
      }
  }

  protected def generateOtp: String = {
    val otp     = new SecureRandom();
    val builder = new StringBuilder();

    for(count <- 0 to 5) {
        builder.append(otp.nextInt(10));
    }
    
    builder.toString
  }
}

