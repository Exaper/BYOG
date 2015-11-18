package com.exaper.byog;

import org.jgrapht.graph.DefaultWeightedEdge;

public class Edge extends DefaultWeightedEdge {
    public Vertex getSource() {
        return (Vertex) super.getSource();
    }

    public Vertex getTarget() {
        return (Vertex) super.getTarget();
    }
}
