package com.graphhopper.routing.util;

public interface WeightingWrapper extends Weighting
{
    /**
     * Get basic weighting
     * @return Weighting
     */
    Weighting getWrappedWeighting();
}
