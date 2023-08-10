package org.ipmes.join;

import java.util.ArrayList;

import org.ipmes.decomposition.TCQuery;
import org.ipmes.decomposition.TCQueryRelation;
import org.ipmes.pattern.DependencyGraph;
import org.ipmes.pattern.PatternEdge;
import org.ipmes.pattern.PatternGraph;

public class PriorityGenRel {

    DependencyGraph temporalRelation;
    PatternGraph spatialRelation;
    ArrayList<TCQueryRelation>[] relations;
    ArrayList<TCQuery> subTCQueries;

    public PriorityGenRel(DependencyGraph temporalRelation, PatternGraph spatialRelation, ArrayList<TCQuery> selected) {
        this.temporalRelation = temporalRelation;
        this.spatialRelation = spatialRelation;
        this.subTCQueries = selected;
        this.relations = genRelations(selected);
    }

    public ArrayList<TCQueryRelation>[] getRelation() {
        return this.relations;
    }

    boolean hasRelations(PatternEdge edge1, PatternEdge edge2) {
        return this.temporalRelation.getParents(edge1.getId()).contains(edge2.getId())
                || this.temporalRelation.getChildren(edge1.getId()).contains(edge2.getId())
                || (!this.spatialRelation.getSharedNodes(edge1.getId(), edge2.getId()).isEmpty());
    }

    ArrayList<TCQueryRelation> helperForGenerate(int resultId, int entryId) {
        ArrayList<TCQueryRelation> ret = new ArrayList<TCQueryRelation>();
        for (PatternEdge edge1 : this.subTCQueries.get((resultId + 1) / 2).getEdges()) {
            for (PatternEdge edge2 : this.subTCQueries.get((entryId + 1) / 2).getEdges()) {
                if (hasRelations(edge1, edge2)) {
                    TCQueryRelation tempRelation = new TCQueryRelation();
                    tempRelation.idOfResult = edge1.getId();
                    tempRelation.idOfEntry = edge2.getId();
                    ret.add(tempRelation);
                }
            }
        }
        return ret;
    }

    ArrayList<TCQueryRelation> evenGenRel(int id) {
        ArrayList<TCQueryRelation> ret = new ArrayList<TCQueryRelation>();
        int entryId = id + 1, resultId = 0;
        ret.addAll(helperForGenerate(resultId, entryId));
        for (resultId = 1; resultId < entryId; resultId += 2)
            ret.addAll(helperForGenerate(resultId, entryId));
        return ret;
    }

    ArrayList<TCQueryRelation> oddGenRel(int id) {
        ArrayList<TCQueryRelation> ret = new ArrayList<TCQueryRelation>();
        int resultId = id, entryId = 0;
        ret.addAll(helperForGenerate(resultId, entryId));
        for (entryId = 1; entryId < resultId; entryId += 2)
            ret.addAll(helperForGenerate(resultId, entryId));
        return ret;
    }

    /**
     * Generate relations for all selected TC Query. This method will collect all
     * relations
     * related with all edges in a tc Query, including temporal relations and
     * spatial relations.
     * 
     * @param selected selected TC-Query
     * @return an array of relations, i-th element is the relations for i-th
     *         TC-Query
     */
    ArrayList<TCQueryRelation>[] genRelations(ArrayList<TCQuery> selected) {
        ArrayList<TCQueryRelation>[] relations = (ArrayList<TCQueryRelation>[]) new ArrayList[2 * selected.size() - 1];
        relations[0] = new ArrayList<>();
        if (relations.length == 1)
            return relations;
        relations[0] = helperForGenerate(0, 1);
        for (int i = 1; i < relations.length - 1; i++) {
            relations[i] = new ArrayList<>();
            if (i % 2 == 0) {
                relations[i] = evenGenRel(i);
            } else {
                relations[i] = oddGenRel(i);
            }
        }
        return relations;
    }
}
