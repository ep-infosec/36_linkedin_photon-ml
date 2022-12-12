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
package com.linkedin.photon.ml.function

import java.util.Random

import breeze.linalg.{DenseVector, SparseVector, Vector}
import org.mockito.Mockito._
import org.testng.Assert.{assertEquals, assertTrue}
import org.testng.annotations.{DataProvider, Test}

import com.linkedin.photon.ml.TaskType
import com.linkedin.photon.ml.data.LabeledPoint
import com.linkedin.photon.ml.function.glm.{LogisticLossFunction, PoissonLossFunction, SingleNodeGLMLossFunction, SquaredLossFunction}
import com.linkedin.photon.ml.function.svm.SingleNodeSmoothedHingeLossFunction
import com.linkedin.photon.ml.normalization.{NoNormalization, NormalizationContext}
import com.linkedin.photon.ml.optimization.game.GLMOptimizationConfiguration
import com.linkedin.photon.ml.optimization.{L2RegularizationContext, NoRegularizationContext}
import com.linkedin.photon.ml.test.SparkTestUtils
import com.linkedin.photon.ml.util.BroadcastWrapper

/**
 * Unit tests to verify that the loss functions compute gradients & Hessians accurately.
 */
class SingleNodeObjectiveFunctionTest extends SparkTestUtils {

  import SingleNodeObjectiveFunctionTest._

  private val twiceDiffTasks =
    Array(TaskType.LOGISTIC_REGRESSION, TaskType.LINEAR_REGRESSION, TaskType.POISSON_REGRESSION)
  private val diffTasks = twiceDiffTasks ++ Array(TaskType.SMOOTHED_HINGE_LOSS_LINEAR_SVM)
  private val binaryClassificationDatasetGenerationFuncs = Array(
    generateBenignDatasetBinaryClassification _,
    generateWeightedBenignDatasetBinaryClassification _,
    generateOutlierDatasetBinaryClassification _,
    generateWeightedOutlierDatasetBinaryClassification _)
  private val linearRegressionDatasetGenerationFuncs = Array(
    generateBenignDatasetLinearRegression _,
    generateWeightedBenignDatasetLinearRegression _,
    generateOutlierDatasetLinearRegression _,
    generateWeightedOutlierDatasetLinearRegression _)
  private val poissonRegressionDatasetGenerationFuncs = Array(
    generateBenignDatasetPoissonRegression _,
    generateWeightedBenignDatasetPoissonRegression _,
    generateOutlierDatasetPoissonRegression _,
    generateWeightedOutlierDatasetPoissonRegression _)

  /**
   * Generate loss functions objects for classes implementing DiffFunction.
   *
   * @return Anonymous functions to generate the loss function and training data for the gradient tests
   */
  @DataProvider(parallel = true)
  def getDifferentiableFunctions: Array[Array[Object]] = diffTasks.flatMap {
      case TaskType.LOGISTIC_REGRESSION =>
        val lossFunc = SingleNodeGLMLossFunction(NO_REG_CONFIGURATION_MOCK, LogisticLossFunction)
        val lossFuncWithL2 = SingleNodeGLMLossFunction(L2_REG_CONFIGURATION_MOCK, LogisticLossFunction)

        binaryClassificationDatasetGenerationFuncs.flatMap { dataGenFunc =>
          Seq[(SingleNodeObjectiveFunction, _)]((lossFunc, dataGenFunc), (lossFuncWithL2, dataGenFunc))
        }

      case TaskType.LINEAR_REGRESSION =>
        val lossFunc = SingleNodeGLMLossFunction(NO_REG_CONFIGURATION_MOCK, SquaredLossFunction)
        val lossFuncWithL2 = SingleNodeGLMLossFunction(L2_REG_CONFIGURATION_MOCK, SquaredLossFunction)

        linearRegressionDatasetGenerationFuncs.flatMap { dataGenFunc =>
          Seq[(SingleNodeObjectiveFunction, _)]((lossFunc, dataGenFunc), (lossFuncWithL2, dataGenFunc))
        }

      case TaskType.POISSON_REGRESSION =>
        val lossFunc = SingleNodeGLMLossFunction(NO_REG_CONFIGURATION_MOCK, PoissonLossFunction)
        val lossFuncWithL2 = SingleNodeGLMLossFunction(L2_REG_CONFIGURATION_MOCK, PoissonLossFunction)

        poissonRegressionDatasetGenerationFuncs.flatMap { dataGenFunc =>
          Seq[(SingleNodeObjectiveFunction, _)]((lossFunc, dataGenFunc), (lossFuncWithL2, dataGenFunc))
        }

      case TaskType.SMOOTHED_HINGE_LOSS_LINEAR_SVM =>
        val lossFunc = SingleNodeSmoothedHingeLossFunction(NO_REG_CONFIGURATION_MOCK)
        val lossFuncWithL2 = SingleNodeSmoothedHingeLossFunction(L2_REG_CONFIGURATION_MOCK)

        binaryClassificationDatasetGenerationFuncs.flatMap { dataGenFunc =>
          Seq[(SingleNodeObjectiveFunction, _)]((lossFunc, dataGenFunc), (lossFuncWithL2, dataGenFunc))
        }

      case other =>
        throw new IllegalArgumentException(s"Unrecognized task type: $other")
    }
    .map(pair => Array(pair._1, pair._2).asInstanceOf[Array[Object]])

