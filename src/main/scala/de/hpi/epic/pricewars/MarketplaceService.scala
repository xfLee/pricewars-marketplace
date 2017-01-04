package de.hpi.epic.pricewars

import akka.actor.{Actor, ActorContext, ActorLogging}
import akka.event.Logging
import de.hpi.epic.pricewars.JSONConverter._
import de.hpi.epic.pricewars.ResultConverter._
import spray.http._
import spray.json._
import spray.routing._

class MarketplaceServiceActor extends Actor with ActorLogging with MarketplaceService {
  override def actorRefFactory: ActorContext = context

  override def receive: Receive = runRoute(route)
}

trait MarketplaceService extends HttpService with CORSSupport {
  val route: Route = respondWithMediaType(MediaTypes.`application/json`) {
    logRequestResponse("marketplace", Logging.InfoLevel) {
      cors {
        path("offers") {
          get {
            parameter('product_id.as[Long] ?) { product_id =>
              complete {
                DatabaseStore.getOffers(product_id)
              }
            }
          } ~
            post {
              optionalHeaderValueByName(HttpHeaders.Authorization.name) { authorizationHeader =>
                entity(as[Offer]) { offer =>
                  detach() {
                    complete {
                      val merchant = ValidateLimit.checkMerchant(authorizationHeader)
                      if (merchant.isDefined) {
                        DatabaseStore.addOffer(offer, merchant.get).successHttpCode(StatusCodes.Created)
                      } else {
                        StatusCode.int2StatusCode(401) -> s"""{"error": "Not authorized or API request limit reached!"}"""
                      }
                    }
                  }
                }
              }
            }
        } ~
          path("offers" / LongNumber) { id =>
            get {
              complete {
                DatabaseStore.getOffer(id)
              }
            } ~
              delete {
                optionalHeaderValueByName(HttpHeaders.Authorization.name) { authorizationHeader =>
                  complete {
                    val merchant = ValidateLimit.checkMerchant(authorizationHeader)
                    if (merchant.isDefined) {
                      val res = DatabaseStore.deleteOffer(id, merchant.get)
                      res match {
                        case Success(v) => StatusCodes.NoContent
                        case f: Failure[Unit] => StatusCode.int2StatusCode(f.code) -> f.toJson.toString()
                      }
                    } else {
                      StatusCode.int2StatusCode(401) -> s"""{"error": "Not authorized or API request limit reached!"}"""
                    }
                  }
                }
              } ~
              put {
                optionalHeaderValueByName(HttpHeaders.Authorization.name) { authorizationHeader =>
                  entity(as[Offer]) { offer =>
                    detach() {
                      complete {
                        val merchant = ValidateLimit.checkMerchant(authorizationHeader)
                        if (merchant.isDefined) {
                          DatabaseStore.updateOffer(id, offer, merchant.get)
                        } else {
                          StatusCode.int2StatusCode(401) -> s"""{"error": "Not authorized or API request limit reached!"}"""
                        }
                      }
                    }
                  }
                }
              }
          } ~
          path("offers" / LongNumber / "buy") { id =>
            post {
              optionalHeaderValueByName(HttpHeaders.Authorization.name) { authorizationHeader =>
                entity(as[BuyRequest]) { buyRequest =>
                  detach() {
                    complete {
                      val consumer = ValidateLimit.checkConsumer(authorizationHeader)
                      if (consumer.isDefined) {
                        DatabaseStore.buyOffer(id, buyRequest.price, buyRequest.amount, consumer.get).successHttpCode(StatusCodes.NoContent)
                      } else {
                        StatusCode.int2StatusCode(401) -> s"""{"error": "Not authorized or API request limit reached!"}"""
                      }
                    }
                  }
                }
              }
            }
          } ~
          path("offers" / LongNumber / "restock") { id =>
            patch {
              optionalHeaderValueByName(HttpHeaders.Authorization.name) { authorizationHeader =>
                entity(as[OfferPatch]) { offer =>
                  complete {
                    val merchant = ValidateLimit.checkMerchant(authorizationHeader)
                    if (merchant.isDefined) {
                      DatabaseStore.restockOffer(id, offer.amount.getOrElse(0), offer.signature.getOrElse(""), merchant.get)
                    } else {
                      StatusCode.int2StatusCode(401) -> s"""{"error": "Not authorized or API request limit reached!"}"""
                    }
                  }
                }
              }
            }
          } ~
          path("merchants") {
            get {
              complete {
                DatabaseStore.getMerchants
              }
            } ~
              post {
                entity(as[Merchant]) { merchant =>
                  detach() {
                    complete {
                      DatabaseStore.addMerchant(merchant).successHttpCode(StatusCodes.Created)
                    }
                  }
                }
              }
          } ~
          path("merchants" / Rest) { id =>
            get {
              complete {
                DatabaseStore.getMerchant(id)
              }
            } ~
              delete {
                complete {
                  val res = DatabaseStore.deleteMerchant(id)
                  res match {
                    case Success(v) => StatusCodes.NoContent
                    case f: Failure[Unit] => StatusCode.int2StatusCode(f.code) -> f.toJson.toString()
                  }
                }
              }
          } ~
          path("consumers") {
            get {
              complete {
                DatabaseStore.getConsumers
              }
            } ~
              post {
                entity(as[Consumer]) { consumer =>
                  detach() {
                    complete {
                      DatabaseStore.addConsumer(consumer).successHttpCode(StatusCodes.Created)
                    }
                  }
                }
              }
          } ~
          path("consumers" / Rest) { id =>
            get {
              complete {
                DatabaseStore.getConsumer(id)
              }
            } ~
              delete {
                complete {
                  val res = DatabaseStore.deleteConsumer(id)
                  res match {
                    case Success(v) => StatusCodes.NoContent
                    case f: Failure[Unit] => StatusCode.int2StatusCode(f.code) -> f.toJson.toString()
                  }
                }
              }
          } ~
          path("products") {
            get {
              complete {
                DatabaseStore.getProducts
              }
            } ~
              post {
                entity(as[Product]) { product =>
                  detach() {
                    complete {
                      DatabaseStore.addProduct(product).successHttpCode(StatusCodes.Created)
                    }
                  }
                }
              }
          } ~
          path("products" / LongNumber) { id =>
            get {
              complete {
                DatabaseStore.getProduct(id)
              }
            } ~
              delete {
                complete {
                  val res = DatabaseStore.deleteProduct(id)
                  res match {
                    case Success(v) => StatusCodes.NoContent
                    case f: Failure[Unit] => StatusCode.int2StatusCode(f.code) -> f.toJson.toString()
                  }
                }
              }
          } ~
          path("producer" / "key") {
            put {
              complete {
                val old_key = ProducerConnector.producer_key.getOrElse("")
                ProducerConnector.producer_key = ProducerConnector.getProducerKey()
                if (ProducerConnector.producer_key.isDefined) {
                  StatusCode.int2StatusCode(200) -> s"""{"old producer key": "$old_key", "new producer key": "${ProducerConnector.producer_key.getOrElse("")}"}"""
                }
                else {
                  StatusCode.int2StatusCode(500) -> s"""{"old producer key": "$old_key", "new producer key": "${ProducerConnector.producer_key.getOrElse("")}"}"""
                }
              }
            }
          }
      } ~
        path("config") {
          put {
            entity(as[Settings]) { settings =>
              detach() {
                complete {
                  ValidateLimit.setLimit(settings.tick, settings.max_req_per_sec)
                  StatusCode.int2StatusCode(200) -> s"""{}"""
                }
              }
            }
          }
        }
    }
  }
}
