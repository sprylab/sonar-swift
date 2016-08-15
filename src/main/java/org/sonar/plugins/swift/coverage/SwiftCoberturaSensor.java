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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.config.Settings;
import org.sonar.plugins.swift.SwiftPlugin;

import java.io.File;

public final class SwiftCoberturaSensor implements Sensor {

    private static final Logger LOGGER = LoggerFactory.getLogger(SwiftCoberturaSensor.class);

    public static final String REPORT_PATTERN_KEY = SwiftPlugin.PROPERTY_PREFIX + ".coverage.reportPattern";

    public static final String DEFAULT_REPORT_PATTERN = "sonar-reports/coverage*.xml";

    private final ReportFilesFinder reportFilesFinder;

    public SwiftCoberturaSensor(final Settings settings) {
        reportFilesFinder = new ReportFilesFinder(settings, REPORT_PATTERN_KEY, DEFAULT_REPORT_PATTERN);
    }

    @Override
    public void describe(final SensorDescriptor descriptor) {
        descriptor.name(SwiftCoberturaSensor.class.getSimpleName());
    }

    @Override
    public void execute(final SensorContext context) {
        final String projectBaseDir = context.fileSystem().baseDir().getPath();

        for (final File report : reportFilesFinder.reportsIn(projectBaseDir)) {
            LOGGER.info("Processing coverage report {}", report);
            CoberturaReportParser.parseReport(report, context);
        }
    }
}
