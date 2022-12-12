/*
 * Copyright 2017 LinkedIn Corp. All rights reserved.
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

import org.apache.hadoop.fs.Path
import org.testng.Assert._
import org.testng.annotations.{DataProvider, Test}

import com.linkedin.photon.ml.cli.game.scoring.ScoredItem
import com.linkedin.photon.ml.test.{SparkTestUtils, TestTemplateWithTmpDir}

/**
 * Integration tests for [[ScoreProcessingUtils]].
 */
class ScoreProcessingUtilsIntegTest extends SparkTestUtils with TestTemplateWithTmpDir {

  private val completeScoreItems = Array(
    ScoredItem(
      predictionScore = 1.0,
      label = Some(1.0),
      weight = Some(1.0),
      idTagToValueMap = Map((AvroFieldNames.UID, "1"), ("id2", "2"))),
    ScoredItem(
      predictionScore = 0.0,
      label = Some(0.0),
      weight = Some(2.0),
      idTagToValueMap = Map((AvroFieldNames.UID, "3"), ("id2", "4"))),
    ScoredItem(
      predictionScore = 0.5,
      label = Some(0.5),
      weight = Some(-1.0),
      idTagToValueMap = Map((AvroFieldNames.UID, "5"), ("id2", "6"))),
    ScoredItem(
      predictionScore = -1.0,
      label = Some(-0.5),
      weight = Some(0.0),
      idTagToValueMap = Map((AvroFieldNames.UID, "7"), ("id2", "8")))
  )

  private val scoredItemsWithoutUid = Array(
    ScoredItem(predictionScore = 1.0, label = Some(1.0), weight = Some(1.0), idTagToValueMap = Map("id2" -> "2")),
    ScoredItem(predictionScore = 0.0, label = Some(0.0), weight = Some(2.0), idTagToValueMap = Map("id2" -> "4")),
    ScoredItem(predictionScore = 0.5, label = Some(0.5), weight = Some(-1.0), idTagToValueMap = Map("id2" -> "6")),
    ScoredItem(predictionScore = -1.0, label = Some(-0.5), weight = Some(0.0), idTagToValueMap = Map("id2" -> "8"))
  )

  private val scoredItemsWithoutLabel = Array(
    ScoredItem(
      predictionScore = 1.0,
      label = None,
      weight = Some(1.0),
      idTagToValueMap = Map((AvroFieldNames.UID, "1"), ("id2", "2"))),
    ScoredItem(
      predictionScore = 0.0,
      label = None,
      weight = Some(2.0),
      idTagToValueMap = Map((AvroFieldNames.UID, "3"), ("id2", "4"))),
    ScoredItem(
      predictionScore = 0.5,
      label = None,
      weight = Some(-1.0),
      idTagToValueMap = Map((AvroFieldNames.UID, "5"), ("id2", "6"))),
    ScoredItem(
      predictionScore = -1.0,
      label = None,
      weight = Some(0.0),
      idTagToValueMap = Map((AvroFieldNames.UID, "7"), ("id2", "8")))
  )

  private val scoredItemsWithoutWeight = Array(
    ScoredItem(
      predictionScore = 1.0,
      label = Some(1.0),
      weight = None,
      idTagToValueMap = Map((AvroFieldNames.UID, "1"), ("id2", "2"))),
    ScoredItem(
      predictionScore = 0.0,
      label = Some(0.0),
      weight = None,
      idTagToValueMap = Map((AvroFieldNames.UID, "3"), ("id2", "4"))),
    ScoredItem(
      predictionScore = 0.5,
      label = Some(0.5),
      weight = None,
      idTagToValueMap = Map((AvroFieldNames.UID, "5"), ("id2", "6"))),
    ScoredItem(
      predictionScore = -1.0,
      label = Some(-0.5),
      weight = None,
      idTagToValueMap = Map((AvroFieldNames.UID, "7"), ("id2", "8")))
  )

