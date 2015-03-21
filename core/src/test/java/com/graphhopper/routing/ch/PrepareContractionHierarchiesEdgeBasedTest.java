package com.graphhopper.routing.ch;

import com.graphhopper.routing.util.TraversalMode;
import org.junit.Test;

public class PrepareContractionHierarchiesEdgeBasedTest extends PrepareContractionHierarchiesTest
{
    public PrepareContractionHierarchiesEdgeBasedTest(){
        tMode = TraversalMode.EDGE_BASED_2DIR;
    }

    @Test
    @Override
    public void testSchortcuts3()
    {
        super.testSchortcuts3();
    }
}
