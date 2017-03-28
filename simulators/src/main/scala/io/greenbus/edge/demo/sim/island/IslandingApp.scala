/**
 * Copyright 2011-2017 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. Green Energy
 * Corp licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.greenbus.edge.demo.sim.island

import java.util.UUID

import akka.actor.{ Actor, Props }
import com.typesafe.scalalogging.LazyLogging
import io.greenbus.edge._
import io.greenbus.edge.client._
import io.greenbus.edge.demo.sim.EssSim.GridForming
import io.greenbus.edge.demo.sim.{ TimeSeriesUpdate => _, _ }
import play.api.libs.json.Json

import scala.concurrent.Future

object ControlParams {
  import play.api.libs.json._
  implicit val writer = Json.writes[ControlParams]
  implicit val reader = Json.reads[ControlParams]
}
case class ControlParams(microgridName: String, chpRampedUp: Double, chpNominal: Double, chargingBatteryRate: Double)

object IslandingApp {
  import io.greenbus.edge.demo.sim.EndpointUtils._

  val eventsKey = Path("Events")
  val paramsKey = Path("Params")
  val enabledKey = Path("IsEnabled")

  val setEnableKey = Path("SetEnable")
  val setDisableKey = Path("SetDisable")

  val boolMappingKey = Path("boolMapping")

  val enabledMapping = ValueArray(Vector(
    ValueObject(Map(
      "value" -> ValueBool(false),
      "name" -> ValueString("Disabled"))),
    ValueObject(Map(
      "value" -> ValueBool(true),
      "name" -> ValueString("Enabled")))))
  val enabledMappingKv = boolMappingKey -> enabledMapping

  def buildPublisherDesc(params: ControlParams): ClientEndpointPublisherDesc = {
    val now = System.currentTimeMillis()

    val indexes = Map(Path("role") -> ValueSimpleString("application"))
    val meta = Map.empty[Path, Value]

    val latestKvs = Map(
      paramsKey -> kv(ValueString(Json.toJson(params).toString(), Some("application/json"))))

    val events = Map(eventsKey -> EventEntry(MetadataDesc(Map(), Map())))

    val activeSets = Map.empty[Path, ActiveSetConfigEntry]

    val timeSeries = Map(
      /*ChpMapping.power -> tsDouble(0.0, now, indexes = Map(Path("gridValueType") -> ValueSimpleString(outputPowerType)), meta = Map(Path("unit") -> ValueString("kW"))),*/
      enabledKey -> tsBool(false, now, indexes = Map(Path("applicationStatusType") -> ValueSimpleString("enabled")), meta = Map(enabledMappingKv)))

    val outputs = Map(
      setEnableKey -> OutputEntry(PublisherOutputValueStatus(0, None), MetadataDesc(Map(), Map(Path("simpleInputType") -> ValueString("indication")))),
      setDisableKey -> OutputEntry(PublisherOutputValueStatus(0, None), MetadataDesc(Map(), Map(Path("simpleInputType") -> ValueString("indication")))))

    ClientEndpointPublisherDesc(indexes, meta, latestKvs, timeSeries, events, activeSets, outputs)
  }

  case object DoInit
  case class GotPublisherConnection(conn: EndpointPublisherConnection)
  case class ConnectionError(ex: Throwable)
  case object Enabled
  case object Disabled
  case class SetSubscription(edgeSubscription: EdgeSubscription)
  case class DataSubscription(edgeSubscription: EdgeSubscription)
  case class GotOutputClient(client: EdgeOutputClient)

  def props(connection: EdgeConnection, endpointId: EndpointId, params: ControlParams): Props = {
    Props(classOf[IslandingApp], connection, endpointId, params)
  }
}

class IslandingApp(connection: EdgeConnection, endpointId: EndpointId, params: ControlParams) extends Actor with CallMarshalActor with LazyLogging {
  import IslandingApp._

  import context.dispatcher

  private val sessionId = PersistenceSessionId(UUID.randomUUID(), 0)
  private var enabled = false
  private var connected = true
  private var pccConnected = true
  private val publisher = new EndpointPublisherImpl(this.marshaller, endpointId, buildPublisherDesc(params))
  private var publisherConnOpt = Option.empty[EndpointPublisherConnection]

  private var outputClientOpt = Option.empty[EdgeOutputClient]