  /**
   * Generate loss functions objects for classes implementing TwiceDiffFunction.
   *
   * @return Anonymous functions to generate the loss function and training data for the Hessian tests
   */
  @DataProvider(parallel = true)
  def getTwiceDifferentiableFunctions: Array[Array[Object]] = twiceDiffTasks.flatMap {
      case TaskType.LOGISTIC_REGRESSION =>
        val lossFunc = SingleNodeGLMLossFunction(NO_REG_CONFIGURATION_MOCK, LogisticLossFunction)
        val lossFuncWithL2 = SingleNodeGLMLossFunction(L2_REG_CONFIGURATION_MOCK, LogisticLossFunction)

        binaryClassificationDatasetGenerationFuncs.flatMap { dataGenFunc =>
          Seq((lossFunc, dataGenFunc), (lossFuncWithL2, dataGenFunc))
        }

      case TaskType.LINEAR_REGRESSION =>
        val lossFunc = SingleNodeGLMLossFunction(NO_REG_CONFIGURATION_MOCK, SquaredLossFunction)
        val lossFuncWithL2 = SingleNodeGLMLossFunction(L2_REG_CONFIGURATION_MOCK, SquaredLossFunction)

        linearRegressionDatasetGenerationFuncs.flatMap { dataGenFunc =>
          Seq((lossFunc, dataGenFunc), (lossFuncWithL2, dataGenFunc))
        }

      case TaskType.POISSON_REGRESSION =>
        val lossFunc = SingleNodeGLMLossFunction(NO_REG_CONFIGURATION_MOCK, PoissonLossFunction)
        val lossFuncWithL2 = SingleNodeGLMLossFunction(L2_REG_CONFIGURATION_MOCK, PoissonLossFunction)

        poissonRegressionDatasetGenerationFuncs.flatMap { dataGenFunc =>
          Seq((lossFunc, dataGenFunc), (lossFuncWithL2, dataGenFunc))
        }

      case other =>
        throw new IllegalArgumentException(s"Unrecognized task type: $other")
    }
    .map(pair => Array(pair._1, pair._2))

  //
  // Binary classification dataset generation functions
  //

  /**
   * Generate unweighted benign datasets for binary classification.
   *
   * @return A List of [[LabeledPoint]]
   */
  def generateBenignDatasetBinaryClassification(): List[LabeledPoint] =
    drawBalancedSampleFromNumericallyBenignDenseFeaturesForBinaryClassifierLocal(
      DATA_RANDOM_SEED,
      TRAINING_SAMPLES,
      PROBLEM_DIMENSION)
      .map { obj =>
        assertEquals(obj._2.length, PROBLEM_DIMENSION, "Samples should have expected lengths")
        new LabeledPoint(label = obj._1, features = obj._2)
      }
      .toList