  private val scoredItemsWithoutIds = Array(
    ScoredItem(predictionScore = 1.0, label = Some(1.0), weight = Some(1.0), idTagToValueMap = Map[String, String]()),
    ScoredItem(predictionScore = 0.0, label = Some(0.0), weight = Some(2.0), idTagToValueMap = Map[String, String]()),
    ScoredItem(predictionScore = 0.5, label = Some(0.5), weight = Some(-1.0), idTagToValueMap = Map[String, String]()),
    ScoredItem(predictionScore = -1.0, label = Some(-0.5), weight = Some(0.0), idTagToValueMap = Map[String, String]())
  )

  private val scoredItemsWithOnlyScores = Array(
    ScoredItem(predictionScore = 1.0, label = None, weight = None, idTagToValueMap = Map[String, String]()),
    ScoredItem(predictionScore = 0.0, label = None, weight = None, idTagToValueMap = Map[String, String]()),
    ScoredItem(predictionScore = 0.5, label = None, weight = None, idTagToValueMap = Map[String, String]()),
    ScoredItem(predictionScore = -1.0, label = None, weight = None, idTagToValueMap = Map[String, String]())
  )

  private val mixedScoreItems = Array(
    ScoredItem(
      predictionScore = 1.0,
      label = None,
      weight = Some(1.0),
      idTagToValueMap = Map((AvroFieldNames.UID, "1"), ("id2", "2"))),
    ScoredItem(
      predictionScore = 0.0,
      label = Some(0.0),
      weight = None,
      idTagToValueMap = Map((AvroFieldNames.UID, "3"), ("id2", "4"))),
    ScoredItem(
      predictionScore = 0.5,
      label = Some(0.5),
      weight = Some(-1.0),
      idTagToValueMap = Map[String, String]()),
    ScoredItem(
      predictionScore = -1.0,
      label = Some(-0.5),
      weight = Some(0.0),
      idTagToValueMap = Map((AvroFieldNames.UID, "7"), ("id2", "8")))
  )

  @DataProvider
  def scoredItemsProvider():Array[Array[Any]] = {
    Array(
      Array("completeScoreItems", completeScoreItems),
      Array("scoredItemsWithoutUid", scoredItemsWithoutUid),
      Array("scoredItemsWithoutLabel", scoredItemsWithoutLabel),
      Array("scoredItemsWithoutWeight", scoredItemsWithoutWeight),
      Array("scoredItemsWithoutIds", scoredItemsWithoutIds),
      Array("scoredItemsWithOnlyScores", scoredItemsWithOnlyScores),
      Array("mixedScoreItems", mixedScoreItems)
    )
  }

  @Test(dataProvider = "scoredItemsProvider")
  def testLoadAndSaveScoredItems(modelId: String, scoredItems: Array[ScoredItem]): Unit =
    sparkTest("testLoadAndSaveScoredItems") {
      val scoredItemsAsRDD = sc.parallelize(scoredItems, 1)
      val dir = new Path(getTmpDir, "scores").toString
      ScoreProcessingUtils.saveScoredItemsToHDFS(scoredItemsAsRDD, dir, Some(modelId))
      val loadedModelIdWithScoredItemAsRDD = ScoreProcessingUtils.loadScoredItemsFromHDFS(dir, sc)
      val loadedModelIds = loadedModelIdWithScoredItemAsRDD.map(_._1)

      // Same model Id
      assertTrue(loadedModelIds.collect().forall(_ == modelId))
      val loadedScoredItemAsRDD = loadedModelIdWithScoredItemAsRDD.map(_._2)
      val loadedScoredItem = loadedScoredItemAsRDD.collect()

      // Same scored items
      assertEquals(loadedScoredItem.deep, scoredItems.deep)

      // Same unique ids
      val loadedUids = loadedScoredItem.map(_.idTagToValueMap.get(AvroFieldNames.UID))
      val uids = scoredItems.map(_.idTagToValueMap.get(AvroFieldNames.UID))
      assertEquals(loadedUids.deep, uids.deep)
    }
}
