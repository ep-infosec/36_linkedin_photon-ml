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
package com.linkedin.photon.ml

import java.io.File

import scala.collection.mutable
import scala.io.Source

import breeze.linalg.{DenseVector, Vector, norm}
import org.apache.commons.io.FileUtils
import org.apache.hadoop.fs.Path
import org.testng.Assert._
import org.testng.annotations.{DataProvider, Test}

import com.linkedin.photon.ml.PhotonOptionNames._
import com.linkedin.photon.ml.TaskType.TaskType
import com.linkedin.photon.ml.io.deprecated.{FieldNamesType, InputFormatType}
import com.linkedin.photon.ml.model.Coefficients
import com.linkedin.photon.ml.normalization.NormalizationType
import com.linkedin.photon.ml.optimization.OptimizerType.OptimizerType
import com.linkedin.photon.ml.optimization.RegularizationType.RegularizationType
import com.linkedin.photon.ml.optimization.{OptimizerType, RegularizationType}
import com.linkedin.photon.ml.supervised.classification.LogisticRegressionModel
import com.linkedin.photon.ml.supervised.model.GeneralizedLinearModel
import com.linkedin.photon.ml.test.{CommonTestUtils, SparkTestUtils, TestTemplateWithTmpDir}
import com.linkedin.photon.ml.util.{VectorUtils, Utils}

/**
 * This class tests the Photon (not GAME) Driver with a set of important configuration parameters
 */
// TODO: Remove along with legacy Driver
class DriverTest extends SparkTestUtils with TestTemplateWithTmpDir {

  import DriverTest._

  @Test
  def testRunWithMinimalArguments(): Unit = sparkTest("testRunWithMinimalArguments") {

    val outputDir = getTmpDir + "/testRunWithMinimalArguments"
    val args = mutable.ArrayBuffer[String]()

    appendCommonJobArgs(args, outputDir)
    args += CommonTestUtils.fromOptionNameToArg(OPTIMIZER_TYPE_OPTION)
    args += "TRON"
    args += CommonTestUtils.fromOptionNameToArg(MAX_NUM_ITERATIONS_OPTION)
    args += LIGHT_MAX_NUM_ITERATIONS.toString

    MockDriver.runLocally(
      args.toArray,
      sc,
      expectedStages = Array(DriverStage.INIT, DriverStage.PREPROCESSED, DriverStage.TRAINED),
      expectedNumFeatures = HEART_EXPECTED_NUM_FEATURES,
      expectedNumTrainingData = HEART_EXPECTED_NUM_TRAINING_DATA,
      expectedIsSummarized = false)

    val models = loadAllModels(new Path(outputDir, Driver.LEARNED_MODELS_TEXT).toString)
    assertEquals(models.length, defaultParams.regularizationWeights.length)
    // Verify lambdas
    assertEquals(models.map(_._1), defaultParams.regularizationWeights.toArray)

    // No best model output dir
    assertFalse(new File(new Path(outputDir, Driver.BEST_MODEL_TEXT).toString).exists())
  }

  @Test
  def testLibSVMRun(): Unit = sparkTest("testLibSVMRun") {

    val outputDir = getTmpDir + "/testLibSVMRun"
    val args = mutable.ArrayBuffer[String]()

    appendCommonJobArgs(args, outputDir, fileSuffix = ".txt")
    args += CommonTestUtils.fromOptionNameToArg(OPTIMIZER_TYPE_OPTION)
    args += "LBFGS"
    args += CommonTestUtils.fromOptionNameToArg(MAX_NUM_ITERATIONS_OPTION)
    args += LIGHT_MAX_NUM_ITERATIONS.toString
    args += CommonTestUtils.fromOptionNameToArg(FEATURE_DIMENSION)
    args += "13"
    args += CommonTestUtils.fromOptionNameToArg(INPUT_FILE_FORMAT)
    args += InputFormatType.LIBSVM.toString

    MockDriver.runLocally(
      args.toArray,
      sc,
      expectedStages = Array(DriverStage.INIT, DriverStage.PREPROCESSED, DriverStage.TRAINED),
      expectedNumFeatures = HEART_EXPECTED_NUM_FEATURES,
      expectedNumTrainingData = HEART_EXPECTED_NUM_TRAINING_DATA,
      expectedIsSummarized = false)

    val models = loadAllModels(new Path(outputDir, Driver.LEARNED_MODELS_TEXT).toString)
    assertEquals(models.length, defaultParams.regularizationWeights.length)
    // Verify lambdas
    assertEquals(models.map(_._1), defaultParams.regularizationWeights.toArray)

    // No best model output dir
    assertFalse(new File(new Path(outputDir, Driver.BEST_MODEL_TEXT).toString).exists())
  }

  @Test
  def testLibSVMRunWithValidation(): Unit = sparkTest("testLibSVMRunWithValidation") {

    val outputDir = getTmpDir + "/testLibSVMRunWithValidation"
    val args = mutable.ArrayBuffer[String]()

    appendCommonJobArgs(args, outputDir, isValidating = true, fileSuffix = ".txt")
    args += CommonTestUtils.fromOptionNameToArg(SUMMARIZATION_OUTPUT_DIR)
    args += outputDir + "/summary"
    args += CommonTestUtils.fromOptionNameToArg(NORMALIZATION_TYPE)
    args += NormalizationType.SCALE_WITH_STANDARD_DEVIATION.toString
    args += CommonTestUtils.fromOptionNameToArg(MAX_NUM_ITERATIONS_OPTION)
    args += LIGHT_MAX_NUM_ITERATIONS.toString
    args += CommonTestUtils.fromOptionNameToArg(FEATURE_DIMENSION)
    args += "13"
    args += CommonTestUtils.fromOptionNameToArg(INPUT_FILE_FORMAT)
    args += InputFormatType.LIBSVM.toString

    MockDriver.runLocally(
      args.toArray,
      sc,
      expectedStages = Array(
        DriverStage.INIT,
        DriverStage.PREPROCESSED,
        DriverStage.TRAINED,
        DriverStage.VALIDATED),
      expectedNumFeatures = HEART_EXPECTED_NUM_FEATURES,
      expectedNumTrainingData = HEART_EXPECTED_NUM_TRAINING_DATA,
      expectedIsSummarized = true)

    val models = loadAllModels(new Path(outputDir, Driver.LEARNED_MODELS_TEXT).toString)
    assertEquals(models.length, defaultParams.regularizationWeights.length)
    // Verify lambdas
    assertEquals(models.map(_._1), defaultParams.regularizationWeights.toArray)

    // The selected best model is supposed to be of lambda 10.0 with features scaling with standard deviation
    val bestModel = loadAllModels(new Path(outputDir, Driver.BEST_MODEL_TEXT).toString)
    assertEquals(bestModel.length, 1)
    // Verify lambda
    assertEquals(bestModel(0)._1, 10, CommonTestUtils.HIGH_PRECISION_TOLERANCE)
  }

