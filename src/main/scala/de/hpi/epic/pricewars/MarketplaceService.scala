package de.hpi.epic.pricewars

import akka.actor.Actor
import de.hpi.epic.pricewars.JSONConverter._
import de.hpi.epic.pricewars.ResultConverter._
import spray.http._
import spray.json._
import spray.routing._

class MarketplaceServiceActor extends Actor with MarketplaceService {
  override def actorRefFactory = context

  override def receive = runRoute(route)
}

/**
  * Created by Jan on 01.11.2016.
  */
trait MarketplaceService extends HttpService with CORSSupport {
  val route = respondWithMediaType(MediaTypes.`application/json`) {
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
            entity(as[Offer]) { offer =>
              detach() {
                complete {
                  DatabaseStore.addOffer(offer).successHttpCode(StatusCodes.Created)
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
              complete {
                val res = DatabaseStore.deleteOffer(id)
                res match {
                  case Success(v) => StatusCodes.NoContent
                  case f: Failure[Unit] => StatusCode.int2StatusCode(f.code) -> f.toJson.toString()
                }
              }
            } ~
            put {
              entity(as[Offer]) { offer =>
                detach() {
                  complete {
                    DatabaseStore.updateOffer(id, offer)
                  }
                }
              }
            }
        } ~
        path("offers" / LongNumber / "buy") { id =>
          post {
            entity(as[BuyRequest]) { buyRequest =>
              detach() {
                complete {
                  DatabaseStore.getOffer(id).flatMap(offer => DatabaseStore.getMerchant(offer.merchant_id.toLong)) match {
                    case Success(merchant) => MerchantConnector.notifyMerchant(merchant, id, buyRequest.amount, buyRequest.price)
                  }
                  DatabaseStore.buyOffer(id, buyRequest.price, buyRequest.amount).successHttpCode(StatusCodes.NoContent)
                }
              }
            }
          }
        } ~
        path("offers" / LongNumber / "restock") { id =>
          patch {
            entity(as[OfferPatch]) { offer =>
              complete {
                offer.amount match {
                  case Some(amount) => DatabaseStore.restockOffer(id, amount)
                  case _ => StatusCodes.InternalServerError -> "no amount specified"
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
        path("merchants" / LongNumber) { id =>
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
        path("consumers" / LongNumber) { id =>
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
        }
    }
  }
}
