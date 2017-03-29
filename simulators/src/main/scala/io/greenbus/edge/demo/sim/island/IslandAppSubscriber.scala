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

import com.typesafe.scalalogging.LazyLogging
import io.greenbus.edge.api.{ EndpointPath, IndexSpecifier, Path, ValueString }
import io.greenbus.edge.api.stream._
import io.greenbus.edge.demo.sim.EndpointBuilders
import io.greenbus.edge.flow.{ QueuedDistributor, Source }

object IslandAppSubscriber {

  //val specifier = IndexSpecifier(Path("gridValueType"), Some(ValueString(EndpointBuilders.bkrStatusType)))

  val pccSpecifier = IndexSpecifier(Path("bkrStatusRole"), Some(ValueString(EndpointBuilders.pccBkr)))
  val custSpecifier = IndexSpecifier(Path("bkrStatusRole"), Some(ValueString(EndpointBuilders.custBkr)))

  def matchSpecifier(spec: IndexSpecifier, update: IdentifiedEdgeUpdate): Option[EdgeDataStatus[KeySetUpdate]] = {
    update match {
      case up: IdDataKeyIndexUpdate =>
        if (up.specifier == spec) {
          Some(up.data)
        } else {
          None
        }
      case _ => None
    }
  }

  def toAvailKeySetUpdate(status: EdgeDataStatus[KeySetUpdate]): Option[Set[EndpointPath]] = {
    status match {
      case ResolvedValue(v) => Some(v.set)
      case _ => None
    }
  }

  def filterSpecUpdate(spec: IndexSpecifier, updates: Seq[IdentifiedEdgeUpdate]): Option[Set[EndpointPath]] = {
    updates.flatMap(matchSpecifier(spec, _)).lastOption.flatMap(toAvailKeySetUpdate)
  }

  def matchDataKey(key: EndpointPath, update: IdentifiedEdgeUpdate): Option[EdgeDataStatus[DataKeyUpdate]] = {
    update match {
      case up: IdDataKeyUpdate =>
        if (up.id == key) {
          Some(up.data)
        } else {
          None
        }
      case _ => None
    }
  }

  def toDataKeyValueUpdate(up: EdgeDataStatus[DataKeyUpdate]): Option[DataKeyUpdate] = {
    up match {
      case ResolvedValue(v) => Some(v)
      case _ => None
    }
  }

  def toSeriesValue(up: DataKeyUpdate): Option[SeriesUpdate] = {
    up.value match {
      case v: SeriesUpdate => Some(v)
      case _ => None
    }
  }

  def toBoolSeriesValue(up: SeriesUpdate): Boolean = {
    up.value.toBoolean
  }

}

case class BreakerStates(pcc: Boolean, cust: Boolean)

class IslandAppSubscriber(client: EdgeSubscriptionClient) extends LazyLogging {
  import IslandAppSubscriber._

  private val pccIndexSub = client.subscribe(Seq(), Seq(), Seq(), IndexSubscriptionParams(dataKeyIndexes = Seq(pccSpecifier)))
  pccIndexSub.updates.bind(updates => handlePccIndexUpdate(filterSpecUpdate(pccSpecifier, updates)))

  private val custIndexSub = client.subscribe(Seq(), Seq(), Seq(), IndexSubscriptionParams(dataKeyIndexes = Seq(custSpecifier)))
  custIndexSub.updates.bind(updates => handleCustIndexUpdate(filterSpecUpdate(custSpecifier, updates)))

  private var pccDataSubOpt = Option.empty[EdgeSubscription]
  private var custDataSubOpt = Option.empty[EdgeSubscription]

  private var pccState = Option.empty[Boolean]
  private var custState = Option.empty[Boolean]
  private val updates = new QueuedDistributor[BreakerStates]

  def states: Source[BreakerStates] = updates

  private def handlePccIndexUpdate(update: Option[Set[EndpointPath]]): Unit = {
    val keys = update.getOrElse(Set())
    if (keys.nonEmpty) {
      pccDataSubOpt.foreach(_.close())
      val builder = SubscriptionBuilder.newBuilder
      keys.foreach(builder.series)
      val params = builder.build()

      if (keys.size > 1) {
        logger.warn(s"More than one PCC breaker status")
      }
      val pccKey = keys.head

      val dataSub = client.subscribe(params)
      dataSub.updates.bind { updates =>
        updates.flatMap(matchDataKey(pccKey, _))
          .flatMap(toDataKeyValueUpdate)
          .flatMap(toSeriesValue)
          .map(toBoolSeriesValue)
          .foreach(handlePccBkrState)
      }

    } else {
      pccDataSubOpt.foreach(_.close())
      pccDataSubOpt = None
    }
  }
  private def handleCustIndexUpdate(update: Option[Set[EndpointPath]]): Unit = {
    val keys = update.getOrElse(Set())
    if (keys.nonEmpty) {
      custDataSubOpt.foreach(_.close())
      val builder = SubscriptionBuilder.newBuilder
      keys.foreach(builder.series)
      val params = builder.build()

      if (keys.size > 1) {
        logger.warn(s"More than one Cust breaker status")
      }
      val bkrKey = keys.head

      val dataSub = client.subscribe(params)
      dataSub.updates.bind { updates =>
        updates.flatMap(matchDataKey(bkrKey, _))
          .flatMap(toDataKeyValueUpdate)
          .flatMap(toSeriesValue)
          .map(toBoolSeriesValue)
          .foreach(handleCustBkrState)
      }

    } else {
      custDataSubOpt.foreach(_.close())
      custDataSubOpt = None
    }
  }

  private def handlePccBkrState(v: Boolean): Unit = {
    val prev = pccState
    pccState = Some(v)
    if (prev != pccState) {
      enqueueIfSufficient()
    }
  }

  private def handleCustBkrState(v: Boolean): Unit = {
    val prev = custState
    custState = Some(v)
    if (prev != custState) {
      enqueueIfSufficient()
    }
  }

  private def enqueueIfSufficient(): Unit = {
    (pccState, custState) match {
      case (Some(pcc), Some(cust)) => updates.push(BreakerStates(pcc, cust))
      case _ =>
    }
  }

}