  /**
   * Generate weighted benign datasets for binary classification.
   *
   * @return A List of [[LabeledPoint]]
   */
  def generateWeightedBenignDatasetBinaryClassification(): List[LabeledPoint] = {
    val r = new Random(WEIGHT_RANDOM_SEED)
    drawBalancedSampleFromNumericallyBenignDenseFeaturesForBinaryClassifierLocal(
      DATA_RANDOM_SEED,
      TRAINING_SAMPLES,
      PROBLEM_DIMENSION)
      .map { obj =>
        assertEquals(obj._2.length, PROBLEM_DIMENSION, "Samples should have expected lengths")
        val weight: Double = r.nextDouble() * WEIGHT_RANDOM_MAX
        new LabeledPoint(label = obj._1, features = obj._2, weight = weight)
      }
      .toList
  }

  /**
   * Generate unweighted outlier-dense datasets for binary classification.
   *
   * @return A List of [[LabeledPoint]]
   */
  def generateOutlierDatasetBinaryClassification(): List[LabeledPoint] =
    drawBalancedSampleFromOutlierDenseFeaturesForBinaryClassifierLocal(
      DATA_RANDOM_SEED,
      TRAINING_SAMPLES,
      PROBLEM_DIMENSION)
      .map({ obj =>
        assertEquals(obj._2.length, PROBLEM_DIMENSION, "Samples should have expected lengths")
        new LabeledPoint(label = obj._1, features = obj._2)
      })
      .toList

  /**
   * Generate weighted outlier-dense datasets for binary classification.
   *
   * @return A List of [[LabeledPoint]]
   */
  def generateWeightedOutlierDatasetBinaryClassification(): List[LabeledPoint] = {
    val r = new Random(WEIGHT_RANDOM_SEED)
    drawBalancedSampleFromOutlierDenseFeaturesForBinaryClassifierLocal(
      DATA_RANDOM_SEED,
      TRAINING_SAMPLES,
      PROBLEM_DIMENSION)
      .map({ obj =>
        assertEquals(obj._2.length, PROBLEM_DIMENSION, "Samples should have expected lengths")
        val weight = r.nextDouble() * WEIGHT_RANDOM_MAX
        new LabeledPoint(label = obj._1, features = obj._2, weight = weight)
      })
      .toList
  }

  //
  // Linear regression dataset generation functions
  //

  /**
   * Generate unweighted benign datasets for linear regression.
   *
   * @return A List of [[LabeledPoint]]
   */
  def generateBenignDatasetLinearRegression(): List[LabeledPoint] =
    drawSampleFromNumericallyBenignDenseFeaturesForLinearRegressionLocal(
      DATA_RANDOM_SEED,
      TRAINING_SAMPLES,
      PROBLEM_DIMENSION)
      .map({ obj =>
        assertEquals(obj._2.length, PROBLEM_DIMENSION, "Samples should have expected lengths")
        new LabeledPoint(label = obj._1, features = obj._2)
      })
      .toList

  /**
   * Generate weighted benign datasets for linear regression.
   *
   * @return A List of [[LabeledPoint]]
   */
  def generateWeightedBenignDatasetLinearRegression(): List[LabeledPoint] = {
    val r = new Random(WEIGHT_RANDOM_SEED)
    drawSampleFromNumericallyBenignDenseFeaturesForLinearRegressionLocal(
      DATA_RANDOM_SEED,
      TRAINING_SAMPLES,
      PROBLEM_DIMENSION)
      .map({ obj =>
        assertEquals(obj._2.length, PROBLEM_DIMENSION, "Samples should have expected lengths")
        val weight = r.nextDouble() * WEIGHT_RANDOM_MAX
        new LabeledPoint(label = obj._1, features = obj._2, weight = weight)
      })
      .toList
  }