  @Test(expectedExceptions = Array(classOf[IllegalArgumentException]))
  def failedTestRunWithOutputDirExists(): Unit = sparkTest("failedTestRunWithOutputDirExists") {

    val outputDir = getTmpDir + "/failedTestRunWithOutputDirExists"
    val args = mutable.ArrayBuffer[String]()

    appendCommonJobArgs(args, outputDir)

    Utils.createHDFSDir(new Path(outputDir), sc.hadoopConfiguration)

    MockDriver.runLocally(
      args.toArray,
      sc,
      expectedStages = Array(DriverStage.INIT, DriverStage.PREPROCESSED, DriverStage.TRAINED),
      expectedNumFeatures = HEART_EXPECTED_NUM_FEATURES,
      expectedNumTrainingData = HEART_EXPECTED_NUM_TRAINING_DATA,
      expectedIsSummarized = false)
  }

  @Test
  def successfulTestRunWithOutputDirExists(): Unit = sparkTest("successfulTestRunWithOutputDirExists") {

    val outputDir = getTmpDir + "/successfulTestRunWithOutputDirExists"
    val args = mutable.ArrayBuffer[String]()

    appendCommonJobArgs(args, outputDir)
    args += CommonTestUtils.fromOptionNameToArg(DELETE_OUTPUT_DIRS_IF_EXIST)
    args += "tRUe"
    args += CommonTestUtils.fromOptionNameToArg(MAX_NUM_ITERATIONS_OPTION)
    args += LIGHT_MAX_NUM_ITERATIONS.toString

    Utils.createHDFSDir(new Path(outputDir), sc.hadoopConfiguration)

    MockDriver.runLocally(
      args.toArray,
      sc,
      expectedStages = Array(DriverStage.INIT, DriverStage.PREPROCESSED, DriverStage.TRAINED),
      expectedNumFeatures = HEART_EXPECTED_NUM_FEATURES,
      expectedNumTrainingData = HEART_EXPECTED_NUM_TRAINING_DATA,
      expectedIsSummarized = false)
  }

  @Test
  def testRunTrainingSetWithEmptyFeatures(): Unit = sparkTest("testRunTrainingSetWithEmptyFeatures") {

    val outputDir = getTmpDir + "/testRunEmptyTrainingSet"
    val args = mutable.ArrayBuffer[String]()

    appendCommonJobArgs(args, outputDir)
    // Training data with empty feature vectors
    args(1) = TEST_DIR + "/empty.avro"
    args += CommonTestUtils.fromOptionNameToArg(OPTIMIZER_TYPE_OPTION)
    args += "TRON"
    args += CommonTestUtils.fromOptionNameToArg(MAX_NUM_ITERATIONS_OPTION)
    args += LIGHT_MAX_NUM_ITERATIONS.toString

    MockDriver.runLocally(
      args.toArray,
      sc,
      expectedStages = Array(DriverStage.INIT, DriverStage.PREPROCESSED, DriverStage.TRAINED),
      // Number of features is not 0; 1 feature should exist for the intercept
      expectedNumFeatures = 1,
      expectedNumTrainingData = 250,
      expectedIsSummarized = false)

    val models = loadAllModels(new Path(outputDir, Driver.LEARNED_MODELS_TEXT).toString)
    assertEquals(models.length, defaultParams.regularizationWeights.length)
    // Verify lambdas
    assertEquals(models.map(_._1), defaultParams.regularizationWeights.toArray)

    // No best model output dir
    assertFalse(new File(new Path(outputDir, Driver.BEST_MODEL_TEXT).toString).exists())
  }

  @Test
  def testRunWithOffHeapMap(): Unit = sparkTest("testRunWithMinimalArguments") {

    val outputDir = getTmpDir + "/testRunWithMinimalArguments"
    val args = mutable.ArrayBuffer[String]()

    appendCommonJobArgs(args, outputDir)
    appendOffHeapConfig(args, addIntercept = false)
    args += CommonTestUtils.fromOptionNameToArg(OPTIMIZER_TYPE_OPTION)
    args += "TRON"
    args += CommonTestUtils.fromOptionNameToArg(INTERCEPT_OPTION)
    args += "false"
    args += CommonTestUtils.fromOptionNameToArg(MAX_NUM_ITERATIONS_OPTION)
    args += LIGHT_MAX_NUM_ITERATIONS.toString

    MockDriver.runLocally(
      args.toArray,
      sc,
      expectedStages = Array(DriverStage.INIT, DriverStage.PREPROCESSED, DriverStage.TRAINED),
      expectedNumFeatures = 13,
      expectedNumTrainingData = HEART_EXPECTED_NUM_TRAINING_DATA,
      expectedIsSummarized = false)

    val models = loadAllModels(new Path(outputDir, Driver.LEARNED_MODELS_TEXT).toString)
    assertEquals(models.length, defaultParams.regularizationWeights.length)
    // Verify lambdas
    assertEquals(models.map(_._1), defaultParams.regularizationWeights.toArray)

    // No best model output dir
    assertFalse(new File(new Path(outputDir, Driver.BEST_MODEL_TEXT).toString).exists())
  }

