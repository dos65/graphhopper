package com.graphhopper.routing.ch;

import com.graphhopper.routing.DijkstraBidirectionRef;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.HashSet;
import java.util.Set;

public class DijkstraBidirCHEdge extends DijkstraBidirectionRef
{
    private final LevelGraph baseGraph;
    private TIntObjectMap<Set<Integer>> node2IdsFrom;
    private TIntObjectMap<Set<Integer>> node2IdsTo;
    private static int initialCollectionSize = 5000;
    private int from, to;

    public DijkstraBidirCHEdge(Graph graph, LevelGraph baseGraph, FlagEncoder encoder, Weighting weighting, TraversalMode tMode)
    {
        super(graph, encoder, weighting, tMode);
        this.baseGraph = baseGraph;
    }

    @Override
    protected void initCollections( int nodes )
    {
        // algorithm with CH does not need that much memory pre allocated
        super.initCollections(Math.min(initialCollectionSize, nodes));
        node2IdsFrom = new TIntObjectHashMap<Set<Integer>>(Math.min(initialCollectionSize, nodes));
        node2IdsTo = new TIntObjectHashMap<Set<Integer>>(Math.min(initialCollectionSize, nodes));
    }

    @Override
    public void initFrom(int from, double dist)
    {
        super.initFrom(from, dist);
        this.from = from;
    }

    @Override
    public void initTo(int to, double dist)
    {
        super.initTo(to, dist);
        this.to = to;
    }

    @Override
    protected Path createAndInitPath()
    {
        bestPath = new Path4CH(graph, graph.getBaseGraph(), flagEncoder);
        return bestPath;
    }

    @Override
    public boolean finished()
    {
        // we need to finish BOTH searches for CH!
        if (finishedFrom && finishedTo)
            return true;

        // changed also the final finish condition for CH
        return currFrom.weight >= bestPath.getWeight() && currTo.weight >= bestPath.getWeight();
    }

    @Override
    protected boolean isWeightLimitExceeded()
    {
        return currFrom.weight > weightLimit && currTo.weight > weightLimit;
    }

    private void updateNode2IdsMap(int node, int traversalId)
    {
        TIntObjectMap<Set<Integer>> node2Ids = isReverse() ? node2IdsTo : node2IdsFrom;
        Set<Integer> ids = node2Ids.get(node);
        if(ids == null)
        {
            ids = new HashSet<Integer>(2);
            node2Ids.put(node, ids);
        }
        ids.add(traversalId);
    }

    @Override
    protected void updateBestPath(EdgeIteratorState edgeState, EdgeEntry entryCurrent, int traversalId)
    {
        int node = edgeState.getAdjNode();
        updateNode2IdsMap(edgeState.getAdjNode(), traversalId);

        int goalNode = isReverse()? from : to;
        if(edgeState.getAdjNode() == goalNode)
        {
            EdgeEntry emptyEe = createEdgeEntry(goalNode, 0);
            updateBestPath(edgeState, entryCurrent, emptyEe);
        }

        Set<Integer> otherIds = getOtherIdsMap().get(node);
        if(otherIds == null)
            return;

        for(int otherId : otherIds)
        {
            EdgeEntry entryOther = bestWeightMapOther.get(otherId);
            if(entryOther == null)
                throw new IllegalStateException("cannot happen for execution of " + getName());
            updateBestPath(edgeState, entryCurrent, entryOther);
        }
    }

    private void updateBestPath(EdgeIteratorState edgeState, EdgeEntry entryCurrent, EdgeEntry entryOther)
    {
        if(entryCurrent.adjNode != entryOther.adjNode)
            throw new IllegalStateException("cannot happen for execution of " + getName());

        if(!acceptUTurn(entryCurrent.edge, edgeState.getAdjNode(), entryOther.edge))
            return;

        edgeState = graph.getEdgeProps(edgeState.getEdge(), edgeState.getBaseNode());
        //TODO: UTurnCost??
        double checkWeight = weighting.calcWeight(edgeState, !isReverse(), entryOther.edge);
        if(Double.isInfinite(checkWeight))
            return;

        double newWeight = entryCurrent.weight + entryOther.weight;
        if(newWeight < bestPath.getWeight())
        {
            bestPath.setSwitchToFrom(isReverse());
            bestPath.setEdgeEntry(entryCurrent);
            bestPath.setWeight(newWeight);
            bestPath.setEdgeEntryTo(entryOther);
        }
    }

    private TIntObjectMap<Set<Integer>> getOtherIdsMap()
    {
        return isReverse()? node2IdsFrom : node2IdsTo;
    }

    @Override
    public boolean accept(EdgeIterator iter, int prevOrNextEdgeId)
    {
        if (!acceptUTurn(iter.getEdge(), iter.getBaseNode(), prevOrNextEdgeId))
            return false;

        return getAdditionalEdgeFilter() == null || getAdditionalEdgeFilter().accept(iter);
    }

    private boolean acceptUTurn(int edge1, int viaNode, int edge2)
    {
        if(traversalMode.hasUTurnSupport())
            return true;

        int origEdge1 = baseGraph.getOrigEdge(edge1, viaNode);
        int origEdge2 = baseGraph.getOrigEdge(edge2, viaNode);

        return origEdge1 != origEdge2;
    }
}
