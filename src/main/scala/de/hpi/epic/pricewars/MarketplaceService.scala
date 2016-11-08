package de.hpi.epic.pricewars

import akka.actor.Actor
import spray.json._
import spray.routing._
import spray.http._
import MediaTypes._

import JSONConverter._
import ResultConverter._

class MarketplaceServiceActor extends Actor with MarketplaceService {
  override def actorRefFactory = context
  override def receive = runRoute(route)
}

/**
  * Created by Jan on 01.11.2016.
  */
trait MarketplaceService extends HttpService {
  val route = respondWithMediaType(MediaTypes.`application/json`) {
    path("offers") {
      get {
        complete {
          DatabaseStore.getOffers
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
            case Success(v) => StatusCodes.NoContent -> """{"result": "deleted"}"""
            case f : Failure[Unit] => StatusCode.int2StatusCode(f.code) -> f.toJson.toString()
          }
        }
      } ~
      put {
        entity(as[Offer]) { offer =>
          detach() {
            complete {
              println(s"updated: $id")
              DatabaseStore.updateOffer(id, offer)
            }
          }
        }
      }
    } ~
    path("offers" / LongNumber / "buy") { id =>
      post {
        complete {
          /*OfferStore.get(id).flatMap(offer => MerchantStore.get(offer.merchant_id.toLong)) match {
            case Success(merchant) => MerchantConnector.notifyMerchant(merchant, id, 1, 1)
          }*/
          DatabaseStore.buyOffer(id, BigDecimal(12), 2)
        }
      }
    } ~
    path("merchants") {
      get {
        complete {
          MerchantStore.get
        }
      } ~
      post {
        entity(as[Merchant]) { merchant =>
          detach() {
            complete {
              MerchantStore.add(merchant).successHttpCode(StatusCodes.Created)
            }
          }
        }
      }
    } ~
    path("merchants" / LongNumber) { id =>
      get {
        complete {
          MerchantStore.get(id)
        }
      } ~
      delete {
        complete {
          val res = MerchantStore.remove(id)
          res match {
            case Success(v) => StatusCodes.NoContent
            case f: Failure[Unit] => StatusCode.int2StatusCode(f.code) -> f.toJson.toString()
          }
        }
      }
    }
  }

}