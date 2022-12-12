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
package com.linkedin.photon.ml.io.deprecated

import org.apache.hadoop.fs.Path
import org.apache.spark.SparkContext

import com.linkedin.photon.ml.Params
import com.linkedin.photon.ml.index.PalDBIndexMapLoader

/**
 * This is a factory for [[InputDataFormat]].
 */
object InputFormatFactory {

  /**
   * Generate an appropriate [[InputDataFormat]] based on the input parameters.
   *
   * @param sc The Spark context
   * @param params The input parameters
   * @return An appropriate [[InputDataFormat]] for the input parameters
   */
  def createInputFormat(sc: SparkContext, params: Params): InputDataFormat = {
    params.inputFormatType match {
      case InputFormatType.AVRO =>
        // Prepare offHeapIndexMap loader if provided
        val offHeapIndexMapLoader = params.offHeapIndexMapDir match {
          case Some(offHeapDir) =>
            Some(PalDBIndexMapLoader(sc, new Path(offHeapDir), params.offHeapIndexMapNumPartitions))
          case None => None
        }

        // Initialize GLMSuite
        val suite = new GLMSuite(params.fieldsNameType,
          params.addIntercept,
          params.constraintString,
          offHeapIndexMapLoader)

        new AvroInputDataFormat(suite)

      case InputFormatType.LIBSVM =>
        if (params.featureDimension <= 0) {
          throw new IllegalArgumentException(
            "LibSVM format must know the total feature dimension beforehand. (A rough upper bound is okay)")
        }

        new LibSVMInputDataFormat(params.featureDimension,
          params.addIntercept
        )

      case _ =>
        throw new IllegalArgumentException("InputFormat unsupported.")
    }
  }
}
