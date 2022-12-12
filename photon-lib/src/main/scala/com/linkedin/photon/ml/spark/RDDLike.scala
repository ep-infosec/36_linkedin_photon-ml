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
package com.linkedin.photon.ml.spark

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel

/**
 * A trait containing simple operations on [[RDD]]s.
 */
protected[ml] trait RDDLike {

  /**
   * Get the Spark context.
   *
   * @return The Spark context
   */
  protected[ml] def sparkContext: SparkContext

  /**
   * Assign a given name to all [[RDD]]s in this object.
   *
   * @note Not used to reference models in the logic of photon-ml, only used for logging currently.
   * @param name The parent name for all [[RDD]]s in this class
   * @return This object with the names of all of its [[RDD]]s assigned
   */
  protected[ml] def setName(name: String): RDDLike

  /**
   * Set the storage level of all [[RDD]]s in this object, and persist their values across the cluster the first time
   * they are computed.
   *
   * @param storageLevel The storage level
   * @return This object with the storage level of all of its [[RDD]]s set
   */
  protected[ml] def persistRDD(storageLevel: StorageLevel): RDDLike

  /**
   * Mark all [[RDD]]s in this object as non-persistent, and remove all blocks for them from memory and disk.
   *
   * @return This object with all of its [[RDD]]s marked non-persistent
   */
  protected[ml] def unpersistRDD(): RDDLike

  /**
   * Materialize all the [[RDD]]s (Spark [[RDD]]s are lazy evaluated: this method forces them to be evaluated).
   *
   * @return This object with all of its [[RDD]]s materialized
   */
  protected[ml] def materialize(): RDDLike
}