  /**
   * Generate unweighted outlier-dense datasets for linear regression.
   *
   * @return A List of [[LabeledPoint]]
   */
  def generateOutlierDatasetLinearRegression(): List[LabeledPoint] =
    drawSampleFromOutlierDenseFeaturesForLinearRegressionLocal(
      DATA_RANDOM_SEED,
      TRAINING_SAMPLES,
      PROBLEM_DIMENSION)
      .map({ obj =>
        assertEquals(obj._2.length, PROBLEM_DIMENSION, "Samples should have expected lengths")
        new LabeledPoint(label = obj._1, features = obj._2)
      })
      .toList

  /**
   * Generate weighted outlier-dense datasets for linear regression.
   *
   * @return A List of [[LabeledPoint]]
   */
  def generateWeightedOutlierDatasetLinearRegression(): List[LabeledPoint] = {
    val r = new Random(WEIGHT_RANDOM_SEED)
    drawSampleFromOutlierDenseFeaturesForLinearRegressionLocal(
      DATA_RANDOM_SEED,
      TRAINING_SAMPLES,
      PROBLEM_DIMENSION)
      .map({ obj =>
        assertEquals(obj._2.length, PROBLEM_DIMENSION, "Samples should have expected lengths")
        val weight = r.nextDouble() * WEIGHT_RANDOM_MAX
        new LabeledPoint(label = obj._1, features = obj._2, weight = weight)
      })
      .toList
  }

  //
  // Poisson regression dataset generation functions
  //

  /**
   * Generate unweighted benign datasets for Poisson regression.
   *
   * @return A List of [[LabeledPoint]]
   */
  def generateBenignDatasetPoissonRegression(): List[LabeledPoint] =
    drawSampleFromNumericallyBenignDenseFeaturesForPoissonRegressionLocal(
      DATA_RANDOM_SEED,
      TRAINING_SAMPLES,
      PROBLEM_DIMENSION)
      .map({ obj =>
        assertEquals(obj._2.length, PROBLEM_DIMENSION, "Samples should have expected lengths")
        new LabeledPoint(label = obj._1, features = obj._2)
      })
      .toList

  /**
   * Generate weighted benign datasets for Poisson regression.
   *
   * @return A List of [[LabeledPoint]]
   */
  def generateWeightedBenignDatasetPoissonRegression(): List[LabeledPoint] = {
    val r = new Random(WEIGHT_RANDOM_SEED)
    drawSampleFromNumericallyBenignDenseFeaturesForPoissonRegressionLocal(
      DATA_RANDOM_SEED,
      TRAINING_SAMPLES,
      PROBLEM_DIMENSION)
      .map({ obj =>
        assertEquals(obj._2.length, PROBLEM_DIMENSION, "Samples should have expected lengths")
        val weight = r.nextDouble() * WEIGHT_RANDOM_MAX
        new LabeledPoint(label = obj._1, features = obj._2, weight = weight)
      })
      .toList
  }

  /**
   * Generate unweighted outlier-dense datasets for Poisson regression.
   *
   * @return A List of [[LabeledPoint]]
   */
  def generateOutlierDatasetPoissonRegression(): List[LabeledPoint] =
    drawSampleFromOutlierDenseFeaturesForPoissonRegressionLocal(
      DATA_RANDOM_SEED,
      TRAINING_SAMPLES,
      PROBLEM_DIMENSION)
      .map({ obj =>
        assertEquals(obj._2.length, PROBLEM_DIMENSION, "Samples should have expected lengths")
        new LabeledPoint(label = obj._1, features = obj._2)
      })
      .toList

  /**
   * Generate weighted outlier-dense datasets for Poisson regression.
   *
   * @return A List of [[LabeledPoint]]
   */
  def generateWeightedOutlierDatasetPoissonRegression(): List[LabeledPoint] = {
    val r = new Random(WEIGHT_RANDOM_SEED)
    drawSampleFromOutlierDenseFeaturesForPoissonRegressionLocal(
      DATA_RANDOM_SEED,
      TRAINING_SAMPLES,
      PROBLEM_DIMENSION)
      .map({ obj =>
        assertEquals(obj._2.length, PROBLEM_DIMENSION, "Samples should have expected lengths")
        val weight = r.nextDouble() * WEIGHT_RANDOM_MAX
        new LabeledPoint(label = obj._1, features = obj._2, weight = weight)
      })
      .toList
  }

