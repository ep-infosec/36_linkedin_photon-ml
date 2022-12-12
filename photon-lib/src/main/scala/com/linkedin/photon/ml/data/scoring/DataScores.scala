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
package com.linkedin.photon.ml.data.scoring

import scala.reflect.ClassTag

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.spark.rdd.RDD.rddToPairRDDFunctions
import org.apache.spark.storage.StorageLevel

import com.linkedin.photon.ml.Types.UniqueSampleId
import com.linkedin.photon.ml.spark.RDDLike

/**
 * A base class for tracking scored data points, where the scores are stored in an [[RDD]] which associates the unique
 * ID of a data point with a score object.
 *
 * @param scoresRdd Data point scores, as described above
 */
abstract protected[ml] class DataScores[T : ClassTag, D <: DataScores[T, D]](
    val scoresRdd: RDD[(UniqueSampleId, T)])
  extends RDDLike {

  /**
   * The addition operation for [[DataScores]].
   *
   * @note This operation performs a full outer join.
   * @param that The [[DataScores]] instance to add to this instance
   * @return A new [[DataScores]] instance encapsulating the accumulated values
   */
  def +(that: D): D

  /**
   * The minus operation for [[DataScores]].
   *
   * @note This operation performs a full outer join.
   * @param that The [[DataScores]] instance to subtract from this instance
   * @return A new [[DataScores]] instance encapsulating the subtracted values
   */
  def -(that: D): D

  /**
   * Get the Spark context for the distributed scores.
   *
   * @return The Spark context
   */
  override def sparkContext: SparkContext = scoresRdd.sparkContext

  /**
   * Set the name of [[scoresRdd]].
   *
   * @param name The parent name for all [[RDD]]s in this class
   * @return This object with the name of [[scoresRdd]] assigned
   */
  override def setName(name: String): RDDLike = {

    scoresRdd.setName(name)

    this
  }

  /**
   * Set the storage level of [[scoresRdd]].
   *
   * @param storageLevel The storage level
   * @return This object with the storage level of [[scoresRdd]] set
   */
  override def persistRDD(storageLevel: StorageLevel): RDDLike = {

    if (!scoresRdd.getStorageLevel.isValid) scoresRdd.persist(storageLevel)

    this
  }

  /**
   * Mark [[scoresRdd]] as non-persistent, and remove all blocks for them from memory and disk.
   *
   * @return This object with [[scoresRdd]] marked non-persistent
   */
  override def unpersistRDD(): RDDLike = {

    if (scoresRdd.getStorageLevel.isValid) scoresRdd.unpersist()

    this
  }

  /**
   * Materialize [[scoresRdd]] (Spark [[RDD]]s are lazy evaluated: this method forces them to be evaluated).
   *
   * @return This object with [[scoresRdd]] materialized
   */
  override def materialize(): RDDLike = {

    scoresRdd.count()

    this
  }

  /**
   * Method used to define equality on multiple class levels while conforming to equality contract. Defines under
   * what circumstances this class can equal another class.
   *
   * @param other Some other object
   * @return Whether this object can equal the other object
   */
  def canEqual(other: Any): Boolean = other.isInstanceOf[DataScores[T, D]]

  /**
   * Compare two [[DataScores]]s objects.
   *
   * @param other Some other object
   * @return True if the both [[DataScores]] objects have identical scores for each unique ID, false otherwise
   */
  override def equals(other: Any): Boolean = other match {

    case that: DataScores[T, D] =>

      val canEqual = this.canEqual(that)
      lazy val areEqual = this
        .scoresRdd
        .fullOuterJoin(that.scoresRdd)
        .mapPartitions { iterator =>

          val areScoresEqual = iterator.forall {
            case (_, (Some(thisScore), Some(thatScore))) => thisScore.equals(thatScore)
            case _ => false
          }

          Iterator.single(areScoresEqual)
        }
        .fold(true)(_ && _)

      canEqual && areEqual

    case _ =>
      false
  }

  /**
   * Returns a hash code value for the object.
   *
   * @return An [[Int]] hash code
   */
  override def hashCode: Int = scoresRdd.hashCode()
}
