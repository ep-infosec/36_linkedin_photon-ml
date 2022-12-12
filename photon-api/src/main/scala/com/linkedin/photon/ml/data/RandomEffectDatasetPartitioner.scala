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
package com.linkedin.photon.ml.data

import scala.collection.{Map, immutable, mutable}

import org.apache.spark.broadcast.Broadcast
import org.apache.spark.rdd.RDD
import org.apache.spark.{HashPartitioner, Partitioner}

import com.linkedin.photon.ml.Types.REId
import com.linkedin.photon.ml.spark.BroadcastLike

/**
 * Partitioner implementation for random effect datasets.
 *
 * In GAME, we can improve on Spark default partitioning by using domain-specific knowledge in two ways. First, we can
 * reduce time spent in shuffle operations by leveraging training record keys (helping joins). Second, we assume that
 * each random effect has less than the maximum partition size of associated training data, i.e. that all the training
 * data for a given RE will fit within a single Spark data partition. So we can group the training records so that they
 * all land in the same partition for a given RE, which is what RandomEffectDatasetPartitioner is about.
 *
 * RandomEffectDatasetPartitioner also makes sure that partitions are as equally balanced as possible, to equalize the
 * workload of the executors: because we assume the data for each random effect is small, it will usually not even fill
 * a Spark data partition, so we fill up the partition (i.e. add (id/partition) records to idToPartitionMap with data
 * for multiple random effects). However, since idToPartitionMap is eventually broadcast to the executors, we also want
 * to keep the size of that Map under control (see parameter partitionerCapacity below).
 *
 * @param numPartitions Number of partitions across which to split random effects
 * @param idToPartitionMap Random effect type to partition map
 */
protected[ml] class RandomEffectDatasetPartitioner(
    val numPartitions: Int,
    private val idToPartitionMap: Broadcast[Map[REId, Int]])
  extends Partitioner
    with BroadcastLike {

  // Backup partitioner for random effect IDs not found in the primary assignment Map
  lazy private val backupPartitioner: HashPartitioner = new HashPartitioner(numPartitions)

  /**
   * Asynchronously delete cached copies of this broadcast on the executors.
   *
   * @return This object with all its broadcast variables unpersisted
   */
  override def unpersistBroadcast(): this.type = {
    idToPartitionMap.unpersist()
    this
  }

  /**
   * Compares two [[RandomEffectDatasetPartitioner]] objects.
   *
   * @param that Some other object
   * @return True if the two partitioners have the same idToPartitionMap, false otherwise
   */
  override def equals(that: Any): Boolean =
    that match {
      case other: RandomEffectDatasetPartitioner => this.idToPartitionMap.value.equals(other.idToPartitionMap.value)
      case _ => false
    }

  /**
   * Returns a hash code value for the object.
   *
   * @return An [[Int]] hash code
   */
  override def hashCode: Int = idToPartitionMap.hashCode()

  /**
   * For a given key, get the corresponding partition id. If the key is not in any partition, we randomly assign
   * the training vector to a partition (with Spark's HashPartitioner).
   *
   * @param key A training vector key (String).
   * @return The partition id to which the training vector belongs.
   */
  def getPartition(key: Any): Int = key match {
    case reId: REId =>
      idToPartitionMap.value.getOrElse(reId, backupPartitioner.getPartition(reId))

    case any =>
      throw new IllegalArgumentException(s"Expected key of ${this.getClass} is String, but ${any.getClass} found")
  }
}

object RandomEffectDatasetPartitioner {

  /**
   * Generate a partitioner for one random effect model.
   *
   * Multiple random effect models, one per random effect ID (e.g. "user123"), are instantiated for a single random
   * effect type (e.g. "per-user"), and each of these instantiations is trained with training vectors marked for that
   * random effect ID. We collect the training vector ids that correspond to the random effect type, then build an id
   * to partition map. Data should be distributed across partitions as equally as possible. Since some items have more
   * data points than others, this partitioner uses simple 'bin packing' for distributing data load across partitions
   * (using minHeap).
   *
   * We stop filling in idToPartitionMap at partitionerCapacity records, because this map is passed to the executors
   * and we therefore wish to control/limit its size.
   *
   * @param gameDataset The GAME training dataset
   * @param reConfig The random effect data configuration options
   * @param partitionerCapacity The partitioner capacity
   * @return A partitioner for one random effect model
   */
  def fromGameDataset(
      gameDataset: RDD[(Long, GameDatum)],
      reConfig: RandomEffectDataConfiguration,
      partitionerCapacity: Int = 10000): RandomEffectDatasetPartitioner = {

    val numPartitions = reConfig.minNumPartitions
    val randomEffectType = reConfig.randomEffectType
    val activeDataUpperBoundOpt = reConfig.numActiveDataPointsUpperBound

    require(numPartitions > 0, s"Number of partitions ($numPartitions) has to be larger than 0.")

    val rawSortedRandomEffectTypes = gameDataset
      .values
      .filter(_.idTagToValueMap.contains(randomEffectType))
      .map(gameData => (gameData.idTagToValueMap(randomEffectType), 1))
      .reduceByKey(_ + _)
      .collect()
      .sortBy(_._2 * -1)
      .take(partitionerCapacity)

    // If the number of active samples is bounded, we can partition them better by using the bound as the count
    val sortedRandomEffectTypes = activeDataUpperBoundOpt match {
      case Some(bound) =>
        rawSortedRandomEffectTypes.map { case (reId, count) =>

          val newCount = if (count > bound) bound else count

          (reId, newCount)
        }

      case None =>
        rawSortedRandomEffectTypes
    }

    val ordering = new Ordering[(Int, Int)] {
      def compare(pair1: (Int, Int), pair2: (Int, Int)): Int = pair2._2 compare pair1._2
    }

    val minHeap = mutable.PriorityQueue.newBuilder[(Int, Int)](ordering)
    minHeap ++= Array.tabulate[(Int, Int)](numPartitions)(i => (i, 0))
    val idToPartitionMapBuilder = immutable.Map.newBuilder[String, Int]
    idToPartitionMapBuilder.sizeHint(numPartitions)

    sortedRandomEffectTypes.foreach { case (id, size) =>
      val (partition, currentSize) = minHeap.dequeue()
      idToPartitionMapBuilder += id -> partition
      minHeap.enqueue((partition, currentSize + size))
    }

    new RandomEffectDatasetPartitioner(
      numPartitions,
      gameDataset.sparkContext.broadcast(idToPartitionMapBuilder.result()))
  }
}
