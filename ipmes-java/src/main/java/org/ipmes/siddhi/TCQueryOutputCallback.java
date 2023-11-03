package org.ipmes.siddhi;

import io.siddhi.core.event.Event;
import io.siddhi.core.stream.output.StreamCallback;
import org.ipmes.join.Join;
import org.ipmes.decomposition.TCQuery;
import org.ipmes.match.MatchEdge;
import org.ipmes.match.MatchResult;
import org.ipmes.pattern.PatternEdge;
import org.ipmes.pattern.PatternGraph;
import org.ipmes.pattern.PatternNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class TCQueryOutputCallback extends StreamCallback {
    TCQuery query;
    PatternGraph patternGraph;
    Join join;
    HashMap<Integer, Integer> node2FieldIdx;

    public TCQueryOutputCallback(TCQuery query, PatternGraph patternGraph, Join join) {
        this.query = query;
        this.patternGraph = patternGraph;
        this.join = join;
        this.node2FieldIdx = new HashMap<>();

        ArrayList<PatternNode> nodes = query.getNodes();
        for (int i = 0; i < nodes.size(); ++i) {
            node2FieldIdx.put(nodes.get(i).getId(), i);
        }
    }

    MatchResult toMatchResult(Event e) {
        Object[] data = e.getData();
        int numNodes = this.query.numNodes();
        ArrayList<PatternEdge> patternEdges = this.query.getEdges();

        MatchResult res = new MatchResult();
        for (int i = 0; i < patternEdges.size(); ++i) {
            long ts = (long) data[numNodes + i * 2];
            long eid = (long) data[numNodes + i * 2 + 1];
            PatternEdge matched = patternEdges.get(i);
            Integer startIdx = this.node2FieldIdx.get(matched.getStartId());
            long startMatch = (long) data[startIdx];
            Integer endIdx = this.node2FieldIdx.get(matched.getEndId());
            long endMatch = (long) data[endIdx];

            res.addMatchEdge(new MatchEdge(
                    eid,
                    ts,
                    startMatch,
                    endMatch,
                    matched));
        }
        return res;
    }

    @Override
    public void receive(Event[] events) {
        for (Event e : events) {
            MatchResult res = toMatchResult(e);
            if (!res.checkNodeUniqueness())
                continue;
            this.join.addMatchResult(res, this.query.getId());
        }
    }
}
