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

import scala.io.Source

case class LoadRecord(
  groupNames: IndexedSeq[String],
  criticalities: IndexedSeq[String],
  feeders: IndexedSeq[String],
  hourlies: IndexedSeq[IndexedSeq[Double]])

object LoadParser {

  def fromFile(file: String) = {

    val lines = Source.fromFile(file, "UTF-8").getLines()

    // Ignore the names from the GreenBus model.
    val modelNames = lines.next()

    val titleLine = lines.next()
    // A.K.A. customer names.
    val groupNames = titleLine.split("\t").drop(1).map(_.trim).toIndexedSeq

    val zoneNumLine = lines.next()
    val zoneNums = zoneNumLine.split("\t").drop(1).map(_.trim).toIndexedSeq

    val criticalityLine = lines.next()
    val criticalities = criticalityLine.split("\t").drop(1).map(_.trim).toIndexedSeq

    val feederLine = lines.next()
    val feeders = feederLine.split("\t").drop(1).map(_.trim).toIndexedSeq

    val allEntries = lines.toSeq.map { line =>
      val lineParts = line.split("\t")
      val values = lineParts.drop(1).map(_.toDouble).toIndexedSeq
      values
    }.toVector

    LoadRecord(groupNames, criticalities, feeders, allEntries)
  }

}