  @Test
  def testRunWithOffHeapMapWithIntercept(): Unit = sparkTest("testRunWithMinimalArguments") {

    val outputDir = getTmpDir + "/testRunWithMinimalArguments"
    val args = mutable.ArrayBuffer[String]()

    appendCommonJobArgs(args, outputDir)
    appendOffHeapConfig(args)
    args += CommonTestUtils.fromOptionNameToArg(OPTIMIZER_TYPE_OPTION)
    args += "TRON"
    args += CommonTestUtils.fromOptionNameToArg(MAX_NUM_ITERATIONS_OPTION)
    args += LIGHT_MAX_NUM_ITERATIONS.toString

    MockDriver.runLocally(
      args.toArray,
      sc,
      expectedStages = Array(DriverStage.INIT, DriverStage.PREPROCESSED, DriverStage.TRAINED),
      expectedNumFeatures = HEART_EXPECTED_NUM_FEATURES,
      expectedNumTrainingData = HEART_EXPECTED_NUM_TRAINING_DATA,
      expectedIsSummarized = false)

    val models = loadAllModels(new Path(outputDir, Driver.LEARNED_MODELS_TEXT).toString)
    assertEquals(models.length, defaultParams.regularizationWeights.length)
    // Verify lambdas
    assertEquals(models.map(_._1), defaultParams.regularizationWeights.toArray)

    // No best model output dir
    assertFalse(new File(new Path(outputDir, Driver.BEST_MODEL_TEXT).toString).exists())
  }

  @Test
  def testRunWithDataValidationPerIterationWithOffHeapMap(): Unit = sparkTest("testRunWithDataValidationPerIteration") {

    val outputDir = getTmpDir + "/testRunWithDataValidationPerIteration"
    val args = mutable.ArrayBuffer[String]()

    appendCommonJobArgs(args, outputDir, isValidating = true)
    appendOffHeapConfig(args, addIntercept = false)
    args += CommonTestUtils.fromOptionNameToArg(VALIDATE_PER_ITERATION)
    args += true.toString
    args += CommonTestUtils.fromOptionNameToArg(INTERCEPT_OPTION)
    args += "false"
    args += CommonTestUtils.fromOptionNameToArg(MAX_NUM_ITERATIONS_OPTION)
    args += LIGHT_MAX_NUM_ITERATIONS.toString

    MockDriver.runLocally(
      args.toArray,
      sc,
      expectedStages = Array(
        DriverStage.INIT,
        DriverStage.PREPROCESSED,
        DriverStage.TRAINED,
        DriverStage.VALIDATED),
      expectedNumFeatures = 13,
      expectedNumTrainingData = HEART_EXPECTED_NUM_TRAINING_DATA,
      expectedIsSummarized = false)

    val models = loadAllModels(new Path(outputDir, Driver.LEARNED_MODELS_TEXT).toString)
    assertEquals(models.length, defaultParams.regularizationWeights.length)
    // Verify lambdas
    assertEquals(models.map(_._1), defaultParams.regularizationWeights.toArray)

    // The selected best model is supposed to be of lambda 10
    val bestModel = loadAllModels(new Path(outputDir, Driver.BEST_MODEL_TEXT).toString)
    assertEquals(bestModel.length, 1)
    // Verify lambda
    assertEquals(bestModel(0)._1, 10, CommonTestUtils.HIGH_PRECISION_TOLERANCE)
  }

  @Test
  def testRunWithDataValidationPerIterationWithOffHeapMapWithIntercept(): Unit =
    sparkTest("testRunWithDataValidationPerIteration") {

      val outputDir = getTmpDir + "/testRunWithDataValidationPerIteration"
      val args = mutable.ArrayBuffer[String]()

      appendCommonJobArgs(args, outputDir, isValidating = true)
      appendOffHeapConfig(args)
      args += CommonTestUtils.fromOptionNameToArg(VALIDATE_PER_ITERATION)
      args += true.toString
      args += CommonTestUtils.fromOptionNameToArg(MAX_NUM_ITERATIONS_OPTION)
      args += LIGHT_MAX_NUM_ITERATIONS.toString

      MockDriver.runLocally(
        args.toArray,
        sc,
        expectedStages = Array(
          DriverStage.INIT,
          DriverStage.PREPROCESSED,
          DriverStage.TRAINED,
          DriverStage.VALIDATED),
        expectedNumFeatures = HEART_EXPECTED_NUM_FEATURES,
        expectedNumTrainingData = HEART_EXPECTED_NUM_TRAINING_DATA,
        expectedIsSummarized = false)

      val models = loadAllModels(new Path(outputDir, Driver.LEARNED_MODELS_TEXT).toString)
      assertEquals(models.length, defaultParams.regularizationWeights.length)
      // Verify lambdas
      assertEquals(models.map(_._1), defaultParams.regularizationWeights.toArray)

      // The selected best model is supposed to be of lambda 10
      val bestModel = loadAllModels(new Path(outputDir, Driver.BEST_MODEL_TEXT).toString)
      assertEquals(bestModel.length, 1)
      // Verify lambda
      assertEquals(bestModel(0)._1, 10, CommonTestUtils.HIGH_PRECISION_TOLERANCE)
    }

  @Test
  def testRunWithTRON(): Unit = sparkTest("testRunWithTRON") {

    val outputDir = getTmpDir + "/testRunWithTRON"
    val args = mutable.ArrayBuffer[String]()

    appendCommonJobArgs(args, outputDir)
    args += CommonTestUtils.fromOptionNameToArg(OPTIMIZER_TYPE_OPTION)
    args += OptimizerType.TRON.toString
    args += CommonTestUtils.fromOptionNameToArg(MAX_NUM_ITERATIONS_OPTION)
    args += LIGHT_MAX_NUM_ITERATIONS.toString

    MockDriver.runLocally(
      args.toArray,
      sc,
      expectedStages = Array(DriverStage.INIT, DriverStage.PREPROCESSED, DriverStage.TRAINED),
      expectedNumFeatures = HEART_EXPECTED_NUM_FEATURES,
      expectedNumTrainingData = HEART_EXPECTED_NUM_TRAINING_DATA,
      expectedIsSummarized = false)

    val models = loadAllModels(new Path(outputDir, Driver.LEARNED_MODELS_TEXT).toString)
    assertEquals(models.length, defaultParams.regularizationWeights.length)
    // Verify lambdas
    assertEquals(models.map(_._1), defaultParams.regularizationWeights.toArray)

    // No best model output dir
    assertFalse(new File(new Path(outputDir, Driver.BEST_MODEL_TEXT).toString).exists())
  }

