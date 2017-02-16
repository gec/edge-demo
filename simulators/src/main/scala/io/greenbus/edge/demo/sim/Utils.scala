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
package io.greenbus.edge.demo.sim

import java.util.Calendar

import scala.concurrent.Future

object Utils {

  val hoursInYear = 365 * 24
  val millisecondsInAnHour = 1000 * 60 * 60
  val millisecondsInADay = millisecondsInAnHour * 24
  def millisecondsToHours(ms: Long): Double = {
    ms.toDouble / millisecondsInAnHour.toDouble
  }

  def getStartOfCurrentDay(): Long = {
    val calendar = Calendar.getInstance()
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH)
    val day = calendar.get(Calendar.DATE)
    calendar.set(year, month, day, 0, 0, 0)
    calendar.getTimeInMillis
  }

  def getStartOfYear(): Long = {
    val calendar = Calendar.getInstance()
    val year = calendar.get(Calendar.YEAR)
    calendar.set(year, Calendar.JANUARY, 1, 0, 0, 0)
    calendar.getTimeInMillis
  }

  def getStartOfCurrentHour(): Long = {
    val calendar = Calendar.getInstance()
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH)
    val day = calendar.get(Calendar.DATE)
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    calendar.set(year, month, day, hour, 0, 0)
    calendar.getTimeInMillis
  }

  def hourInYearAndFraction(): (Int, Double) = {
    val now = Calendar.getInstance().getTimeInMillis
    val startOfYear = getStartOfYear()
    val startOfHour = getStartOfCurrentHour()
    val timeInYearToStartOfHour = startOfHour - startOfYear

    val hourFloor = timeInYearToStartOfHour / millisecondsInAnHour

    val fractionOfHour = (now - startOfHour).toDouble / millisecondsInAnHour.toDouble

    (hourFloor.toInt, fractionOfHour)
  }

  def timeTillNextUpdate(now: Long, timeSeries: IndexedSeq[(Long, Double)]): Long = {
    val nowInDailyTime = now - getStartOfCurrentDay()

    val i = timeSeries.indexWhere { case (time, _) => time >= nowInDailyTime }

    if (i < 0) {
      val wrappedDailyTime = timeSeries.head._1
      millisecondsInADay - nowInDailyTime + wrappedDailyTime
    } else {
      val dailyTime = timeSeries(i)._1
      dailyTime - nowInDailyTime
    }
  }

  def valueAtTime(now: Long, timeSeries: IndexedSeq[(Long, Double)]): Double = {

    val nowInDailyTime = now - getStartOfCurrentDay()

    if (nowInDailyTime < timeSeries.head._1) {
      timeSeries.head._2
    } else {
      val i = timeSeries.lastIndexWhere { case (time, v) => time < nowInDailyTime }
      timeSeries(i)._2
    }
  }
  /*
  def doubleMeas(v: Double, time: Option[Long] = None): Measurement = {
    val b = Measurement.newBuilder()
      .setDoubleVal(v)
      .setType(Measurement.Type.DOUBLE)

    time.foreach(b.setTime)

    b.build()
  }
  def intMeas(v: Int): Measurement = {
    Measurement.newBuilder()
      .setIntVal(v)
      .setType(Measurement.Type.INT)
      .build()
  }
  def boolMeas(v: Boolean): Measurement = {
    Measurement.newBuilder()
      .setBoolVal(v)
      .setType(Measurement.Type.BOOL)
      .build()
  }

  def commandReqAsDouble(cmdReq: CommandRequest): Option[Double] = {
    if (cmdReq.hasIntVal) {
      Some(cmdReq.getIntVal.toDouble)
    } else if (cmdReq.hasDoubleVal) {
      Some(cmdReq.getDoubleVal)
    } else {
      None
    }
  }

  def commandReqInt(cmdReq: CommandRequest): Option[Long] = {
    if (cmdReq.hasIntVal) {
      Some(cmdReq.getIntVal)
    } else {
      None
    }
  }

  def commandReqAsInt(cmdReq: CommandRequest): Option[Long] = {
    if (cmdReq.hasIntVal) {
      Some(cmdReq.getIntVal)
    } else if (cmdReq.hasDoubleVal) {
      Some(cmdReq.getDoubleVal.toInt)
    } else {
      None
    }
  }

  def measAnyNumericToDouble(m: Measurement): Option[Double] = {
    if (m.hasDoubleVal) Some(m.getDoubleVal) else None
  }
  def measToInt(m: Measurement): Option[Long] = {
    if (m.hasIntVal) Some(m.getIntVal) else None
  }
  def measToBoolOpt(m: Measurement): Option[Boolean] = {
    if (m.hasBoolVal) Some(m.getBoolVal) else None
  }

  def whenNotNil[A, B](coll: Seq[A])(f: => Future[Seq[B]]): Future[Seq[B]] = {
    if (coll.nonEmpty) f else Future.successful(Seq())
  }

  def findPoint(equip: String, pts: Seq[Point], typ: String): Point = {
    pts.find(_.getTypesList.contains(typ)).getOrElse(throw new IllegalArgumentException(s"Could not find Point of type $typ for $equip"))
  }
  def findCommand(equip: String, cmds: Seq[Command], typ: String): Command = {
    cmds.find(_.getTypesList.contains(typ)).getOrElse(throw new IllegalArgumentException(s"Could not find Command of type $typ for $equip"))
  }*/
}
