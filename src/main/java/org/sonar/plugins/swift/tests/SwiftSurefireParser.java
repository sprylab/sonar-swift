/*
 * SonarQube Swift Plugin
 * Copyright (C) 2015 Backelite
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.plugins.swift.tests;

import com.google.common.collect.ImmutableList;

import org.apache.commons.lang.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.measures.Metric;
import org.sonar.api.utils.MessageException;
import org.sonar.api.utils.StaxParser;
import org.sonar.plugins.surefire.TestCaseDetails;
import org.sonar.plugins.surefire.TestSuiteParser;
import org.sonar.plugins.surefire.TestSuiteReport;

import javax.xml.transform.TransformerException;

import java.io.File;
import java.io.FilenameFilter;
import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class SwiftSurefireParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(SwiftSurefireParser.class);

    private final SensorContext context;

    SwiftSurefireParser(final SensorContext context) {
        this.context = context;
    }

    void collect(final File reportsDir) {
        final File[] xmlFiles = getReports(reportsDir);

        if (xmlFiles.length > 0) {
            parseFiles(xmlFiles);
        }
    }

    private File[] getReports(final File dir) {
        if (dir == null || !dir.isDirectory() || !dir.exists()) {
            return new File[0];
        }

        return dir.listFiles(new FilenameFilter() {
            public boolean accept(final File dir, final String name) {
                // .junit is for Fastlane support
                return (name.startsWith("TEST") && name.endsWith(".xml")) || (name.endsWith(".junit"));
            }
        });
    }

    private void parseFiles(final File[] reports) {
        final Set<TestSuiteReport> analyzedReports = new HashSet<>();

        try {
            for (final File report : reports) {
                final TestSuiteParser parserHandler = new TestSuiteParser();
                final StaxParser parser = new StaxParser(parserHandler, false);
                parser.parse(report);

                for (final TestSuiteReport fileReport : parserHandler.getParsedReports()) {
                    if (!fileReport.isValid() || analyzedReports.contains(fileReport)) {
                        continue;
                    }

                    if (fileReport.getTests() > 0) {
                        final int testsCount = fileReport.getTests() - fileReport.getSkipped();

                        // Skipped Unit Tests
                        saveClassMeasure(fileReport, new Metric.Builder(CoreMetrics.SKIPPED_TESTS_KEY, "Skipped Unit Tests",
                                Metric.ValueType.INT)
                                .setDescription("Number of skipped unit tests")
                                .setDirection(Metric.DIRECTION_WORST)
                                .setQualitative(true)
                                .setDomain(CoreMetrics.DOMAIN_COVERAGE)
                                .setBestValue(0.0)
                                .setOptimizedBestValue(true)
                                .create(), fileReport.getSkipped());

                        // Unit Tests
                        saveClassMeasure(fileReport, new Metric.Builder(CoreMetrics.TESTS_KEY, "Unit Tests", Metric.ValueType.INT)
                                .setDescription("Number of unit tests")
                                .setDirection(Metric.DIRECTION_WORST)
                                .setQualitative(false)
                                .setDomain(CoreMetrics.DOMAIN_COVERAGE)
                                .create(), testsCount);

                        // Unit Test Errors
                        saveClassMeasure(fileReport, new Metric.Builder(CoreMetrics.TEST_ERRORS_KEY, "Unit Test Errors", Metric
                                .ValueType.INT)
                                .setDescription("Number of unit test errors")
                                .setDirection(Metric.DIRECTION_WORST)
                                .setQualitative(true)
                                .setDomain(CoreMetrics.DOMAIN_COVERAGE)
                                .setBestValue(0.0)
                                .setOptimizedBestValue(true)
                                .create(), fileReport.getErrors());

                        // Unit Test Failures
                        saveClassMeasure(fileReport, new Metric.Builder(CoreMetrics.TEST_FAILURES_KEY, "Unit Test Failures",
                                Metric.ValueType.INT)
                                .setDescription("Number of unit test failures")
                                .setDirection(Metric.DIRECTION_WORST)
                                .setQualitative(true)
                                .setDomain(CoreMetrics.DOMAIN_COVERAGE)
                                .setBestValue(0.0)
                                .setOptimizedBestValue(true)
                                .create(), fileReport.getFailures());

                        // Unit Test Duration
                        saveClassMeasure(fileReport, new Metric.Builder(CoreMetrics.TEST_EXECUTION_TIME_KEY, "Unit Test " +
                                "Duration", Metric.ValueType.MILLISEC)
                                .setDescription("Execution duration of unit tests")
                                .setDirection(Metric.DIRECTION_WORST)
                                .setQualitative(false)
                                .setDomain(CoreMetrics.DOMAIN_COVERAGE)
                                .create(), (long) fileReport.getTimeMS());

                        final int passedTests = testsCount - fileReport.getErrors() - fileReport.getFailures();
                        if (testsCount > 0) {
                            double percentage = passedTests * 100D / testsCount;

                            // Test Success (%)
                            final Metric<Serializable> metric = new Metric.Builder(CoreMetrics.TEST_SUCCESS_DENSITY_KEY, "Unit " +
                                    "Test Success (%)",
                                    Metric.ValueType.PERCENT)
                                    .setDescription("Density of successful unit tests")
                                    .setDirection(Metric.DIRECTION_BETTER)
                                    .setQualitative(true)
                                    .setDomain(CoreMetrics.DOMAIN_COVERAGE)
                                    .setWorstValue(0.0)
                                    .setBestValue(100.0)
                                    .setOptimizedBestValue(true)
                                    .create();

                            saveClassMeasure(fileReport, metric, percentage);
                        }

                        saveTestsDetails(fileReport);
                        analyzedReports.add(fileReport);
                    }
                }
            }
        } catch (final Exception e) {
            throw MessageException.of("Cannot parse surefire reports", e);
        }
    }

    private void saveTestsDetails(final TestSuiteReport fileReport) throws TransformerException {
        final StringBuilder testCaseDetails = new StringBuilder(256);
        final List<TestCaseDetails> details = fileReport.getDetails();

        testCaseDetails.append("<tests-details>");
        for (final TestCaseDetails detail : details) {
            testCaseDetails.append("<testcase status=\"").append(detail.getStatus())
                    .append("\" time=\"").append(detail.getTimeMS())
                    .append("\" name=\"").append(detail.getName()).append("\"");
            boolean isError = detail.getStatus().equals(TestCaseDetails.STATUS_ERROR);
            if (isError || detail.getStatus().equals(TestCaseDetails.STATUS_FAILURE)) {
                testCaseDetails.append(">")
                        .append(isError ? "<error message=\"" : "<failure message=\"")
                        .append(StringEscapeUtils.escapeXml(detail.getErrorMessage())).append("\">")
                        .append("<![CDATA[").append(StringEscapeUtils.escapeXml(detail.getStackTrace())).append("]]>")
                        .append(isError ? "</error>" : "</failure>").append("</testcase>");
            } else {
                testCaseDetails.append("/>");
            }
        }
        testCaseDetails.append("</tests-details>");

        final InputFile inputFile = getUnitTestResource(fileReport.getClassKey());
        if (inputFile != null && inputFile.file().exists()) {
            final Metric<Serializable> metric = new Metric.Builder(CoreMetrics.TEST_DATA_KEY, "Unit Test Details", Metric.ValueType
                    .DATA).setDescription("Unit tests details")
                    .setDirection(Metric.DIRECTION_WORST)
                    .setDomain(CoreMetrics.DOMAIN_COVERAGE)
                    .create();

            context.newMeasure()
                    .forMetric(metric)
                    .on(inputFile)
                    .withValue(testCaseDetails.toString())
                    .save();
        }
    }

    private void saveClassMeasure(final TestSuiteReport fileReport, final Metric<Serializable> metric, final Serializable value) {
        final InputFile inputFile = getUnitTestResource(fileReport.getClassKey());

        if (inputFile != null && inputFile.file().exists()) {
            context.newMeasure()
                    .forMetric(metric)
                    .on(inputFile)
                    .withValue(value)
                    .save();
        }
    }

    private InputFile getUnitTestResource(final String classname) {
        final String fileName = classname.replace('.', '/') + ".swift";

        File file = new File(fileName);
        if (!file.isAbsolute()) {
            file = new File(context.fileSystem().baseDir(), fileName);
        }

        /*
         * Most xcodebuild JUnit parsers don't include the path to the class in the class field, so search for it if it
         * wasn't found in the root.
         */
        if (!file.isFile() || !file.exists()) {
            final List<File> files = ImmutableList.copyOf(context.fileSystem().files(context.fileSystem().predicates().and(
                    context.fileSystem().predicates().hasType(InputFile.Type.TEST),
                    context.fileSystem().predicates().matchesPathPattern("**/" + fileName))));

            if (files.isEmpty()) {
                LOGGER.debug("Unable to locate test source file {}", fileName);
            } else {
                /*
                 * Lazily get the first file, since we wouldn't be able to determine the correct one from just the
                 * test class name in the event that there are multiple matches.
                 */
                file = files.get(0);
            }
        }

        return context.fileSystem().inputFile(context.fileSystem().predicates().hasPath(file.getPath()));
    }
}