  @Test
  def testRunWithLBFGS(): Unit = sparkTest("testRunWithLBFGS") {

    val outputDir = getTmpDir + "/testRunWithLBFGS"
    val args = mutable.ArrayBuffer[String]()

    appendCommonJobArgs(args, outputDir)
    args += CommonTestUtils.fromOptionNameToArg(OPTIMIZER_TYPE_OPTION)
    args += OptimizerType.LBFGS.toString
    args += CommonTestUtils.fromOptionNameToArg(MAX_NUM_ITERATIONS_OPTION)
    args += LIGHT_MAX_NUM_ITERATIONS.toString

    MockDriver.runLocally(
      args.toArray,
      sc,
      expectedStages = Array(DriverStage.INIT, DriverStage.PREPROCESSED, DriverStage.TRAINED),
      expectedNumFeatures = HEART_EXPECTED_NUM_FEATURES,
      expectedNumTrainingData = HEART_EXPECTED_NUM_TRAINING_DATA,
      expectedIsSummarized = false)

    val models = loadAllModels(new Path(outputDir, Driver.LEARNED_MODELS_TEXT).toString)
    assertEquals(models.length, defaultParams.regularizationWeights.length)
    // Verify lambdas
    assertEquals(models.map(_._1), defaultParams.regularizationWeights.toArray)

    // No best model output dir
    assertFalse(new File(new Path(outputDir, Driver.BEST_MODEL_TEXT).toString).exists())
  }

  /**
    * Test training with L1 regularization. This test verifies that as the regularization weight increases,
    * coefficients shrink and will be shrink to zero.
    */
  @Test
  def testRunWithL1(): Unit = sparkTest("testRunWithL1") {

    val outputDir = getTmpDir + "/testRunWithL1"
    val args = mutable.ArrayBuffer[String]()
    val lambdas = Array(1.0, 1000.0)

    appendCommonJobArgs(args, outputDir)
    args += CommonTestUtils.fromOptionNameToArg(REGULARIZATION_TYPE_OPTION)
    args += RegularizationType.L1.toString
    args += CommonTestUtils.fromOptionNameToArg(REGULARIZATION_WEIGHTS_OPTION)
    args += lambdas.mkString(",")
    args += CommonTestUtils.fromOptionNameToArg(INTERCEPT_OPTION)
    args += "false"
    args += CommonTestUtils.fromOptionNameToArg(MAX_NUM_ITERATIONS_OPTION)
    args += HEAVY_MAX_NUM_ITERATIONS_FOR_LBFGS.toString

    MockDriver.runLocally(
      args.toArray,
      sc,
      expectedStages = Array(DriverStage.INIT, DriverStage.PREPROCESSED, DriverStage.TRAINED),
      expectedNumFeatures = 13,
      expectedNumTrainingData = HEART_EXPECTED_NUM_TRAINING_DATA,
      expectedIsSummarized = false)

    val models = loadAllModels(new Path(outputDir, Driver.LEARNED_MODELS_TEXT).toString)

    assertEquals(models.length, lambdas.length)
    // Verify lambdas
    assertEquals(models.map(_._1), lambdas)

    // No best model output dir
    assertFalse(new File(new Path(outputDir, Driver.BEST_MODEL_TEXT).toString).exists())

    val epsilon = 1.0E-10
    val normsAndCounts = models.map { case (lambda, model) =>
      (lambda, norm(model.coefficients.means, 1), model.coefficients.means.toArray.count(x => Math.abs(x) < epsilon))
    }
    for (i <- 0 until lambdas.length - 1) {
      val (lambda1, norm1, zero1) = normsAndCounts(i)
      val (lambda2, norm2, zero2) = normsAndCounts(i + 1)
      assertTrue(norm1 > norm2, s"Norm assertion failed. ($lambda1, $norm1, $zero1) and ($lambda2, $norm2, $zero2)")
      assertTrue(zero1 < zero2, s"Zero count assertion failed. ($lambda1, $norm1, $zero1) " +
          s"and ($lambda2, $norm2, $zero2)")
    }
  }

  /**
    * Test training with elastic net regularization with alpha = 0.5. This test verifies that as the regularization
    * weight increases, coefficients shrink and will be shrink to zero.
    */
  @Test
  def testRunWithElasticNet(): Unit = sparkTest("testRunWithElasticNet") {

    val outputDir = getTmpDir + "/testRunWithElasticNet"
    val args = mutable.ArrayBuffer[String]()
    val lambdas = Array(10.0, 10000.0)
    val alpha = 0.5

    appendCommonJobArgs(args, outputDir)
    args += CommonTestUtils.fromOptionNameToArg(REGULARIZATION_TYPE_OPTION)
    args += RegularizationType.ELASTIC_NET.toString
    args += CommonTestUtils.fromOptionNameToArg(ELASTIC_NET_ALPHA_OPTION)
    args += alpha.toString
    args += CommonTestUtils.fromOptionNameToArg(REGULARIZATION_WEIGHTS_OPTION)
    args += lambdas.mkString(",")
    args += CommonTestUtils.fromOptionNameToArg(MAX_NUM_ITERATIONS_OPTION)
    args += HEAVY_MAX_NUM_ITERATIONS_FOR_LBFGS.toString

    MockDriver.runLocally(
      args.toArray,
      sc,
      expectedStages = Array(DriverStage.INIT, DriverStage.PREPROCESSED, DriverStage.TRAINED),
      expectedNumFeatures = HEART_EXPECTED_NUM_FEATURES,
      expectedNumTrainingData = HEART_EXPECTED_NUM_TRAINING_DATA,
      expectedIsSummarized = false)

    val models = loadAllModels(new Path(outputDir, Driver.LEARNED_MODELS_TEXT).toString)
    assertEquals(models.length, lambdas.length)
    // Verify lambdas
    assertEquals(models.map(_._1), lambdas)

    // No best model output dir
    assertFalse(new File(new Path(outputDir, Driver.BEST_MODEL_TEXT).toString).exists())

    val epsilon = 1.0E-10
    def elasticNetNorm(vec: Vector[Double], alpha: Double): Double = {
      alpha * norm(vec, 1) + (1 - alpha) * norm(vec, 2)
    }
    val normsAndCounts = models.map {
      case (lambda, model) => (
        lambda,
        alpha * elasticNetNorm(model.coefficients.means, alpha),
        model.coefficients.means.toArray.count(x => Math.abs(x) < epsilon))
    }
    for (i <- 0 until lambdas.length - 1) {
      val (lambda1, norm1, zero1) = normsAndCounts(i)
      val (lambda2, norm2, zero2) = normsAndCounts(i + 1)
      assertTrue(norm1 > norm2, s"Norm assertion failed. ($lambda1, $norm1, $zero1) and ($lambda2, $norm2, $zero2)")
      assertTrue(zero1 < zero2, s"Zero count assertion failed. ($lambda1, $norm1, $zero1) and " +
          s"($lambda2, $norm2, $zero2)")
    }
  }

