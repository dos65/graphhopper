package com.graphhopper.routing.ch;

import com.graphhopper.routing.util.TraversalMode;

public class PrepareContractionHierarchiesEdgeBasedTest extends PrepareContractionHierarchiesTest
{
    public PrepareContractionHierarchiesEdgeBasedTest(){
        tMode = TraversalMode.EDGE_BASED_2DIR;
    }
}
