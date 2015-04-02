package com.graphhopper.routing.ch;

import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.util.*;
import com.graphhopper.storage.*;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CHTurnCostsTest
{
    private final EncodingManager em = new EncodingManager("car|turnCosts=true");
    private final FlagEncoder encoder = em.getEncoder("CAR");
    private final Weighting weighting = new ShortestWeighting();
    private final TraversalMode tMode = TraversalMode.EDGE_BASED_2DIR;
    private Directory dir;

    @Before
    public void setUp() {
        dir = new GHDirectory("", DAType.RAM_INT);
    }

    @Test
    public void testTurnCosts()
    {
        LevelGraphStorage g = new GraphBuilder(em).setLevelGraph(true).levelGraphCreate();
        PrepareContractionHierarchiesTest.initDirected2(g);
        TurnCostExtension turnCosts = (TurnCostExtension) g.getExtension();

        EdgeIteratorState edgeFrom = GHUtility.getEdge(g, 8, 9);
        EdgeIteratorState edgeTo = GHUtility.getEdge(g, 9, 10);
        turnCosts.addTurnInfo(edgeFrom.getEdge(), 9, edgeTo.getEdge(), encoder.getTurnFlags(true, 0));

        TurnWeighting turnWeighting = new TurnWeighting(weighting, encoder, turnCosts);

        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies(dir, g, encoder, turnWeighting, tMode);
        prepare.doWork();

        RoutingAlgorithm algo = prepare.createAlgo(g, new AlgorithmOptions("dijkstrabi", encoder, turnWeighting, tMode));
        Path p = algo.calcPath(0, 10);
        assertEquals(Helper.createTList(0, 17, 16, 15, 14, 13, 12, 11, 9, 10), p.calcNodes());
    }

    @Test
    public void testLeadToInfinity()
    {
        LevelGraphStorage g = new GraphBuilder(em).setLevelGraph(true).levelGraphCreate();
        initTurnCostsGraph(g);
        TurnCostExtension turnCosts = (TurnCostExtension) g.getExtension();

        EdgeIteratorState edgeFrom = GHUtility.getEdge(g, 1, 9);
        EdgeIteratorState edgeTo = GHUtility.getEdge(g, 9, 10);
        turnCosts.addTurnInfo(edgeFrom.getEdge(), 9, edgeTo.getEdge(), encoder.getTurnFlags(true, 0));

        TurnWeighting turnWeighting = new TurnWeighting(weighting, encoder, turnCosts);
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies(dir, g, em.getEncoder("CAR"), turnWeighting, tMode);
        prepare.doWork();

        RoutingAlgorithm algo = prepare.createAlgo(g, new AlgorithmOptions("dijkstrabi", encoder, turnWeighting, tMode));
        Path p = algo.calcPath(0, 10);
        assertEquals(Helper.createTList(0, 1, 2, 8, 9, 10), p.calcNodes());
    }

    private void initTurnCostsGraph(Graph g)
    {
        /*
             5 4    0
              \|    |
            6-3--2--1
              |  |  |
              7--8--9-10
                    |
                    11
         */
        g.edge(0, 1, 1, true);
        g.edge(1, 2, 1, false);
        g.edge(2, 3, 1, false);
        g.edge(3, 4, 1, true);
        g.edge(3, 5, 1, true);
        g.edge(3, 6, 1, true);
        g.edge(3, 7, 1, false);
        g.edge(7, 8, 1, true);
        g.edge(8, 9, 1, true);
        g.edge(2, 8, 1, false);
        g.edge(1, 9, 1, true);
        g.edge(9, 10, 1, true);
        g.edge(9, 11, 1, true);
    }
}
