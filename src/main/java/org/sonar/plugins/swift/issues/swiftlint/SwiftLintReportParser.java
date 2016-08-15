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

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.issue.internal.DefaultIssueLocation;
import org.sonar.api.rule.RuleKey;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class SwiftLintReportParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(SwiftLintReportParser.class);

    private final SensorContext context;

    SwiftLintReportParser(final SensorContext context) {
        this.context = context;
    }

    void parseReport(File reportFile) {
        FileReader fr = null;
        BufferedReader br = null;
        
        try {
            // Read and parse report
            fr = new FileReader(reportFile);
            br = new BufferedReader(fr);

            String line;
            while ((line = br.readLine()) != null) {
                recordIssue(line);
            }
        } catch (final IOException e) {
            LOGGER.error("Failed to parse SwiftLint report file", e);
        } finally {
            IOUtils.closeQuietly(br);
            IOUtils.closeQuietly(fr);
        }
    }

    private void recordIssue(final String line) {
        Pattern pattern = Pattern.compile("(.*.swift):(\\w+):?(\\w+)?: (warning|error): (.*) \\((\\w+)");

        Matcher matcher = pattern.matcher(line);
        while (matcher.find()) {
            final String filePath = matcher.group(1);
            final int lineNum = Integer.parseInt(matcher.group(2));
            final String message = matcher.group(5);
            final String ruleId = matcher.group(6);

            try {
                // get indexed file from context
                final InputFile inputFile = context.fileSystem().inputFile(context.fileSystem().predicates().hasPath(filePath));

                // save new issue
                context.newIssue()
                        .at(new DefaultIssueLocation().on(inputFile).message(message).at(inputFile.selectLine(lineNum)))
                        .forRule(RuleKey.of(SwiftLintRulesDefinition.REPOSITORY_KEY, ruleId))
                        .save();
            } catch (final Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }
}
