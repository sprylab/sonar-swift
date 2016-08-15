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
package org.sonar.plugins.swift.issues.swiftlint;

import org.apache.tools.ant.DirectoryScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.config.Settings;
import org.sonar.plugins.swift.SwiftPlugin;

import java.io.File;

public class SwiftLintSensor implements Sensor {

    public static final String REPORT_PATH_KEY = SwiftPlugin.PROPERTY_PREFIX + ".swiftlint.report";
    public static final String DEFAULT_REPORT_PATH = "sonar-reports/*swiftlint.txt";

    private static final Logger LOGGER = LoggerFactory.getLogger(SwiftLintSensor.class);

    private final Settings conf;
    private final FileSystem fileSystem;

    public SwiftLintSensor(final FileSystem fileSystem, final Settings config) {
        this.conf = config;
        this.fileSystem = fileSystem;
    }

    @Override
    public void describe(final SensorDescriptor descriptor) {
        descriptor.name(SwiftLintSensor.class.getSimpleName());
    }

    @Override
    public void execute(final SensorContext context) {
        final String projectBaseDir = fileSystem.baseDir().getAbsolutePath();

        SwiftLintReportParser parser = new SwiftLintReportParser(context);
        parseReportIn(projectBaseDir, parser);
    }

    private void parseReportIn(final String baseDir, final SwiftLintReportParser parser) {
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setIncludes(new String[]{reportPath()});
        scanner.setBasedir(baseDir);
        scanner.setCaseSensitive(false);
        scanner.scan();
        String[] files = scanner.getIncludedFiles();

        for (String filename : files) {
            LOGGER.info("Processing SwiftLint report {}", filename);
            parser.parseReport(new File(filename));
        }
    }

    private String reportPath() {
        String reportPath = conf.getString(REPORT_PATH_KEY);
        if (reportPath == null) {
            reportPath = DEFAULT_REPORT_PATH;
        }

        return reportPath;
    }
}
