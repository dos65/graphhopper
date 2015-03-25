package com.graphhopper.routing;

import com.graphhopper.coll.GHTreeMapComposed;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeIterator;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

import java.util.*;

public class DijkstraOneToManyTraversal extends AbstractRoutingAlgorithm
{
    public static final EdgeEntry NOT_FOUND_EE = new EdgeEntry(-1, -1, 0);
    private int limitVisitedNodes = Integer.MAX_VALUE;

    private AutoVector<EdgeEntry> weights;

    private EdgeEntry[] reachedNodes;
    private PriorityQueue<EdgeEntry> heap;

    private final TIntArrayListWithCap changedIds;
    private final TIntArrayListWithCap changedNodes;

    private EdgeEntry currEdge;

    private TIntSet visitedNodes;
    private int to;
    private boolean doClear = true;

    public DijkstraOneToManyTraversal(Graph graph, FlagEncoder encoder, Weighting weighting, TraversalMode trMode)
    {
        super(graph, encoder, weighting, trMode);

        int capacity = matchCapacity();
        weights = new AutoVector<EdgeEntry>(capacity + 1);
        heap = new PriorityQueue<EdgeEntry>(1000);
        reachedNodes = new EdgeEntry[graph.getNodes()];
        
        changedIds = new TIntArrayListWithCap();
        changedNodes = new TIntArrayListWithCap();

        visitedNodes = new TIntHashSet();
    }

    private int matchCapacity()
    {
        if(!traversalMode.isEdgeBased())
            return graph.getNodes();

        int edges = graph.getAllEdges().getCount();
        if (traversalMode.getNoOfStates() == 1)
            return edges;
        else
            return (edges << 1) + 1;
    }

    public DijkstraOneToManyTraversal setLimitVisitedNodes(int nodes)
    {
        this.limitVisitedNodes = nodes;
        return this;
    }

    public EdgeEntry findEE(int from, int to)
    {
        this.to = to;

        if(doClear)
        {
            lazyClear();

            currEdge = createEdgeEntry(from, 0);

            if(!traversalMode.isEdgeBased())
            {
                weights.set(from, currEdge);
                changedIds.add(from);
            }

        } else
        {
            EdgeEntry entry = reachedNodes[to];
            if(entry != null && entry.parent != null && entry.weight <= currEdge.weight)
                return entry;

            if(heap.isEmpty() || visitedNodes.size() >= limitVisitedNodes)
                return NOT_FOUND_EE;

            currEdge = heap.poll();
        }

        visitedNodes.clear();
        if(finished())
            return currEdge;

        while(true)
        {
            visitedNodes.add(currEdge.adjNode);
            EdgeIterator iter = outEdgeExplorer.setBaseNode(currEdge.adjNode);
            while(iter.next())
            {
                if(!accept(iter, currEdge.edge))
                    continue;

                double tmpWeight = weighting.calcWeight(iter, false, currEdge.edge) + currEdge.weight;
                if (Double.isInfinite(tmpWeight))
                    continue;

                int traversalId = traversalMode.createTraversalId(iter, false);
                EdgeEntry ee = weights.get(traversalId);
                if(ee == null)
                {
                    ee = new EdgeEntry(iter.getEdge(), iter.getAdjNode(), tmpWeight);
                    ee.parent = currEdge;
                    weights.set(traversalId, ee);
                    heap.add(ee);
                    updateReached(ee);

                    changedIds.add(traversalId);
                } else if (ee.weight > tmpWeight)
                {
                    updateReached(ee);

                    heap.remove(ee);
                    ee.edge = iter.getEdge();
                    ee.weight = tmpWeight;
                    ee.parent = currEdge;
                    heap.add(ee);

                    changedIds.add(traversalId);
                }

            }

            if (heap.isEmpty() || visitedNodes.size() >= limitVisitedNodes || isWeightLimitReached())
                return NOT_FOUND_EE;

            currEdge = heap.peek();
            if(finished())
                return currEdge;

            heap.poll();
        }

    }

    // Update reachedNodes for getting from cache by node
    private void updateReached(EdgeEntry ee)
    {
        EdgeEntry previous = reachedNodes[ee.adjNode];
        if(previous == null)
        {
            reachedNodes[ee.adjNode] = ee;
            changedNodes.add(ee.adjNode);
        } else if(previous.weight > ee.weight)
        {
            reachedNodes[ee.adjNode] = ee;
        }

    }

    @Override
    protected boolean finished()
    {
        return currEdge.adjNode == to;
    }

    private boolean isWeightLimitReached()
    {
        return currEdge.weight >= weightLimit;
    }

    @Override
    protected Path extractPath()
    {
        EdgeEntry ee = reachedNodes[to];
        Path path = new Path(graph, flagEncoder);
        if(ee != null)
            path.setEdgeEntry(ee).setWeight(ee.weight).extract();
        return path;
    }

    @Override
    public Path calcPath(int from, int to)
    {
        findEE(from, to);
        return extractPath();
    }

    @Override
    public int getVisitedNodes()
    {
        return visitedNodes.size();
    }

    public String getMemoryUsageAsString()
    {
        return "NOT IMPLEMENTED";
    }

    public void clear()
    {
        doClear = true;
    }

    private void lazyClear()
    {
        for(int i = 0; i < changedIds.size(); i++)
        {
            int index = changedIds.get(i);
            weights.set(index, null);
        }
        for(int i =0; i < changedNodes.size(); i++)
        {
            int index = changedNodes.get(i);
            reachedNodes[index] = null;
        }
        changedNodes.reset();
        changedIds.reset();
        heap.clear();

        doClear = false;
    }

    //TODO: maybe we should clear pointer "prepareAlgo" in PrepareContracionHierchies?
    public void close()
    {
        weights = null;
        reachedNodes = null;
        heap = null;
    }

    private static class TIntArrayListWithCap extends TIntArrayList
    {
        public int getCapacity()
        {
            return _data.length;
        }
    }

    private static class AutoVector<E> extends Vector<E>
    {
        public AutoVector(int initialCapacity)
        {
            super(initialCapacity);
        }

        public AutoVector(int initialCapacity, int capacityIncrement)
        {
            super(initialCapacity, capacityIncrement);
        }

        @Override
        public synchronized E get(int index)
        {
            if(index >= size())
                setSize(index + 1);
            return super.get(index);
        }


        @Override
        public synchronized E set(int index, E element)
        {
            if(index >= size())
                setSize(index + 1);
            return super.set(index, element);
        }
    }

}