  /**
   * Check a computed derivative value against a numerical estimate and throw an error is either the relative or
   * absolute error is too great.
   *
   * @param prefix An informative message about the derivative being checked
   * @param descriptor A description of the context for the numerical estimation
   * @param deltaBefore The function value at delta units before the computation
   * @param deltaAfter The function value at delta units after the computation
   * @param expected The computed value of the derivative
   * @param tolerance The relative & absolute error tolerance
   */
  def checkDerivativeError(
    prefix: String,
    descriptor: String,
    deltaBefore: Double,
    deltaAfter: Double,
    expected: Double,
    tolerance: Double): Unit = {

    assertTrue(java.lang.Double.isFinite(deltaBefore), s"Value before step [$deltaBefore] should be finite")
    assertTrue(java.lang.Double.isFinite(deltaAfter), s"Value after step [$deltaAfter] should be finite")
    assertTrue(java.lang.Double.isFinite(expected), s"Expected value [$expected] should be finite")

    val numericDerivative = (deltaAfter - deltaBefore) / (2.0 * DERIVATIVE_DELTA)
    val absoluteError = Math.abs(numericDerivative - expected)
    val relativeErrorNumerator = absoluteError
    val relativeErrorFactor = Math.min(Math.abs(numericDerivative), Math.abs(expected))
    val relativeErrorDenominator = if (relativeErrorFactor > 0) {
      relativeErrorFactor
    } else {
      1
    }
    val relativeError = relativeErrorNumerator / relativeErrorDenominator

    assert(
      relativeError < tolerance || absoluteError < tolerance,
      s"""$prefix
         |Computed and numerical differentiation estimates should be close.
         |NUMERICAL ESTIMATE: $numericDerivative ($descriptor)
         |COMPUTED: $expected
         |ABSOLUTE ERROR: $absoluteError
         |RELATIVE ERROR: $relativeError""".stripMargin)
  }

  /**
   * Verify that gradient is accurate when computed locally.
   *
   * @note Rather than calling computeAt(...) to get both the objective and gradient in one shot, we use
   *       DiffFunction#value and DiffFunction#gradient instead. This is to ensure that we get some coverage of these
   *       functions which aren't used anywhere else. In the near term, we should decide if we want to keep those
   *       methods as part of the design or remove them, as they aren't used by any of the solvers
   * @param function The objective function being tested
   * @param dataGenerationFunction A builder function for the training dataset
   */
  @Test(dataProvider = "getDifferentiableFunctions", groups = Array[String]("ObjectiveFunctionTests", "testCore"))
  def checkGradientConsistentWithObjectiveLocal(
    function: SingleNodeObjectiveFunction with DiffFunction,
    dataGenerationFunction: () => List[LabeledPoint]): Unit = {

    val data = dataGenerationFunction()
    val r = new Random(PARAMETER_RANDOM_SEED)

    for (iter <- 0 until LOCAL_CONSISTENCY_CHECK_SAMPLES) {
      val initParam: Vector[Double] = DenseVector.fill[Double](PROBLEM_DIMENSION) {
        if (iter > 0) {
          r.nextDouble()
        } else {
          0
        }
      }
      val computed = function.gradient(data, initParam, NORMALIZATION_MOCK)

      // Element-wise numerical differentiation to get the gradient
      for (idx <- 0 until PROBLEM_DIMENSION) {
        val before = initParam.copy
        before(idx) -= DERIVATIVE_DELTA
        val after = initParam.copy
        after(idx) += DERIVATIVE_DELTA

        val objBefore = function.value(data, before, NORMALIZATION_MOCK)
        val objAfter = function.value(data, after, NORMALIZATION_MOCK)

        checkDerivativeError(
          "Checking Gradient",
          s"f=[$function / ${function.getClass.getName}], iter=[$iter], idx=[$idx]",
          objBefore,
          objAfter,
          computed(idx),
          GRADIENT_TOLERANCE)
      }
    }
  }

