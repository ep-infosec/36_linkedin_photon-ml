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
package com.linkedin.photon.ml.hyperparameter.criteria

import breeze.linalg.DenseVector
import breeze.numerics.sqrt
import breeze.stats.distributions.Gaussian

import com.linkedin.photon.ml.hyperparameter.estimators.PredictionTransformation

/**
 * Expected improvement selection criterion. This transformation produces the expected improvement of the model
 * predictions (over the current "best" value).
 *
 * @see "Practical Bayesian Optimization of Machine Learning Algorithms" (PBO),
 *   https://papers.nips.cc/paper/4522-practical-bayesian-optimization-of-machine-learning-algorithms.pdf
 *
 * @param bestEvaluation The current best evaluation
 */
class ExpectedImprovement(bestEvaluation: Double) extends PredictionTransformation {

  // Maximize EI to minimize the evaluation value.
  def isMaxOpt: Boolean = true

  private val standardNormal = new Gaussian(0, 1)

  /**
   * Applies the expected improvement transformation to the model output.
   *
   * @param predictiveMeans Predictive mean output from the model
   * @param predictiveVariances Predictive variance output from the model
   * @return The expected improvement over the current best evaluation
   */
  def apply(
      predictiveMeans: DenseVector[Double],
      predictiveVariances: DenseVector[Double]): DenseVector[Double] = {

    val std = sqrt(predictiveVariances)

    // PBO Eq. 1
    val gamma = - (predictiveMeans - bestEvaluation) / std

    // Eq. 2
    std :* ((gamma :* gamma.map(standardNormal.cdf)) + gamma.map(standardNormal.pdf))
  }
}
