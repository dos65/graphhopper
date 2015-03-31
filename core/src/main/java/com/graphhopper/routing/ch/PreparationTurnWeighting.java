package com.graphhopper.routing.ch;

import com.graphhopper.routing.util.TurnCostEncoder;
import com.graphhopper.routing.util.TurnWeighting;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.routing.util.WeightingWrapper;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.storage.TurnCostExtension;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.EdgeSkipIterState;

public class PreparationTurnWeighting implements WeightingWrapper
{
    private final Graph graph;
    private final TurnWeighting weighting;
    private final Weighting userWeighting;
    private final TurnCostEncoder encoder;
    private final TurnCostExtension turnCostExt;
    private final LevelGraph baseGraph;
    private int maxNodes;
    private double defaultUTurnCost = 40;

    public PreparationTurnWeighting(Graph graph, LevelGraph baseGraph, TurnWeighting weighting, TurnCostEncoder encoder)
    {
        this.graph = graph;
        this.weighting = weighting;
        this.encoder = encoder;
        this.turnCostExt = weighting.getTurnCostExt();
        this.baseGraph = baseGraph;
        this.userWeighting = weighting.getWrappedWeighting();
        maxNodes = graph.getNodes();
    }

    @Override
    public double getMinWeight(double distance)
    {
        return weighting.getMinWeight(distance);
    }

    private double calcEdgeStateWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId )
    {
        if (edgeState instanceof EdgeSkipIterState)
        {
            EdgeSkipIterState tmp = (EdgeSkipIterState) edgeState;
            if (tmp.isShortcut())
                // if a shortcut is in both directions the weight is identical => no need for 'reverse'
                return tmp.getWeight();
        }
        return userWeighting.calcWeight(edgeState, reverse, prevOrNextEdgeId);

    }

    @Override
    public double calcWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId)
    {
        double weight = calcEdgeStateWeight(edgeState, reverse, prevOrNextEdgeId);

        if (prevOrNextEdgeId == EdgeIterator.NO_EDGE)
            return weight;

        int edge1, edge2;

        int nodeVia = edgeState.getBaseNode();
        edgeState = graph.getEdgeProps(edgeState.getEdge(), edgeState.getAdjNode());
        edge1 = getRealEdgeId(edgeState, nodeVia);

        EdgeIteratorState edgeState2 = graph.getEdgeProps(prevOrNextEdgeId, nodeVia);
        edge2 = getRealEdgeId(edgeState2, nodeVia);

        double turnCosts;
        if(reverse)
            turnCosts = weighting.calcTurnWeight(edge1, nodeVia, edge2);
        else
            turnCosts = weighting.calcTurnWeight(edge2, nodeVia, edge1);

        if (turnCosts == 0 && edge1 == edge2)
            return weight + defaultUTurnCost;

        return weight + turnCosts;
    }

    protected int getRealEdgeId(EdgeIteratorState edge, int node)
    {
        EdgeSkipIterState sEdge = (EdgeSkipIterState) edge;

        while(sEdge.isShortcut())
        {
            EdgeSkipIterState nextEdge;
            nextEdge= (EdgeSkipIterState) graph.getEdgeProps(sEdge.getSkippedEdge1(), node);
            if(nextEdge == null)
                nextEdge = (EdgeSkipIterState) graph.getEdgeProps(sEdge.getSkippedEdge2(), node);
            //TODO
            if(nextEdge == null)
                throw new IllegalStateException();
            sEdge = nextEdge;
        }
        return sEdge.getEdge();
    }

    @Override
    public Weighting getWrappedWeighting()
    {
        return weighting;
    }

    @Override
    public String toString()
    {
        return "PREPARE+TURN WEIGHTING";
    }
}
