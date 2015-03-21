package com.graphhopper.routing;

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeIterator;
import gnu.trove.list.array.TIntArrayList;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.PriorityQueue;
import java.util.StringTokenizer;

public class DijkstraOneToManyTraversal extends AbstractRoutingAlgorithm
{
    public static final EdgeEntry NOT_FOUND_EE = new EdgeEntry(-1, -1, 0);
    private int limitVisitedNodes = Integer.MAX_VALUE;

    private AutoResizableArray<EdgeEntry> weights;
    private EdgeEntry[] reachedNodes;
    private PriorityQueue<EdgeEntry> heap;

    private final TIntArrayListWithCap changedIds;
    private final TIntArrayListWithCap changedNodes;

    private EdgeEntry currEdge;
    private int visitedNodes;
    private int to;

    public DijkstraOneToManyTraversal(Graph graph, FlagEncoder encoder, Weighting weighting, TraversalMode trMode)
    {
        super(graph, encoder, weighting, trMode);

        int capacity = matchCapacity();

        weights = new AutoResizableArray<EdgeEntry>(capacity + 1);
        heap = new PriorityQueue<EdgeEntry>(1000);
        reachedNodes = new EdgeEntry[graph.getNodes()];
        
        changedIds = new TIntArrayListWithCap();
        changedNodes = new TIntArrayListWithCap();
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

        if(!changedNodes.isEmpty())
        {
            EdgeEntry entry = reachedNodes[to];
            if(entry != null && entry.weight <= currEdge.weight)
                return entry;

            if(heap.isEmpty() || visitedNodes >= limitVisitedNodes)
                return NOT_FOUND_EE;

            currEdge = heap.poll();
        } else
        {
            currEdge = createEdgeEntry(from, 0);

            if(!traversalMode.isEdgeBased())
            {
                weights.set(from, currEdge);
                reachedNodes[from] = currEdge;
                changedNodes.add(from);
                changedIds.add(from);
            }
        }

        visitedNodes = 0;
        if(finished())
            return currEdge;

        while(true)
        {
            visitedNodes++;
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
                    reachedNodes[iter.getAdjNode()] = ee;
                    heap.add(ee);

                    changedIds.add(traversalId);
                    changedNodes.add(iter.getAdjNode());
                } else if (ee.weight > tmpWeight)
                {
                    heap.remove(ee);
                    ee.edge = iter.getEdge();
                    ee.adjNode = iter.getAdjNode();
                    ee.weight = tmpWeight;
                    ee.parent = currEdge;
                    heap.add(ee);

                    changedIds.add(traversalId);
                    changedNodes.add(iter.getAdjNode());
                }

            }

            if (heap.isEmpty() || visitedNodes >= limitVisitedNodes)
                return NOT_FOUND_EE;

            currEdge = heap.peek();

            if(finished())
                return currEdge;

            if(currEdge.weight > weightLimit)
                return NOT_FOUND_EE;


            heap.poll();
        }

    }

    @Override
    protected boolean finished()
    {
        return currEdge.adjNode == to;
    }

    @Override
    protected Path extractPath()
    {
        return null;
    }

    @Override
    public Path calcPath(int from, int to)
    {
        EdgeEntry endEe = findEE(from, to);
        Path path = new Path(graph, flagEncoder);
        if(endEe != NOT_FOUND_EE)
        {
            path.setEdgeEntry(endEe).setWeight(endEe.weight).extract();
        }
        return path;
    }

    @Override
    public int getVisitedNodes()
    {
        return visitedNodes;
    }

    public String getMemoryUsageAsString()
    {
        return "NOT IMPLEMENTED";
    }

    public double getWeight(int node)
    {
        EdgeEntry ee = reachedNodes[node];
        if(ee != null)
            return ee.weight;

        return Double.MAX_VALUE;
    }
    
    public void clear()
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
    }
    
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

    public static class AutoResizableArray<E> extends AbstractList<E>
    {
        private Object[]  elements;
        private int size;

        public AutoResizableArray(int initialSize)
        {
            size = initialSize;
            elements = new Object[size];
        }

        @Override
        public E get(int index)
        {
            if(index >= size)
                return null;
            return (E) elements[index];
        }

        @Override
        public int size()
        {
            return size;
        }

        @Override
        public E set(int index, E element)
        {
            if(index >= size)
            {
                size = index + 1;
                elements = Arrays.copyOf(elements, size);
            }

            Object previous = elements[index];
            elements[index] = element;
            return previous == null ? null : (E) previous;
        }

        @Override
        public boolean isEmpty()
        {
            throw new RuntimeException();
        }
    }
}
