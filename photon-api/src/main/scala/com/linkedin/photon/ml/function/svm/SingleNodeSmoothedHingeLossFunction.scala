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
package com.linkedin.photon.ml.function.svm

import breeze.linalg.Vector

import com.linkedin.photon.ml.data.LabeledPoint
import com.linkedin.photon.ml.function.{DiffFunction, L2RegularizationDiff, SingleNodeObjectiveFunction}
import com.linkedin.photon.ml.normalization.NormalizationContext
import com.linkedin.photon.ml.optimization.RegularizationType
import com.linkedin.photon.ml.optimization.game.GLMOptimizationConfiguration
import com.linkedin.photon.ml.util.{BroadcastWrapper, VectorUtils}

/**
 * This class is used to calculate the value and gradient of Rennie's smoothed hinge loss function, as an approximation
 * of a linear SVM, for individual optimization problems.
 *
 * FAQ: Why use cumGradient (cumulative gradient)?
 * A:   Using cumGradient allows the functions to avoid memory allocation by modifying and returning cumGradient instead
 *      of creating a new gradient vector.
 */
protected[ml] class SingleNodeSmoothedHingeLossFunction extends SingleNodeObjectiveFunction with DiffFunction {

  /**
   * Compute the value of the function over the given data for the given model coefficients.
   *
   * @param input The given data over which to compute the objective value
   * @param coefficients The model coefficients used to compute the function's value
   * @param normalizationContext The normalization context
   * @return The computed value of the function
   */
  override protected[ml] def value(
      input: Iterable[LabeledPoint],
      coefficients: Vector[Double],
      normalizationContext: BroadcastWrapper[NormalizationContext]): Double =
    calculate(input, coefficients, normalizationContext)._1

  /**
   * Compute the gradient of the function over the given data for the given model coefficients.
   *
   * @param input The given data over which to compute the gradient
   * @param coefficients The model coefficients used to compute the function's gradient
   * @param normalizationContext The normalization context
   * @return The computed gradient of the function
   */
  override protected[ml] def gradient(
      input: Iterable[LabeledPoint],
      coefficients: Vector[Double],
      normalizationContext: BroadcastWrapper[NormalizationContext]): Vector[Double] =
    calculate(input, coefficients, normalizationContext)._2

  /**
   * Compute both the value and the gradient of the function for the given model coefficients (computing value and
   * gradient at once is sometimes more efficient than computing them sequentially).
   *
   * @param input The given data over which to compute the value and gradient
   * @param coefficients The model coefficients used to compute the function's value and gradient
   * @param normalizationContext The normalization context
   * @return The computed value and gradient of the function
   */
  override protected[ml] def calculate(
      input: Iterable[LabeledPoint],
      coefficients: Vector[Double],
      normalizationContext: BroadcastWrapper[NormalizationContext]): (Double, Vector[Double]) = {
    val initialCumGradient = VectorUtils.zeroOfSameType(coefficients)

    input.aggregate((0.0, initialCumGradient))(
      seqop = {
        case ((loss, cumGradient), datum) =>
          val v = SmoothedHingeLossFunction.calculateAt(datum, coefficients, cumGradient)
          (loss + v, cumGradient)
      },
      combop = {
        case ((loss1, grad1), (loss2, grad2)) =>
          (loss1 + loss2, grad1 += grad2)
      })
  }
}

object SingleNodeSmoothedHingeLossFunction {

  /**
   * Factory method to create a new objective function with SingleNodeSmoothedHingeLossFunction as the base loss
   * function.
   *
   * @param configuration The optimization problem configuration
   * @param interceptIndexOpt The index of the intercept, if there is one
   * @return A new SingleNodeSmoothedHingeLossFunction
   */
  def apply(
      configuration: GLMOptimizationConfiguration,
      interceptIndexOpt: Option[Int] = None): SingleNodeSmoothedHingeLossFunction = {

    val regularizationContext = configuration.regularizationContext
    val regularizationWeight = configuration.regularizationWeight

    regularizationContext.regularizationType match {
      case RegularizationType.L2 | RegularizationType.ELASTIC_NET =>
        new SingleNodeSmoothedHingeLossFunction with L2RegularizationDiff {
          l2RegWeight = regularizationContext.getL2RegularizationWeight(regularizationWeight)

          override def interceptOpt: Option[Int] = interceptIndexOpt
        }

      case _ => new SingleNodeSmoothedHingeLossFunction
    }
  }
}