  private var breakerSet = Map.empty[EndpointPath, Option[Boolean]]
  private var setSub = Option.empty[EdgeSubscription]
  private var dataSub = Option.empty[EdgeSubscription]

  publisher.outputReceiver.bind(handleOutput)

  self ! DoInit

  def receive = {
    case DoInit => {
      val connectFut = connection.connectPublisher(endpointId, sessionId, publisher)
      connectFut.foreach(conn => self ! GotPublisherConnection(conn))
      connectFut.failed.foreach(ex => self ! ConnectionError(ex))

      val outputClientFut = connection.openOutputClient()
      outputClientFut.foreach(cl => self ! GotOutputClient(cl))
      outputClientFut.failed.foreach(ex => self ! ConnectionError(ex))

      doIndexSub()
    }
    case ConnectionError(ex) => logger.error("CONNECTION ERROR: " + ex)
    case GotPublisherConnection(conn) => {
      publisherConnOpt = Some(conn)
    }
    case GotOutputClient(cl) => {
      outputClientOpt = Some(cl)
      //doCheck()
    }
    case SetSubscription(sub) => {
      setSub = Some(sub)
      sub.notifications.bind { notification =>
        this.marshaller.marshal {
          notification.indexNotification.dataKeyNotifications.foreach { keyNot =>
            keyNot.snapshot.foreach { pathSet =>
              breakerSet = pathSet.map(p => p -> Option.empty[Boolean]).toMap
              doDataSub(pathSet)
            }
          }
        }
      }

    }
    case DataSubscription(sub) => {
      dataSub = Some(sub)
      sub.notifications.bind(not => this.marshaller.marshal { handleDataNotification(not) })
    }
    case Enabled =>
      enabled = true
      pushTimeSeries(enabledKey, ValueBool(true))
      postEvent(Seq("application", "status"), "Application enabled")
      publisher.flush()
    //doCheck()
    case Disabled =>
      enabled = false
      pushTimeSeries(enabledKey, ValueBool(false))
      postEvent(Seq("application", "status"), "Application disabled")
      publisher.flush()
    case MarshalledCall(f) => f()
  }

  private def pushTimeSeries(key: Path, value: SampleValue): Unit = {
    publisher.timeSeriesStreams.get(key).foreach(_.push(TimeSeriesSample(System.currentTimeMillis(), value)))
  }

  private def postEvent(topic: Seq[String], text: String): Unit = {
    publisher.eventStreams.get(eventsKey).foreach { sink =>
      sink.push(TopicEvent(Path(topic), Some(ValueString(text))))
    }
  }

  /*private def doCheck(): Unit = {
    val open = breakerSet.exists(_._2.contains(false))
    if (enabled) {
      if (connected && open) {
        doOnTrip()
      } else if (!connected && !open) {
        doOnClose()
      }
    }
    connected = !open
  }*/

  private def doCheck(pccClosedUpdate: Boolean): Unit = {
    //val open = breakerSet.exists(_._2.contains(false))
    if (enabled) {
      if (pccConnected && !pccClosedUpdate) {
        doOnTrip()
      } else if (!pccConnected && pccClosedUpdate) {
        doOnClose()
      }
    }
    pccConnected = pccClosedUpdate
  }

  private def doOnTrip(): Unit = {
    outputClientOpt match {
      case Some(client) =>

        val tripFut = client.issueOutput(EndpointPath(NamedEndpointId(Path(Seq("Rankin", "MGRID", "CUST_BKR"))), BreakerMapping.bkrTrip), ClientOutputParams())

        val battEndPath = EndpointPath(NamedEndpointId(Path(Seq("Rankin", "MGRID", "ESS"))), EssMapping.setBatteryMode)
        val battFut = client.issueOutput(battEndPath, ClientOutputParams(outputValueOpt = Some(ValueUInt32(GridForming.numeric))))

        val chpEndPath = EndpointPath(NamedEndpointId(Path(Seq("Rankin", "MGRID", "CHP"))), ChpMapping.setTarget)
        val chpFut = client.issueOutput(chpEndPath, ClientOutputParams(outputValueOpt = Some(ValueDouble(params.chpRampedUp))))

        postEvent(Seq("application", "action"), "Executed actions on PCC trip")
        publisher.flush()

        Future.sequence(Seq(tripFut, battFut, chpFut)).foreach { results =>
          logger.info("commands successful: " + results)
          postEvent(Seq("application", "action"), "Executed actions on PCC trip success")
          publisher.flush()
        }

      case None =>
        postEvent(Seq("application", "error"), "Observed islanding but output not configured")
        publisher.flush()
    }

  }