  @Test
  def testRuntWithFeatureScaling(): Unit = sparkTest("testRuntWithFeatureScaling") {

    val outputDir = getTmpDir + "/testRuntWithFeatureScaling"
    val args = mutable.ArrayBuffer[String]()

    appendCommonJobArgs(args, outputDir)
    args += CommonTestUtils.fromOptionNameToArg(NORMALIZATION_TYPE)
    args += NormalizationType.SCALE_WITH_STANDARD_DEVIATION.toString
    args += CommonTestUtils.fromOptionNameToArg(MAX_NUM_ITERATIONS_OPTION)
    args += LIGHT_MAX_NUM_ITERATIONS.toString

    MockDriver.runLocally(
      args.toArray,
      sc,
      expectedStages = Array(DriverStage.INIT, DriverStage.PREPROCESSED, DriverStage.TRAINED),
      expectedNumFeatures = HEART_EXPECTED_NUM_FEATURES,
      expectedNumTrainingData = HEART_EXPECTED_NUM_TRAINING_DATA,
      expectedIsSummarized = true)

    val models = loadAllModels(new Path(outputDir, Driver.LEARNED_MODELS_TEXT).toString)
    assertEquals(models.length, defaultParams.regularizationWeights.length)
    // Verify lambdas
    assertEquals(models.map(_._1), defaultParams.regularizationWeights.toArray)

    // No best model output dir
    assertFalse(new File(new Path(outputDir, Driver.BEST_MODEL_TEXT).toString).exists())
  }

  @Test
  def testRuntWithFeatureStandardization(): Unit = sparkTest("testRuntWithFeatureScaling") {

    val outputDir = getTmpDir + "/testRuntWithFeatureNormalization"
    val args = mutable.ArrayBuffer[String]()

    appendCommonJobArgs(args, outputDir)
    args += CommonTestUtils.fromOptionNameToArg(NORMALIZATION_TYPE)
    args += NormalizationType.STANDARDIZATION.toString
    args += CommonTestUtils.fromOptionNameToArg(MAX_NUM_ITERATIONS_OPTION)
    args += LIGHT_MAX_NUM_ITERATIONS.toString

    MockDriver.runLocally(
      args.toArray,
      sc,
      expectedStages = Array(DriverStage.INIT, DriverStage.PREPROCESSED, DriverStage.TRAINED),
      expectedNumFeatures = HEART_EXPECTED_NUM_FEATURES,
      expectedNumTrainingData = HEART_EXPECTED_NUM_TRAINING_DATA,
      expectedIsSummarized = true)

    val models = loadAllModels(new Path(outputDir, Driver.LEARNED_MODELS_TEXT).toString)
    assertEquals(models.length, defaultParams.regularizationWeights.length)
    // Verify lambdas
    assertEquals(models.map(_._1), defaultParams.regularizationWeights.toArray)

    // No best model output dir
    assertFalse(new File(new Path(outputDir, Driver.BEST_MODEL_TEXT).toString).exists())
  }

  @Test
  def testRuntWithTreeAggregate(): Unit = sparkTest("testRuntWithTreeAggregate") {

    val outputDir = getTmpDir + "/testRuntWithTreeAggregate"
    val args = mutable.ArrayBuffer[String]()

    appendCommonJobArgs(args, outputDir)
    args += CommonTestUtils.fromOptionNameToArg(MAX_NUM_ITERATIONS_OPTION)
    args += LIGHT_MAX_NUM_ITERATIONS.toString
    args += CommonTestUtils.fromOptionNameToArg(TREE_AGGREGATE_DEPTH)
    args += 2.toString

    MockDriver.runLocally(
      args.toArray,
      sc,
      expectedStages = Array(DriverStage.INIT, DriverStage.PREPROCESSED, DriverStage.TRAINED),
      expectedNumFeatures = HEART_EXPECTED_NUM_FEATURES,
      expectedNumTrainingData = HEART_EXPECTED_NUM_TRAINING_DATA,
      expectedIsSummarized = false)

    val models = loadAllModels(new Path(outputDir, Driver.LEARNED_MODELS_TEXT).toString)
    assertEquals(models.length, defaultParams.regularizationWeights.length)
    // Verify lambdas
    assertEquals(models.map(_._1), defaultParams.regularizationWeights.toArray)

    // No best model output dir
    assertFalse(new File(new Path(outputDir, Driver.BEST_MODEL_TEXT).toString).exists())
  }

  @Test(expectedExceptions = Array(classOf[IllegalArgumentException]))
  def failedTestRunWithSummarizationOutputDirExists(): Unit =
    sparkTest("failedTestRunWithSummarizationOutputDirExists") {

      val outputDir = getTmpDir + "/testRunWithSummarization"
      val summarizationOutputDir = getTmpDir + "/summary"
      val args = mutable.ArrayBuffer[String]()

      appendCommonJobArgs(args, outputDir)
      args += CommonTestUtils.fromOptionNameToArg(SUMMARIZATION_OUTPUT_DIR)
      args += summarizationOutputDir

      Utils.createHDFSDir(new Path(summarizationOutputDir), sc.hadoopConfiguration)

      MockDriver.runLocally(
        args.toArray,
        sc,
        expectedStages = Array(DriverStage.INIT, DriverStage.PREPROCESSED, DriverStage.TRAINED),
        expectedNumFeatures = HEART_EXPECTED_NUM_FEATURES,
        expectedNumTrainingData = HEART_EXPECTED_NUM_TRAINING_DATA,
        expectedIsSummarized = true)
    }

