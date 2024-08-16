package org.example;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class Graph {
    private final int vertices;
    private final List<List<Node>> adj;

    static class Node implements Comparator<Node> {
        public int vertex;
        public int weight;

        public Node() {}

        public Node(int vertex, int weight) {
            this.vertex = vertex;
            this.weight = weight;
        }

        @Override
        public int compare(Node node1, Node node2) {
            return Integer.compare(node1.weight, node2.weight);
        }
    }

    public Graph(int vertices) {
        this.vertices = vertices;
        adj = new ArrayList<>(vertices);
        for (int i = 0; i < vertices; i++) {
            adj.add(new LinkedList<>());
        }
    }

    public void addEdge(int source, int destination, int weight) {
        adj.get(source).add(new Node(destination, weight));
    }

    public void dijkstra(int startVertex, int endVertex, String logFileName) throws IOException {
        PriorityQueue<Node> pq = new PriorityQueue<>(vertices, new Node());
        int[] distances = new int[vertices];
        boolean[] visited = new boolean[vertices];
        int[] parents = new int[vertices];

        Arrays.fill(distances, Integer.MAX_VALUE);
        Arrays.fill(parents, -1);
        pq.add(new Node(startVertex, 0));
        distances[startVertex] = 0;

        while (!pq.isEmpty()) {
            int currentVertex = pq.poll().vertex;

            if (visited[currentVertex]) continue;
            visited[currentVertex] = true;

            for (Node neighbor : adj.get(currentVertex)) {
                int neighborVertex = neighbor.vertex;
                int newDist = distances[currentVertex] + neighbor.weight;

                if (newDist < distances[neighborVertex]) {
                    distances[neighborVertex] = newDist;
                    pq.add(new Node(neighborVertex, newDist));
                    parents[neighborVertex] = currentVertex;
                }
            }
        }

        // Записываем результат в лог файл
        FileWriter logFile = new FileWriter(logFileName);
        logFile.write("Кратчайший путь из вершины " + (startVertex + 1) + " до вершины " + (endVertex + 1) + ": " + distances[endVertex] + "\n");
        logFile.write("Путь: ");
        printPath(parents, endVertex, logFile);
        logFile.close();
    }

    private void printPath(int[] parents, int vertex, FileWriter logFile) throws IOException {
        if (vertex == -1) return;
        printPath(parents, parents[vertex], logFile);
        logFile.write((vertex + 1) + " ");
    }
}