  private def doOnClose(): Unit = {
    outputClientOpt match {
      case Some(client) =>

        val closeFut = client.issueOutput(EndpointPath(NamedEndpointId(Path(Seq("Rankin", "MGRID", "CUST_BKR"))), BreakerMapping.bkrClose), ClientOutputParams())

        val battEndId = NamedEndpointId(Path(Seq("Rankin", "MGRID", "ESS")))
        val battFut = client.issueOutput(EndpointPath(battEndId, EssMapping.setBatteryMode), ClientOutputParams(outputValueOpt = Some(ValueUInt32(EssSim.Constant.numeric))))
        val battSpFut = client.issueOutput(EndpointPath(battEndId, EssMapping.setChargeRate), ClientOutputParams(outputValueOpt = Some(ValueDouble(params.chargingBatteryRate))))

        val chpEndPath = EndpointPath(NamedEndpointId(Path(Seq("Rankin", "MGRID", "CHP"))), ChpMapping.setTarget)
        val chpFut = client.issueOutput(chpEndPath, ClientOutputParams(outputValueOpt = Some(ValueDouble(params.chpNominal))))

        postEvent(Seq("application", "action"), "Executed actions on PCC close")
        publisher.flush()

        Future.sequence(Seq(closeFut, battSpFut, battFut, chpFut)).foreach { results =>
          logger.info("commands successful: " + results)
          postEvent(Seq("application", "action"), "Executed actions on PCC close success")
          publisher.flush()
        }

      case None =>
        postEvent(Seq("application", "error"), "Observed reclose but output not configured")
        publisher.flush()
    }
  }

  private def doDataSub(pathList: Set[EndpointPath]): Unit = {
    dataSub.foreach(_.close())
    val subFut = connection.openSubscription(ClientSubscriptionParams(dataSubscriptions = pathList.toVector))
    subFut.foreach(sub => self ! DataSubscription(sub))
    subFut.failed.foreach(ex => self ! ConnectionError(ex))
  }

  private def doIndexSub(): Unit = {
    val specifier = IndexSpecifier(Path("gridValueType"), Some(ValueSimpleString(EndpointBuilders.bkrStatusType)))
    val params = ClientSubscriptionParams(indexParams = ClientIndexSubscriptionParams(dataKeyIndexes = Seq(specifier)))

    val subFut = connection.openSubscription(params)

    subFut.foreach(sub => self ! SetSubscription(sub))
    subFut.failed.foreach(ex => self ! ConnectionError(ex))
  }

  private def handleDataNotification(notification: ClientSubscriptionNotification): Unit = {
    notification.dataNotifications.foreach { dataNot =>
      dataNot.value match {
        case st: TimeSeriesState => st.values.lastOption.foreach(ts => handleValue(dataNot.key, ts.sample.value))
        case up: TimeSeriesUpdate => up.values.lastOption.foreach(ts => handleValue(dataNot.key, ts.sample.value))
        case _ =>
          logger.warn("Unrecognized data notification: " + dataNot)
      }

    }
  }

  private def handleValue(path: EndpointPath, value: SampleValue): Unit = {
    val pccPath = EndpointPath(NamedEndpointId(Path(Seq("Rankin", "MGRID", "PCC_BKR"))), BreakerMapping.bkrStatus)
    if (path == pccPath) {
      val nowClosed = value.toBoolean
      doCheck(nowClosed)
    }
    /*if (breakerSet.contains(path)) {
      breakerSet += (path -> Some(value.toBoolean))
      doCheck()
    }*/
  }

  private def handleOutput(batch: UserOutputRequestBatch): Unit = {
    batch.requests.foreach { request =>
      request.key match {
        case `setEnableKey` =>
          self ! Enabled
          request.resultAsync(OutputSuccess(None))
        case `setDisableKey` =>
          self ! Disabled
          request.resultAsync(OutputSuccess(None))
        case _ =>
          logger.warn("Unhandled output: " + request.key)
          request.resultAsync(OutputFailure("Unsupported"))
      }
    }
  }

}
