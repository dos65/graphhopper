package com.graphhopper.routing;

import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.storage.Graph;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public class DijkstraOneToManyTraversalTest extends AbstractRoutingAlgorithmTester
{

    /**
     * Runs the same test with each of the supported traversal modes
     */
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> configs()
    {
        return Arrays.asList(new Object[][]
                {
                        {TraversalMode.NODE_BASED},
                        {TraversalMode.EDGE_BASED_1DIR},
                        {TraversalMode.EDGE_BASED_2DIR},
                        {TraversalMode.EDGE_BASED_2DIR_UTURN}
                });
    }

    private final TraversalMode traversalMode;

    public DijkstraOneToManyTraversalTest(TraversalMode tMode)
    {
        this.traversalMode = tMode;
    }

    @Override
    public RoutingAlgorithmFactory createFactory( Graph prepareGraph, AlgorithmOptions prepareOpts )
    {
        return new RoutingAlgorithmFactory()
        {
            @Override
            public RoutingAlgorithm createAlgo( Graph g, AlgorithmOptions opts )
            {
                return new DijkstraOneToManyTraversal(g, opts.getFlagEncoder(), opts.getWeighting(), traversalMode);
            }
        };
    }
}
