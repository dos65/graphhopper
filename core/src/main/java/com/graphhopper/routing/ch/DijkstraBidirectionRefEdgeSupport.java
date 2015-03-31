package com.graphhopper.routing.ch;

import com.graphhopper.routing.DijkstraBidirectionRef;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.QueryGraph;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.util.TurnWeighting;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.EdgeSkipIterState;
import com.graphhopper.util.EdgeSkipIterator;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

class DijkstraBidirectionRefEdgeSupport extends DijkstraBidirectionRef
{
    private TIntObjectMap<Set<Integer>> node2IdsFrom;
    private TIntObjectMap<Set<Integer>> node2IdsTo;
    private static int initialCollectionSize = 5000;
    private int from, to;

    public DijkstraBidirectionRefEdgeSupport(Graph g, FlagEncoder encoder, Weighting weighting, TraversalMode tMode)
    {
        super(g, encoder, weighting, tMode);
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
    public boolean finished()
    {
        // we need to finish BOTH searches for CH!
        if (finishedFrom && finishedTo)
            return true;

        // changed also the final finish condition for CH
        return currFrom.weight >= bestPath.getWeight() && currTo.weight >= bestPath.getWeight();
    }

    private void updateNode2IdsMap(int node, int traversalId)
    {
        TIntObjectMap<Set<Integer>> node2Trav = isReverse() ? node2IdsTo : node2IdsFrom;
        Set<Integer> ids = node2Trav.get(node);
        if(ids == null)
        {
            ids = new HashSet<Integer>(2);
            node2Trav.put(node, ids);
        }
        ids.add(traversalId);
    }

    @Override
    protected void updateBestPath(EdgeIteratorState edgeState, EdgeEntry entryCurrent, int traversalId)
    {
        EdgeEntry entryOther = bestWeightMapOther.get(traversalId);
        if(entryOther != null)
        {
            if(checkUnpacked(entryCurrent, entryOther))
                updateBestPathByEe(edgeState, entryCurrent, entryOther);
        }

        updateNode2IdsMap(edgeState.getAdjNode(), traversalId);

        int goalNode = isReverse()? from : to;
        if(edgeState.getAdjNode() == goalNode && entryCurrent.weight < bestPath.getWeight())
        {
            EdgeEntry emptyEe = createEdgeEntry(goalNode, 0);
            setBestPath(entryCurrent, emptyEe, entryCurrent.weight);
        }

        TIntObjectMap<Set<Integer>> otherNode2IdsMap = isReverse()? node2IdsFrom : node2IdsTo;
        Set<Integer> otherIds = otherNode2IdsMap.get(edgeState.getAdjNode());
        if(otherIds == null)
            return;

        for(int otherId : otherIds)
        {
            EdgeEntry otherTravEdge = bestWeightMapOther.get(otherId);
            if (!checkUnpacked(entryCurrent, otherTravEdge))
                continue;

            updateBestPathByEe(edgeState, entryCurrent, otherTravEdge);
        }
    }

    private boolean checkUnpacked(EdgeEntry entryCurrent, EdgeEntry entryOther)
    {
        if(!(weighting instanceof PreparationTurnWeighting))
            return true;

        int viaNode, edgeFrom, edgeTo;
        edgeFrom = entryCurrent.edge;
        edgeTo = entryOther.edge;
        viaNode = entryCurrent.adjNode;

        EdgeSkipIterState edgeFromState = (EdgeSkipIterState) graph.getEdgeProps(edgeFrom, viaNode);
        EdgeSkipIterState edgeToState = (EdgeSkipIterState) graph.getEdgeProps(edgeTo, viaNode);

        PreparationTurnWeighting prepTurnWeighting = (PreparationTurnWeighting) weighting;

        edgeFrom = prepTurnWeighting.getRealEdgeId(edgeFromState, viaNode);
        edgeTo = prepTurnWeighting.getRealEdgeId(edgeToState, viaNode);

        if(edgeFrom == edgeTo && !traversalMode.hasUTurnSupport())
            return false;

        TurnWeighting turnWeighting = (TurnWeighting) prepTurnWeighting.getWrappedWeighting();
        double turnWeight;
        if(isReverse())
            turnWeight = turnWeighting.calcTurnWeight(edgeTo, viaNode, edgeFrom);
        else
            turnWeight = turnWeighting.calcTurnWeight(edgeFrom, viaNode, edgeTo);

        return !Double.isInfinite(turnWeight);
    }

    private void updateBestPathByEe(EdgeIteratorState edgeState, EdgeEntry entryCurrent, EdgeEntry entryOther)
    {


        double newWeight = entryCurrent.weight + entryOther.weight;
        if (entryOther.adjNode != entryCurrent.adjNode)
        {
            // prevents the path to contain the edge at the meeting point twice and subtract the weight (excluding turn weight => no previous edge)
            entryCurrent = entryCurrent.parent;
            newWeight -= weighting.calcWeight(edgeState, isReverse(), EdgeIterator.NO_EDGE);
        } else
        {
            // we detected a u-turn at meeting point, skip if not supported
            if (entryCurrent.edge == entryOther.edge && !traversalMode.hasUTurnSupport())
                return;
        }

        if (newWeight < bestPath.getWeight())
            setBestPath(entryCurrent, entryOther, newWeight);
    }

    private void setBestPath(EdgeEntry entryCurrent, EdgeEntry entryOther, double newWeight)
    {
        bestPath.setSwitchToFrom(isReverse());
        bestPath.setEdgeEntry(entryCurrent);
        bestPath.setWeight(newWeight);
        bestPath.setEdgeEntryTo(entryOther);
    }

    @Override
    protected boolean accept(EdgeIterator iter, int prevOrNextEdgeId)
    {
        if(!traversalMode.hasUTurnSupport() && prevOrNextEdgeId != EdgeIterator.NO_EDGE)
        {
            int viaNode, edgeFrom, edgeTo;
            edgeFrom = iter.getEdge();
            edgeTo = prevOrNextEdgeId;
            viaNode = iter.getBaseNode();
            EdgeSkipIterState edgeFromState = (EdgeSkipIterState) graph.getEdgeProps(edgeFrom, viaNode);
            EdgeSkipIterState edgeToState = (EdgeSkipIterState) graph.getEdgeProps(edgeTo, viaNode);

            PreparationTurnWeighting prepTurnWeighting = (PreparationTurnWeighting) weighting;
            edgeFrom = prepTurnWeighting.getRealEdgeId(edgeFromState, viaNode);
            edgeTo = prepTurnWeighting.getRealEdgeId(edgeToState, viaNode);

            if (edgeFrom == edgeTo)
                return false;
        }

        return super.accept(iter, prevOrNextEdgeId);
    }

    @Override
    protected boolean isWeightLimitExceeded()
    {
        return currFrom.weight > weightLimit && currTo.weight > weightLimit;
    }

    @Override
    protected Path createAndInitPath()
    {
        bestPath = new Path4CH(graph, graph.getBaseGraph(), flagEncoder);
        return bestPath;
    }

    @Override
    public String getName()
    {
        return "dijkstrabiCH|edgeBased";
    }

    @Override
    public String toString()
    {
        return getName() + "|" + weighting;
    }
}