  @Test
  def testRunWithSummarization(): Unit = sparkTest("testRunWithSummarization") {

    val outputDir = getTmpDir + "/testRunWithSummarization"
    val args = mutable.ArrayBuffer[String]()

    appendCommonJobArgs(args, outputDir)
    args += CommonTestUtils.fromOptionNameToArg(SUMMARIZATION_OUTPUT_DIR)
    args += outputDir + "/summary"
    args += CommonTestUtils.fromOptionNameToArg(MAX_NUM_ITERATIONS_OPTION)
    args += LIGHT_MAX_NUM_ITERATIONS.toString

    MockDriver.runLocally(
      args.toArray,
      sc,
      expectedStages = Array(DriverStage.INIT, DriverStage.PREPROCESSED, DriverStage.TRAINED),
      expectedNumFeatures = HEART_EXPECTED_NUM_FEATURES,
      expectedNumTrainingData = HEART_EXPECTED_NUM_TRAINING_DATA,
      expectedIsSummarized = true)

    val models = loadAllModels(new Path(outputDir, Driver.LEARNED_MODELS_TEXT).toString)
    assertEquals(models.length, defaultParams.regularizationWeights.length)
    // Verify lambdas
    assertEquals(models.map(_._1), defaultParams.regularizationWeights.toArray)

    // No best model output dir
    assertFalse(new File(new Path(outputDir, Driver.BEST_MODEL_TEXT).toString).exists())

    // Verify summary output
    assertTrue(new File(outputDir + "/summary/part-00000.avro").exists())
  }

  @Test
  def testRunWithDataValidation(): Unit = sparkTest("testRunWithDataValidation") {

    val outputDir = getTmpDir + "/testRunWithDataValidation"
    val args = mutable.ArrayBuffer[String]()

    appendCommonJobArgs(args, outputDir, isValidating = true)
    args += CommonTestUtils.fromOptionNameToArg(SUMMARIZATION_OUTPUT_DIR)
    args += outputDir + "/summary"
    args += CommonTestUtils.fromOptionNameToArg(NORMALIZATION_TYPE)
    args += NormalizationType.SCALE_WITH_STANDARD_DEVIATION.toString
    args += CommonTestUtils.fromOptionNameToArg(MAX_NUM_ITERATIONS_OPTION)
    args += LIGHT_MAX_NUM_ITERATIONS.toString

    MockDriver.runLocally(
      args.toArray,
      sc,
      expectedStages = Array(
        DriverStage.INIT,
        DriverStage.PREPROCESSED,
        DriverStage.TRAINED,
        DriverStage.VALIDATED),
      expectedNumFeatures = HEART_EXPECTED_NUM_FEATURES,
      expectedNumTrainingData = HEART_EXPECTED_NUM_TRAINING_DATA,
      expectedIsSummarized = true)

    val models = loadAllModels(new Path(outputDir, Driver.LEARNED_MODELS_TEXT).toString)
    assertEquals(models.length, defaultParams.regularizationWeights.length)
    // Verify lambdas
    assertEquals(models.map(_._1), defaultParams.regularizationWeights.toArray)

    // The selected best model is supposed to be of lambda 100.0 with features scaling with standard deviation
    val bestModel = loadAllModels(new Path(outputDir, Driver.BEST_MODEL_TEXT).toString)
    assertEquals(bestModel.length, 1)
    // Verify lambda
    assertEquals(bestModel(0)._1, 10, CommonTestUtils.HIGH_PRECISION_TOLERANCE)
  }

  @Test
  def testRunWithDataValidationPerIteration(): Unit = sparkTest("testRunWithDataValidationPerIteration") {

    val outputDir = getTmpDir + "/testRunWithDataValidationPerIteration"
    val args = mutable.ArrayBuffer[String]()

    appendCommonJobArgs(args, outputDir, isValidating = true)
    args += CommonTestUtils.fromOptionNameToArg(VALIDATE_PER_ITERATION)
    args += true.toString
    args += CommonTestUtils.fromOptionNameToArg(MAX_NUM_ITERATIONS_OPTION)
    args += LIGHT_MAX_NUM_ITERATIONS.toString

    MockDriver.runLocally(
      args.toArray,
      sc,
      expectedStages = Array(
        DriverStage.INIT,
        DriverStage.PREPROCESSED,
        DriverStage.TRAINED,
        DriverStage.VALIDATED),
      expectedNumFeatures = HEART_EXPECTED_NUM_FEATURES,
      expectedNumTrainingData = HEART_EXPECTED_NUM_TRAINING_DATA,
      expectedIsSummarized = false)

    val models = loadAllModels(new Path(outputDir, Driver.LEARNED_MODELS_TEXT).toString)
    assertEquals(models.length, defaultParams.regularizationWeights.length)
    // Verify lambdas
    assertEquals(models.map(_._1), defaultParams.regularizationWeights.toArray)

    // The selected best model is supposed to be of lambda 0.1
    val bestModel = loadAllModels(new Path(outputDir, Driver.BEST_MODEL_TEXT).toString)
    assertEquals(bestModel.length, 1)
    // Verify lambda
    assertEquals(bestModel(0)._1, 10, CommonTestUtils.HIGH_PRECISION_TOLERANCE)
  }

  @DataProvider
  def testInvalidRegularizationAndOptimizerDataProvider(): Array[Array[Any]] = {
    Array(
      Array(RegularizationType.L1, OptimizerType.TRON),
      Array(RegularizationType.ELASTIC_NET, OptimizerType.TRON)
    )
  }