  /**
   * Verify that the Hessian is accurate when computed locally.
   *
   * @param function The objective function being tested
   * @param dataGenerationFunction A builder function for the training dataset
   */
  @Test(
    dataProvider = "getTwiceDifferentiableFunctions",
    dependsOnMethods = Array("checkGradientConsistentWithObjectiveLocal"),
    groups = Array[String]("ObjectiveFunctionTests", "testCore"))
  def checkHessianConsistentWithObjectiveLocal(
    function: SingleNodeGLMLossFunction with TwiceDiffFunction,
    dataGenerationFunction: () => List[LabeledPoint]): Unit = {

    val data = dataGenerationFunction()
    val r = new Random(PARAMETER_RANDOM_SEED)

    for (iter <- 0 until LOCAL_CONSISTENCY_CHECK_SAMPLES) {
      val initParam: Vector[Double] = DenseVector.fill[Double](PROBLEM_DIMENSION) {
        if (iter > 0) {
          r.nextDouble()
        } else {
          0
        }
      }

      // Loop over basis vectors. This will give us H*e_i = H(:,i) (so one column of H at a time)
      for (basis <- 0 until PROBLEM_DIMENSION) {
        val basisVector: Vector[Double] = new SparseVector[Double](
          Array[Int](basis),
          Array[Double](1.0),
          1,
          PROBLEM_DIMENSION)
        val hessianVector = function.hessianVector(data, initParam, basisVector, NORMALIZATION_MOCK)

        // Element-wise numerical differentiation to get the Hessian
        for (idx <- 0 until PROBLEM_DIMENSION) {
          val before = initParam.copy
          before(idx) -= DERIVATIVE_DELTA
          val after = initParam.copy
          after(idx) += DERIVATIVE_DELTA

          // TODO: This calculation causes memory usage to blow up (5g+) for larger settings of
          // LOCAL_CONSISTENCY_CHECK_SAMPLES (100+). Investigate.
          val gradBefore = function.gradient(data, before, NORMALIZATION_MOCK)
          val gradAfter = function.gradient(data, after, NORMALIZATION_MOCK)

          checkDerivativeError(
            "Checking Hessian",
            s"f=[$function / ${function.getClass.getName}], iter=[$iter], basis=[$basis], idx=[$idx], Hessian=[$hessianVector]",
            gradBefore(basis),
            gradAfter(basis),
            hessianVector(idx),
            HESSIAN_TOLERANCE)
        }
      }
    }
  }
}

object SingleNodeObjectiveFunctionTest {
  private val LOCAL_CONSISTENCY_CHECK_SAMPLES = 25
  private val PROBLEM_DIMENSION = 5
  private val NORMALIZATION = NoNormalization()
  private val NORMALIZATION_MOCK = mock(classOf[BroadcastWrapper[NormalizationContext]])
  private val L2_REG_CONFIGURATION_MOCK = mock(classOf[GLMOptimizationConfiguration])
  private val NO_REG_CONFIGURATION_MOCK = mock(classOf[GLMOptimizationConfiguration])
  private val REGULARIZATION_WEIGHT = 100.0
  private val DERIVATIVE_DELTA = 1e-6
  private val GRADIENT_TOLERANCE = 1e-3
  private val HESSIAN_TOLERANCE = 1e-3
  private val DATA_RANDOM_SEED = 0
  private val PARAMETER_RANDOM_SEED = 500
  private val WEIGHT_RANDOM_SEED = 100
  private val WEIGHT_RANDOM_MAX = 10
  private val TRAINING_SAMPLES = PROBLEM_DIMENSION * PROBLEM_DIMENSION

  doReturn(NORMALIZATION).when(NORMALIZATION_MOCK).value
  doReturn(L2RegularizationContext).when(L2_REG_CONFIGURATION_MOCK).regularizationContext
  doReturn(REGULARIZATION_WEIGHT).when(L2_REG_CONFIGURATION_MOCK).regularizationWeight
  doReturn(NoRegularizationContext).when(NO_REG_CONFIGURATION_MOCK).regularizationContext
}
