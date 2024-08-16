package org.example;

import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        try {
            Graph graph = new Graph(6);

            graph.addEdge(0, 1, 2);
            graph.addEdge(0, 2, 4);
            graph.addEdge(1, 2, 1);
            graph.addEdge(1, 3, 7);
            graph.addEdge(2, 4, 3);
            graph.addEdge(3, 4, 1);
            graph.addEdge(3, 5, 3);
            graph.addEdge(4, 5, 1);

            graph.dijkstra(0, 5, "log.txt");
        }catch (IOException e){
            e.printStackTrace();
        }
    }
}
