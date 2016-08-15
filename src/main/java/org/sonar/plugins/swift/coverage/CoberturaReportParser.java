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
package org.sonar.plugins.swift.coverage;


import com.google.common.collect.Maps;

import org.apache.commons.lang.StringUtils;
import org.codehaus.staxmate.in.SMHierarchicCursor;
import org.codehaus.staxmate.in.SMInputCursor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.measures.CoverageMeasuresBuilder;
import org.sonar.api.measures.Metric;
import org.sonar.api.utils.KeyValueFormat;
import org.sonar.api.utils.MessageException;
import org.sonar.api.utils.ParsingUtils;
import org.sonar.api.utils.StaxParser;
import org.sonar.api.utils.XmlParserException;

import javax.xml.stream.XMLStreamException;

import java.io.File;
import java.text.ParseException;
import java.util.Locale;
import java.util.Map;

final class CoberturaReportParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoberturaReportParser.class);

    private final SensorContext context;

    private CoberturaReportParser(final SensorContext context) {
        this.context = context;
    }

    /**
     * Parse a Cobertura xml report and create measures accordingly
     */
    static void parseReport(final File xmlFile, final SensorContext context) {
        new CoberturaReportParser(context).parse(xmlFile);
    }

    private void parse(final File xmlFile) {
        try {
            final StaxParser parser = new StaxParser(new StaxParser.XmlStreamHandler() {

                @Override
                public void stream(final SMHierarchicCursor rootCursor) throws XMLStreamException {
                    rootCursor.advance();
                    collectPackageMeasures(rootCursor.descendantElementCursor("package"));
                }
            });

            parser.parse(xmlFile);
        } catch (final XMLStreamException e) {
            throw MessageException.of(e.getMessage(), e);
        }
    }

    private void collectPackageMeasures(final SMInputCursor pack) throws XMLStreamException {
        while (pack.getNext() != null) {
            final Map<String, CoverageMeasuresBuilder> builderByFilename = Maps.newHashMap();
            collectFileMeasures(pack.descendantElementCursor("class"), builderByFilename);

            for (Map.Entry<String, CoverageMeasuresBuilder> entry : builderByFilename.entrySet()) {
                final InputFile defaultInputFile = context.fileSystem().inputFile(context.fileSystem().predicates().hasPath
                        (entry.getKey()));

                if (defaultInputFile != null && defaultInputFile.file().exists()) {
                    final CoverageMeasuresBuilder measuresBuilder = entry.getValue();

                    if (measuresBuilder.getLinesToCover() > 0) {
                        // Lines to Cover
                        context.newMeasure()
                                .forMetric(new Metric.Builder(CoreMetrics.LINES_TO_COVER_KEY,
                                        "Lines to Cover",
                                        Metric.ValueType.INT)
                                        .setDescription("Lines to cover")
                                        .setDirection(Metric.DIRECTION_BETTER)
                                        .setQualitative(false)
                                        .setDomain(CoreMetrics.DOMAIN_COVERAGE)
                                        .create())
                                .on(defaultInputFile)
                                .withValue(measuresBuilder.getLinesToCover())
                                .save();

                        // Uncovered Lines
                        context.newMeasure()
                                .forMetric(new Metric.Builder(CoreMetrics.UNCOVERED_LINES_KEY,
                                        "Uncovered Lines",
                                        Metric.ValueType.INT)
                                        .setDescription("Uncovered lines")
                                        .setDirection(Metric.DIRECTION_WORST)
                                        .setDomain(CoreMetrics.DOMAIN_COVERAGE)
                                        .setBestValue(0.0)
                                        .create())
                                .on(defaultInputFile)
                                .withValue(measuresBuilder.getLinesToCover() - measuresBuilder.getCoveredLines())
                                .save();

                        // Coverage Hits by Line
                        context.newMeasure()
                                .forMetric(new Metric.Builder(CoreMetrics.COVERAGE_LINE_HITS_DATA_KEY,
                                        "Coverage Hits by Line",
                                        Metric.ValueType.DATA)
                                        .setDescription("Coverage hits by line")
                                        .setDomain(CoreMetrics.DOMAIN_COVERAGE)
                                        .setDeleteHistoricalData(true)
                                        .create())
                                .on(defaultInputFile)
                                .withValue(KeyValueFormat.format(measuresBuilder.getHitsByLine()))
                                .save();
                    }

                    if (measuresBuilder.getConditions() > 0) {
                        // Branches to Cover
                        context.newMeasure()
                                .forMetric(new Metric.Builder(CoreMetrics.CONDITIONS_TO_COVER_KEY,
                                        "Branches to Cover",
                                        Metric.ValueType.INT)
                                        .setDescription("Branches to cover")
                                        .setDomain(CoreMetrics.DOMAIN_COVERAGE)
                                        .setHidden(true)
                                        .create())
                                .on(defaultInputFile)
                                .withValue(KeyValueFormat.format(measuresBuilder.getHitsByLine()))
                                .save();

                        // Uncovered Conditions
                        context.newMeasure()
                                .forMetric(new Metric.Builder(CoreMetrics.UNCOVERED_CONDITIONS_KEY,
                                        "Uncovered Conditions",
                                        Metric.ValueType.INT)
                                        .setDescription("Uncovered conditions")
                                        .setDirection(Metric.DIRECTION_WORST)
                                        .setDomain(CoreMetrics.DOMAIN_COVERAGE)
                                        .setBestValue(0.0)
                                        .create())
                                .on(defaultInputFile)
                                .withValue(measuresBuilder.getConditions())
                                .save();

                        // Conditions by Line
                        context.newMeasure()
                                .forMetric(new Metric.Builder(CoreMetrics.CONDITIONS_BY_LINE_KEY,
                                        "Conditions by Line",
                                        Metric.ValueType.DATA)
                                        .setDescription("Conditions by line")
                                        .setDomain(CoreMetrics.DOMAIN_COVERAGE)
                                        .setDeleteHistoricalData(true)
                                        .create())
                                .on(defaultInputFile)
                                .withValue(KeyValueFormat.format(measuresBuilder.getConditionsByLine()))
                                .save();

                        // Covered Conditions by Line
                        context.newMeasure()
                                .forMetric(new Metric.Builder(CoreMetrics.COVERED_CONDITIONS_BY_LINE_KEY,
                                        "Covered Conditions by Line",
                                        Metric.ValueType.DATA)
                                        .setDescription("Covered conditions by line")
                                        .setDomain(CoreMetrics.DOMAIN_COVERAGE)
                                        .setDeleteHistoricalData(true)
                                        .create())
                                .on(defaultInputFile)
                                .withValue(KeyValueFormat.format(measuresBuilder.getCoveredConditionsByLine()))
                                .save();
                    }
                } else {
                    LOGGER.info("No coverage found for '{}'", entry.getKey());
                }
            }
        }
    }

    private static void collectFileMeasures(final SMInputCursor clazz, final Map<String, CoverageMeasuresBuilder> builderByFilename)
            throws XMLStreamException {
        while (clazz.getNext() != null) {
            final String fileName = clazz.getAttrValue("filename");
            CoverageMeasuresBuilder builder = builderByFilename.get(fileName);

            if (builder == null) {
                builder = CoverageMeasuresBuilder.create();
                builderByFilename.put(fileName, builder);
            }

            collectFileData(clazz, builder);
        }
    }

    private static void collectFileData(final SMInputCursor clazz, final CoverageMeasuresBuilder builder) throws
            XMLStreamException {
        final SMInputCursor line = clazz.childElementCursor("lines").advance().childElementCursor("line");
        while (line.getNext() != null) {
            final int lineId = Integer.parseInt(line.getAttrValue("number"));
            try {
                builder.setHits(lineId, (int) ParsingUtils.parseNumber(line.getAttrValue("hits"), Locale.ENGLISH));
            } catch (ParseException e) {
                throw new XmlParserException(e);
            }

            final String isBranch = line.getAttrValue("branch");
            final String text = line.getAttrValue("condition-coverage");
            if (StringUtils.equals(isBranch, "true") && StringUtils.isNotBlank(text)) {
                final String[] conditions = StringUtils.split(StringUtils.substringBetween(text, "(", ")"), "/");
                builder.setConditions(lineId, Integer.parseInt(conditions[1]), Integer.parseInt(conditions[0]));
            }
        }
    }
}
