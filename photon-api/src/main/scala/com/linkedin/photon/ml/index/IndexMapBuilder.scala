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
package com.linkedin.photon.ml.index

/**
 * This trait defines a proper contract for an IndexMap.
 */
trait IndexMapBuilder {

  /**
   * Initialize an IndexMapBuilder, should be triggered as the 1st step of a builder.
   *
   * @param outputDir The directory to store the built index map file
   * @param partitionId The partition id of the current builder
   * @return The current builder
   */
  def init(outputDir: String, partitionId: Int, namespace: String): IndexMapBuilder

  /**
   * Close current builder.
   *
   * The contract is that after close(), the feature names and indexes need to be persisted
   * on disk. They might not be before close() is called.
   */
  def close(): Unit

  /**
   * Put a feature into map using a specific indexing rule.
   *
   * This does not require that the feature name and index be persisted on disk.
   *
   * @param name The feature name
   * @param idx The feature index
   * @return The current builder
   */
  def put(name: String, idx: Int): IndexMapBuilder
}
