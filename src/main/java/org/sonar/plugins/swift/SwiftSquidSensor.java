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
package org.sonar.plugins.swift;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.Sensor;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.batch.fs.FilePredicate;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.rule.CheckFactory;
import org.sonar.api.batch.rule.Checks;
import org.sonar.api.component.ResourcePerspectives;
import org.sonar.api.issue.Issuable;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.resources.Project;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.scan.filesystem.PathResolver;
import org.sonar.plugins.swift.lang.SwiftAstScanner;
import org.sonar.plugins.swift.lang.SwiftConfiguration;
import org.sonar.plugins.swift.lang.api.SwiftGrammar;
import org.sonar.plugins.swift.lang.api.SwiftMetric;
import org.sonar.plugins.swift.lang.checks.CheckList;
import org.sonar.plugins.swift.lang.core.Swift;
import org.sonar.squidbridge.AstScanner;
import org.sonar.squidbridge.SquidAstVisitor;
import org.sonar.squidbridge.api.CheckMessage;
import org.sonar.squidbridge.api.SourceCode;
import org.sonar.squidbridge.api.SourceFile;
import org.sonar.squidbridge.checks.SquidCheck;
import org.sonar.squidbridge.indexer.QueryByType;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Collection;
import java.util.List;
import java.util.Locale;


public class SwiftSquidSensor implements Sensor {

    private static final Logger LOGGER = LoggerFactory.getLogger(SwiftSquidSensor.class);
    private final FileSystem fileSystem;
    private final PathResolver pathResolver;
    private final ResourcePerspectives resourcePerspectives;
    private final Checks<SquidCheck<SwiftGrammar>> checks;
    private final FilePredicate mainFilePredicates;
    private SensorContext context;

    public SwiftSquidSensor(FileSystem fileSystem, PathResolver pathResolver, ResourcePerspectives
            resourcePerspectives, CheckFactory checkFactory) {

        this.fileSystem = fileSystem;
        this.pathResolver = pathResolver;
        this.resourcePerspectives = resourcePerspectives;
        this.checks = checkFactory.<SquidCheck<SwiftGrammar>>create(CheckList.REPOSITORY_KEY).addAnnotatedChecks(CheckList
                .getChecks());
        this.mainFilePredicates = fileSystem.predicates()
                .and(fileSystem.predicates().hasLanguage(Swift.KEY), fileSystem.predicates().hasType(InputFile.Type.MAIN));
    }

    public boolean shouldExecuteOnProject(Project project) {
        return project.isRoot() && fileSystem.hasFiles(fileSystem.predicates().hasLanguage(Swift.KEY));
    }

    public void analyse(Project project, SensorContext context) {
        this.context = context;

        List<SquidAstVisitor<SwiftGrammar>> visitors = Lists.<SquidAstVisitor<SwiftGrammar>>newArrayList(checks.all());
        AstScanner<SwiftGrammar> scanner = SwiftAstScanner.create(createConfiguration(), visitors.toArray(new
                SquidAstVisitor[visitors
                .size()]));

        scanner.scanFiles(ImmutableList.copyOf(fileSystem.files(mainFilePredicates)));

        Collection<SourceCode> squidSourceFiles = scanner.getIndex().search(new QueryByType(SourceFile.class));
        save(squidSourceFiles);
    }

    private SwiftConfiguration createConfiguration() {
        return new SwiftConfiguration(fileSystem.encoding());
    }

    private void save(Collection<SourceCode> squidSourceFiles) {
        for (SourceCode squidSourceFile : squidSourceFiles) {
            SourceFile squidFile = (SourceFile) squidSourceFile;

            String relativePath = pathResolver.relativePath(fileSystem.baseDir(), new java.io.File(squidFile.getKey()));
            InputFile inputFile = fileSystem.inputFile(fileSystem.predicates().hasRelativePath(relativePath));

            saveMeasures(inputFile, squidFile);
            saveIssues(inputFile, squidFile);
        }
    }

    private void saveMeasures(InputFile inputFile, SourceFile squidFile) {
        context.saveMeasure(inputFile, CoreMetrics.FILES, squidFile.getDouble(SwiftMetric.FILES));
        context.saveMeasure(inputFile, CoreMetrics.LINES, squidFile.getDouble(SwiftMetric.LINES));
        context.saveMeasure(inputFile, CoreMetrics.NCLOC, squidFile.getDouble(SwiftMetric.LINES_OF_CODE));
        //context.saveMeasure(inputFile, CoreMetrics.FUNCTIONS, squidFile.getDouble(SwiftMetric.FUNCTIONS));
        context.saveMeasure(inputFile, CoreMetrics.STATEMENTS, squidFile.getDouble(SwiftMetric.STATEMENTS));
        //context.saveMeasure(inputFile, CoreMetrics.COMPLEXITY, squidFile.getDouble(SwiftMetric.COMPLEXITY));
        context.saveMeasure(inputFile, CoreMetrics.COMMENT_LINES, squidFile.getDouble(SwiftMetric.COMMENT_LINES));
    }

    private void saveIssues(InputFile inputFile, SourceFile squidFile) {
        Collection<CheckMessage> messages = squidFile.getCheckMessages();
        FileReader fileReader = null;

        try {
            fileReader = new FileReader(inputFile.file());
            final InputFile defaultInputFile = new DefaultInputFile(SwiftPlugin.PROPERTY_PREFIX, inputFile.relativePath());

            if (messages != null && defaultInputFile != null) {
                for (CheckMessage message : messages) {
                    RuleKey ruleKey = checks.ruleKey((SquidCheck<SwiftGrammar>) message.getCheck());
                    Issuable issuable = resourcePerspectives.as(Issuable.class, defaultInputFile);

                    if (issuable != null) {
                        Issuable.IssueBuilder issueBuilder = issuable.newIssueBuilder()
                                .ruleKey(ruleKey)
                                .line(message.getLine())
                                .message(message.getText(Locale.ENGLISH));

                        if (message.getCost() != null) {
                            issueBuilder.effortToFix(message.getCost());
                        }

                        issuable.addIssue(issueBuilder.build());
                    }
                }
            }
        } catch (FileNotFoundException e) {
            LOGGER.error("Failed to parse input file", e);
        } finally {
            IOUtils.closeQuietly(fileReader);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