  @Test(
    dataProvider = "testInvalidRegularizationAndOptimizerDataProvider",
    expectedExceptions = Array(classOf[IllegalArgumentException]))
  def testInvalidRegularizationAndOptimizer(regularizationType: RegularizationType, optimizer: OptimizerType): Unit =
    sparkTest("testInvalidRegularizationAndOptimizer") {

      val outputDir = getTmpDir + "/testInvalidRegularizationAndOptimizer"
      val args = mutable.ArrayBuffer[String]()

      appendCommonJobArgs(args, outputDir, isValidating = true)
      args += CommonTestUtils.fromOptionNameToArg(REGULARIZATION_TYPE_OPTION)
      args += regularizationType.toString
      args += CommonTestUtils.fromOptionNameToArg(OPTIMIZER_TYPE_OPTION)
      args += optimizer.toString
      args += CommonTestUtils.fromOptionNameToArg(MAX_NUM_ITERATIONS_OPTION)
      args += HEAVY_MAX_NUM_ITERATIONS_FOR_TRON.toString

      MockDriver.runLocally(
        args.toArray,
        sc,
        expectedStages = Array(
          DriverStage.INIT,
          DriverStage.PREPROCESSED,
          DriverStage.TRAINED,
          DriverStage.VALIDATED),
        expectedNumFeatures = HEART_EXPECTED_NUM_FEATURES,
        expectedNumTrainingData = HEART_EXPECTED_NUM_TRAINING_DATA,
        expectedIsSummarized = false)
    }

  @Test
  def testWarmStart(): Unit = sparkTest("testWarmStart") {

    import org.apache.log4j.{Level, Logger}

    Logger.getLogger("org").setLevel(Level.OFF)
    Logger.getLogger("akka").setLevel(Level.OFF)

    val models = Array("false", "true")
      .map { useWarmStart =>

        val outputDir = getTmpDir + "/testWarmStart-" + useWarmStart
        val lambdas = Array(1e-4, 1e-2, 1, 10, 50)
        val stages = Array(DriverStage.INIT, DriverStage.PREPROCESSED, DriverStage.TRAINED, DriverStage.VALIDATED)
        val args = mutable.ArrayBuffer[String]()

        args += CommonTestUtils.fromOptionNameToArg(TRAIN_DIR_OPTION)
        args += LOGISTIC_TRAIN_PATH
        args += CommonTestUtils.fromOptionNameToArg(VALIDATE_DIR_OPTION)
        args += LOGISTIC_VALIDATE_PATH
        args += CommonTestUtils.fromOptionNameToArg(OUTPUT_DIR_OPTION)
        args += outputDir
        args += CommonTestUtils.fromOptionNameToArg(TASK_TYPE_OPTION)
        args += TaskType.LOGISTIC_REGRESSION.toString
        args += CommonTestUtils.fromOptionNameToArg(FORMAT_TYPE_OPTION)
        args += FieldNamesType.TRAINING_EXAMPLE.toString
        args += CommonTestUtils.fromOptionNameToArg(OPTIMIZER_TYPE_OPTION)
        args += OptimizerType.LBFGS.toString
        args += CommonTestUtils.fromOptionNameToArg(TOLERANCE_OPTION)
        args += 1e-12.toString
        args += CommonTestUtils.fromOptionNameToArg(MAX_NUM_ITERATIONS_OPTION)
        args += "1000"
        args += CommonTestUtils.fromOptionNameToArg(REGULARIZATION_TYPE_OPTION)
        args += RegularizationType.L2.toString
        args += CommonTestUtils.fromOptionNameToArg(REGULARIZATION_WEIGHTS_OPTION)
        args += lambdas.mkString(",")
        args += CommonTestUtils.fromOptionNameToArg(USE_WARM_START)
        args += useWarmStart

        val completedJob = MockDriver.runLocally(
          args.toArray,
          sc,
          expectedStages = stages,
          expectedNumFeatures = LOGISTIC_EXPECTED_NUM_FEATURES,
          expectedNumTrainingData = LOGISTIC_EXPECTED_NUM_TRAINING_DATA,
          expectedIsSummarized = false)

        val models: Array[(Double, GeneralizedLinearModel)] = loadAllModels(
          new Path(outputDir, Driver.LEARNED_MODELS_TEXT).toString)
        assertEquals(models.length, lambdas.length)
        assertEquals(models.map(_._1), lambdas)

        val metrics = completedJob.metrics
        assertEquals(metrics.size, lambdas.length)
        lambdas.foreach(l => assertTrue(metrics.contains(l)))

        assertEquals(models.length, metrics.size)

        (models, metrics)
      }

    val (coldModels, coldMetrics) = models(0)
    val (warmModels, warmMetrics) = models(1)

    // Check that the models have similar coefficients between warm-start and no-warm-start
    // This check is within 1e-12 on all coefficients, which is stringent. It is possible to have
    // same metrics (AUC...) with different models if features are correlated in the training data.
    assertEquals(coldModels.length, warmModels.length)
    coldModels.zip(warmModels).foreach { case ((coldLambda, coldModel), (warmLambda, warmModel)) =>
      assertEquals(coldLambda, warmLambda)
      VectorUtils.areAlmostEqual(coldModel.coefficients.means, warmModel.coefficients.means)
    }

    // Check that the models have identical metrics with similar values.
    // Metrics should be equal in all cases, even if there are correlated features in the training data.
    assertEquals(coldMetrics.size, warmMetrics.size)
    coldMetrics.foreach { case (lambda, coldMetricsMap) =>
      assertTrue(warmMetrics.contains(lambda))
      warmMetrics(lambda).foreach { case (metricName, warmMetricValue) =>
        assertTrue(coldMetricsMap.contains(metricName))
        val coldMetricValue = coldMetricsMap(metricName)
        val diff = (coldMetricValue - warmMetricValue) / math.min(coldMetricValue, warmMetricValue)
        assertEquals(math.abs(diff), 0D, 1e-3)
      }
    }
  }
}

object DriverTest {

  private val defaultParams = new Params()

  private val TEST_DIR = ClassLoader.getSystemResource("DriverIntegTest").getPath + "/input"
  private val OFF_HEAP_TEST_DIR = ClassLoader.getSystemResource("PalDBIndexMapTest").getPath

