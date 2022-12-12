/*
 * Copyright 2018 LinkedIn Corp. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.linkedin.photon.ml.data.avro

import org.apache.spark.sql.Row
import org.apache.spark.sql.types.DataTypes._
import org.testng.Assert._
import org.apache.spark.ml.linalg.Vectors
import org.apache.spark.ml.linalg.SQLDataTypes.VectorType
import org.apache.spark.sql.catalyst.expressions.GenericRowWithSchema
import org.apache.spark.sql.types.{StructField, StructType}
import org.testng.annotations.{DataProvider, Test}

import com.linkedin.photon.ml.Constants.DELIMITER
import com.linkedin.photon.ml.index.{DefaultIndexMap, IndexMap}

/**
 * Unit tests for [[AvroDataWriter]].
 */
class AvroDataWriterTest {

  @DataProvider
  def rowsProvider(): Array[Array[GenericRowWithSchema]] = {

    val vector = Vectors.sparse(3, Array(0, 2), Array(0.0, 1.0))
    val arrays = Array[Array[Any]](
      Array(1, 0, 1, vector),
      Array(true, false, true, vector),
      Array(1.0f, 0.0f, 1.0f, vector),
      Array(1L, 0L, 1L, vector),
      Array(1.0D, 0.0D, 1.0D, vector),
      Array("1", "0.0", "1E0", vector))
    val types = Array(IntegerType, BooleanType, FloatType, LongType, DoubleType, StringType)

    // Build a row with null fields for offset and weight
    val nullArray = Array(1.0D, null, null, vector)
    val nullSchema = new StructType(
      Array(
        StructField("response", DoubleType),
        StructField("offset", NullType),
        StructField("weight", NullType),
        StructField("features", VectorType)))
    val nullRow = new GenericRowWithSchema(nullArray, nullSchema)

    val rows = arrays
      .zip(types)
      .map { case (a, t) =>
        val schema = new StructType(
          Array(
            StructField("response", t),
            StructField("offset", t),
            StructField("weight", t),
            StructField("features", VectorType)))

        Array(new GenericRowWithSchema(a, schema))
      }

    rows :+ Array(nullRow)
  }

  @Test(dataProvider = "rowsProvider")
  def testGetValueAsDouble(row: Row): Unit = {

    val label = AvroDataWriter.getValueAsDouble(row, "response")
    assertEquals(label, 1.0D)
    val offset = AvroDataWriter.getValueAsDouble(row, "offset")
    assertEquals(offset, 0.0D)
    val weight = AvroDataWriter.getValueAsDouble(row, "weight")
    assertEquals(weight, 1.0D)
  }

  @Test
  def testBuildAvroFeatures(): Unit = {

    val vector = Vectors.sparse(3, Array(0, 1, 2), Array(1.0, 2.0, 3.0))
    val indexMap: IndexMap = new DefaultIndexMap(
      featureNameToIdMap = Map(
        s"name0${DELIMITER}term0" -> 0,
        s"name1$DELIMITER" -> 1,
        s"${DELIMITER}term2" -> 2))
    val results = AvroDataWriter.buildAvroFeatures(vector, indexMap)
    assertEquals(results.size(), 3)
    assertEquals(results.get(0).getName, "name0")
    assertEquals(results.get(0).getTerm, "term0")
    assertEquals(results.get(0).getValue, 1.0)
    assertEquals(results.get(1).getName, "name1")
    assertEquals(results.get(1).getTerm, "")
    assertEquals(results.get(1).getValue, 2.0)
    assertEquals(results.get(2).getName, "")
    assertEquals(results.get(2).getTerm, "term2")
    assertEquals(results.get(2).getValue, 3.0)
  }
}
