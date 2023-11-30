package org.ipmes;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;
import java.lang.Runtime;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import org.ipmes.decomposition.TCQGenerator;
import org.ipmes.decomposition.TCQuery;
import org.ipmes.join.Join;
import org.ipmes.join.PriorityJoin;
import org.ipmes.match.FullMatch;
import org.ipmes.match.LiteMatchResult;
import org.ipmes.pattern.*;

import org.json.JSONObject;

public class Main {

    static ArgumentParser getParser() {
        ArgumentParser parser = ArgumentParsers.newFor("ipmes-java").build()
                .defaultHelp(true)
                .description("IPMES implemented in Java.");
        parser.addArgument("-r", "--regex")
                .dest("useRegex")
                .setDefault(false)
                .action(Arguments.storeTrue())
                .help("Explicitly use regex matching. Default will automatically depend on the pattern prefix name");
        parser.addArgument("--darpa")
                .action(Arguments.storeTrue())
                .setDefault(false)
                .help("We are running on DARPA dataset.");
        parser.addArgument("--dump-results")
                .dest("dumpResults")
                .action(Arguments.storeTrue())
                .setDefault(false)
                .help("Output match results.");
        parser.addArgument("--pattern-format")
                .dest("patternFormat")
                .choices(new String[]{"SPADE", "DARPA", "Universal"})
                .setDefault("SPADE")
                .help("Pattern Format");
        parser.addArgument("-w", "--window-size").type(Long.class)
                .dest("windowSize")
                .setDefault(1800L)
                .help("Time window size (sec) when joining.");
        parser.addArgument("--debug")
                .action(Arguments.storeTrue())
                .setDefault(false)
                .help("Output debug information.");
        parser.addArgument("pattern_prefix").type(String.class)
                .required(true)
                .help("The path prefix of pattern's files, e.g. ./data/patterns/TTP11");
        parser.addArgument("data_graph").type(String.class)
                .required(true)
                .help("The path to the preprocessed data graph");

        return parser;
    }

    public static void main(String[] args) throws Exception {
        // parse argument
        ArgumentParser parser = getParser();
        Namespace ns = null;
        try {
            ns = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }
        Boolean useRegex = ns.getBoolean("useRegex");
        Boolean isDarpa = ns.getBoolean("darpa");
        Boolean isDebug = ns.getBoolean("debug");
        Boolean dumpResults = ns.getBoolean("dumpResults");
        String patternFormat = ns.getString("patternFormat");
        String ttpPrefix = ns.getString("pattern_prefix");
        String dataGraphPath = ns.getString("data_graph");
        long windowSize = ns.getLong("windowSize") * 1000;
        if (isDarpa)
            patternFormat = "DARPA";

        // parse data
        String orelsFile;
        if (ttpPrefix.endsWith("regex")) {
            useRegex = true;
            orelsFile = ttpPrefix.substring(0, ttpPrefix.length() - 5) + "oRels.json";
        } else {
            orelsFile = ttpPrefix + "_oRels.json";
        }

        PatternGraph spatialPattern;
        if (patternFormat.equals("Universal")) {
            spatialPattern = PatternGraph.parseUniversalPattern(new FileReader(ttpPrefix + ".csv"));
        } else {
            SigExtractor extractor;
            if (patternFormat.equals("DARPA"))
                extractor = new DarpaExtractor();
            else
                extractor = new SpadePatternExtractor();
            spatialPattern = PatternGraph
                    .parse(
                            new FileReader(ttpPrefix + "_node.json"),
                            new FileReader(ttpPrefix + "_edge.json"),
                            extractor)
                    .get();
        }

        TemporalRelation temporalPattern = TemporalRelation.parse(new FileReader(orelsFile)).get();

        LiteMatchResult.MAX_NUM_NODES = spatialPattern.numNodes();

        if (isDebug) {
            System.err.println("Patterns:");
            spatialPattern.getEdges().forEach(System.err::println);
        }

        // Decomposition
        TCQGenerator d = new TCQGenerator(temporalPattern, spatialPattern);
        ArrayList<TCQuery> tcQueries = d.decompose();

        if (isDebug) {
            System.err.println("TC Queries:");
            tcQueries.forEach(System.err::println);
        }

        Join join = new PriorityJoin(temporalPattern, spatialPattern, windowSize, tcQueries);

        BufferedReader inputReader = new BufferedReader(new FileReader(dataGraphPath));
        String line = inputReader.readLine();

        TCMatcher matcher = new TCMatcher(tcQueries, useRegex, windowSize, join);
        EventSender sender = new EventSender(matcher);
        int maxPoolSize = 0;
        Runtime jvm = Runtime.getRuntime();
        long maxHeapSize = jvm.totalMemory();
        while (line != null) {
            sender.sendLine(line);
            line = inputReader.readLine();
            maxPoolSize = Math.max(maxPoolSize, join.getPoolSize() + matcher.getPoolSize());
            maxHeapSize = Math.max(maxHeapSize, jvm.totalMemory());
        }
        sender.flushBuffers();

        // output
        JSONObject output = new JSONObject();

        output.put("PeakPoolSize", maxPoolSize);
        output.put("PeakHeapSize", maxHeapSize);

        output.put("TriggerCounts", matcher.getTriggerCounts());

        Collection<FullMatch> results = join.extractAnswer();
        output.put("NumResults", results.size());

        if (dumpResults) {
            ArrayList<JSONObject> resultOutput = new ArrayList<>();
            for (FullMatch result : results) {
                JSONObject obj = new JSONObject();
                obj.put("StartTime", result.getStartTime());
                obj.put("EndTime", result.getEndTime());
                obj.put("MatchIDs", result.getMatchData());
                resultOutput.add(obj);
            }
            output.put("MatchResults", resultOutput);
        }

        if (isDebug) {
            System.err.println("Match Results:");
            for (FullMatch result : results)
                System.err.println(result);
        }

        System.out.println(output.toString(2));
    }
}