  private val HEART_TRAIN_PATH = s"$TEST_DIR/heart"
  private val HEART_VALIDATE_PATH = s"$TEST_DIR/heart_validation"
  private val HEART_EXPECTED_NUM_FEATURES = 14
  private val HEART_EXPECTED_NUM_TRAINING_DATA = 250
  private val OFFHEAP_HEART_STORE_NO_INTERCEPT = OFF_HEAP_TEST_DIR + "/paldb_offheapmap_for_heart"
  private val OFFHEAP_HEART_STORE_WITH_INTERCEPT = OFF_HEAP_TEST_DIR + "/paldb_offheapmap_for_heart_with_intercept"
  private val OFFHEAP_HEART_STORE_PARTITION_NUM = "2"

  private val LINEAR_TRAIN_PATH = s"$TEST_DIR/linear_regression_train.avro"
  private val LINEAR_VALIDATE_PATH = s"$TEST_DIR/linear_regression_val.avro"
  private val LINEAR_EXPECTED_NUM_FEATURES = 7
  private val LINEAR_EXPECTED_NUM_TRAINING_DATA = 1000

  private val LOGISTIC_TRAIN_PATH = s"$TEST_DIR/logistic_regression_train.avro"
  private val LOGISTIC_VALIDATE_PATH = s"$TEST_DIR/logistic_regression_val.avro"
  private val LOGISTIC_EXPECTED_NUM_FEATURES = 124
  private val LOGISTIC_EXPECTED_NUM_TRAINING_DATA = 32561

  private val POISSON_TRAIN_PATH = s"$TEST_DIR/poisson_train.avro"
  private val POISSON_VALIDATE_PATH = s"$TEST_DIR/poisson_test.avro"
  private val POISSON_EXPECTED_NUM_FEATURES = 27
  private val POISSON_EXPECTED_NUM_TRAINING_DATA = 13547

  // Configured for TRON optimizer in tests that we care about the optimizer's performance
  private val HEAVY_MAX_NUM_ITERATIONS_FOR_TRON = 20
  // Configured for L-BFGS optimizer in tests that we care about the optimizer's performance
  private val HEAVY_MAX_NUM_ITERATIONS_FOR_LBFGS = 50
  // Configured for any optimizer in tests that we care about something other than the optimizer's performance
  private val LIGHT_MAX_NUM_ITERATIONS = 1

  /**
   */
  def appendCommonJobArgs(
      args: mutable.ArrayBuffer[String],
      outputDir: String,
      isValidating: Boolean = false,
      fileSuffix: String = ".avro"): Unit = {
    args += CommonTestUtils.fromOptionNameToArg(TRAIN_DIR_OPTION)
    args += s"$HEART_TRAIN_PATH$fileSuffix"

    if (isValidating) {
      args += CommonTestUtils.fromOptionNameToArg(VALIDATE_DIR_OPTION)
      args += s"$HEART_VALIDATE_PATH$fileSuffix"
    }

    args += CommonTestUtils.fromOptionNameToArg(OUTPUT_DIR_OPTION)
    args += outputDir

    args += CommonTestUtils.fromOptionNameToArg(TASK_TYPE_OPTION)
    args += TaskType.LOGISTIC_REGRESSION.toString

    if (fileSuffix == ".avro") {
      args += CommonTestUtils.fromOptionNameToArg(FORMAT_TYPE_OPTION)
      args += FieldNamesType.TRAINING_EXAMPLE.toString
    }
  }

  /**
   */
  def appendOffHeapConfig(args: mutable.ArrayBuffer[String], addIntercept: Boolean = true): Unit = {
    args += CommonTestUtils.fromOptionNameToArg(OFFHEAP_INDEXMAP_DIR)
    if (addIntercept) {
      args += OFFHEAP_HEART_STORE_WITH_INTERCEPT
    } else {
      args += OFFHEAP_HEART_STORE_NO_INTERCEPT
    }
    args += CommonTestUtils.fromOptionNameToArg(OFFHEAP_INDEXMAP_NUM_PARTITIONS)
    args += OFFHEAP_HEART_STORE_PARTITION_NUM
  }

  /**
   */
  // TODO: Formalize these model loaders
  // These model loading utils are temporarily put here. A thorough solution should be provided while refactoring
  // utils of GLMSuite and we should provide general and flexible ways of ser/der model objects
  def loadAllModels(modelsDir: String): Array[(Double, GeneralizedLinearModel)] = {
    val models = mutable.ArrayBuffer[(Double, GeneralizedLinearModel)]()
    val files = new File(modelsDir).listFiles()
    for (file <- files.filter { f => !f.getName.startsWith("_") && !f.getName.startsWith(".") }) {
      models += loadModelFromText(file.getPath, TaskType.LOGISTIC_REGRESSION)
    }
    models.sortWith(_._1 < _._1).toArray
  }

  /**
   */
  def loadModelFromText(modelPath: String, taskType: TaskType): (Double, GeneralizedLinearModel) = {
    val coeffs = mutable.ArrayBuffer[(Long, Double)]()
    var intercept: Option[Double] = None
    var lambda: Option[Double] = None
    for (line <- Source.fromFile(modelPath).getLines()) {
      val tokens = line.split("\t")

      // Heart scale dataset feature names are indices, and they don't have terms.
      // Thus, we are ignoring tokens(1)
      val name = tokens(0)
      if (name == Constants.INTERCEPT_NAME) {
        intercept = Some(tokens(2).toDouble)
      } else {
        coeffs += ((tokens(0).toLong, tokens(2).toDouble))
      }
      lambda = Some(tokens(3).toDouble)
    }

    intercept match {
      case Some(x) =>
        coeffs += ((coeffs.size.toLong, x))
      case _ =>
    }

    val features = new DenseVector[Double](coeffs.sortWith(_._1 < _._1).map(_._2).toArray)

    val model = taskType match {
      case TaskType.LOGISTIC_REGRESSION => new LogisticRegressionModel(Coefficients(features))
      case _ => new RuntimeException("Other task type not supported.")
    }
    (lambda.get, model.asInstanceOf[GeneralizedLinearModel])
  }
}
