package org.ipmes;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
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
        parser.addArgument("--dump-trigger-counts")
                .dest("dumpTriggerCounts")
                .action(Arguments.storeTrue())
                .setDefault(false)
                .help("Output trigger counts.");
        parser.addArgument("--dump-results")
                .dest("dumpResults")
                .action(Arguments.storeTrue())
                .setDefault(false)
                .help("Output match results.");
        parser.addArgument("-w", "--window-size").type(Long.class)
                .dest("windowSize")
                .setDefault(1800L)
                .help("Time window size (sec) when joining.");
        parser.addArgument("--debug")
                .action(Arguments.storeTrue())
                .setDefault(false)
                .help("Output debug information.");
        parser.addArgument("pattern_file").type(String.class)
                .required(true)
                .help("The path of pattern's file, e.g. ../data/universal_patterns/TTP11.json");
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

        Boolean isDebug = ns.getBoolean("debug");
        Boolean dumpTriggerCounts = ns.getBoolean("dumpTriggerCounts");
        Boolean dumpResults = ns.getBoolean("dumpResults");
        String patternFile = ns.getString("pattern_file");
        String dataGraphPath = ns.getString("data_graph");
        long windowSize = ns.getLong("windowSize") * 1000;

        // parse data
        Pattern pattern;
        try {
            pattern = PatternParser.parse(patternFile);
        } catch (IOException exception) {
            System.err.println("Failed to parse pattern");
            return;
        }
        PatternGraph spatialPattern = pattern.patternGraph;
        TemporalRelation temporalPattern = pattern.temporalRelation;

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

        TCMatcher matcher = new TCMatcher(tcQueries, pattern.useRegex, windowSize, join);
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

        if (dumpTriggerCounts)
            output.put("TriggerCounts", matcher.getTriggerCounts());

        Collection<FullMatch> results = join.extractAnswer();
        output.put("NumResults", results.size());

        if (dumpResults) {
            List<JSONObject> resultOutput = new LinkedList<>();
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