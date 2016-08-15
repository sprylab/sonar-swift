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
package org.sonar.plugins.swift.complexity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.measures.Measure;
import org.sonar.plugins.swift.SwiftPlugin;

import java.util.List;
import java.util.Map;

class LizardMeasurePersistor {

    private static final Logger LOGGER = LoggerFactory.getLogger(LizardMeasurePersistor.class);

    private SensorContext context;

    LizardMeasurePersistor(final SensorContext sensorContext) {
        context = sensorContext;
    }

    void saveMeasures(final Map<String, List<Measure>> measures) {
        if (measures == null) {
            return;
        }

        for (final Map.Entry<String, List<Measure>> entry : measures.entrySet()) {
            final InputFile defaultInputFile = new DefaultInputFile(SwiftPlugin.PROPERTY_PREFIX, entry.getKey());
            if (fileExists(defaultInputFile)) {
                for (Measure measure : entry.getValue()) {
                    try {
                        LOGGER.debug("Save measure {} for file {}", measure.getMetric().getName(), defaultInputFile);
                        context.saveMeasure(defaultInputFile, measure);
                    } catch (final Exception e) {
                        LOGGER.error(" Exception -> {} -> {}", entry.getKey(), measure.getMetric().getName());
                    }
                }
            }
        }
    }

    private boolean fileExists(final InputFile file) {
        return context.getResource(file) != null;
    }
}
