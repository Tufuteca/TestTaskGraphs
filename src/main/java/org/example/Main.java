package org.example;


import org.eclipse.milo.opcua.sdk.client.AddressSpace;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.sdk.client.model.nodes.objects.FolderTypeNode;
import org.eclipse.milo.opcua.sdk.client.nodes.UaNode;
import org.eclipse.milo.opcua.sdk.client.nodes.UaObjectNode;
import org.eclipse.milo.opcua.sdk.client.nodes.UaVariableNode;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned;
import org.eclipse.milo.opcua.stack.core.types.enumerated.*;
import org.eclipse.milo.opcua.stack.core.types.structured.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.google.common.collect.Lists.newArrayList;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection.*;
import static org.eclipse.milo.opcua.stack.core.util.ConversionUtil.toList;

public class Main {

    private static final String CONNECT = "opc.tcp://DESKTOP-T7F9Q6N:48020";

    public static void main(String[] args) {
        try {
            OpcUaClient client = OpcUaClient.create(CONNECT);

            client.connect().get();

            // Начинаем просмотр с корневого каталога
            List<UaNode> nodes = browseNodes("", client, Identifiers.RootFolder);
            writeNodesToFile(nodes, "nodes.txt");

            // Создаем одну подписку
            UaSubscription subscription = client.getSubscriptionManager().createSubscription(1000.0).get();

            // Подписываемся на изменения значений переменных
            for (UaNode node : nodes) {
                if (node instanceof UaVariableNode) {
                    UaVariableNode variableNode = (UaVariableNode) node;
                    subscribeToTag(subscription, variableNode, "values.txt");
                }
            }

            // Ожидание для демонстрации подписки
            Thread.sleep(600);

            client.disconnect().get();
        } catch (UaException | ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static List<UaNode> browseNodes(String indent, OpcUaClient client, NodeId browseRoot) throws ExecutionException, InterruptedException {
        List<UaNode> nodes = new ArrayList<>();

        BrowseDescription browse = new BrowseDescription(
                browseRoot,
                BrowseDirection.Forward,
                Identifiers.References,
                true,
                uint(NodeClass.Object.getValue() | NodeClass.Variable.getValue()),
                uint(BrowseResultMask.All.getValue())
        );

        BrowseResult browseResult = client.browse(browse).get();

        List<ReferenceDescription> references = toList(browseResult.getReferences());

        for (ReferenceDescription rd : references) {
            rd.getNodeId().toNodeId(client.getNamespaceTable())
                    .ifPresent(nodeId -> {
                        try {
                            UaNode node = client.getAddressSpace().getNode(nodeId);
                            // Добавляем только теги (переменные)
                            if (node instanceof UaVariableNode) {
                                nodes.add(node);
                            }
                            // Рекурсивно просматриваем дочерние узлы
                            nodes.addAll(browseNodes(indent + "  ", client, nodeId));
                        } catch (InterruptedException | ExecutionException e) {
                            throw new RuntimeException("Browsing nodeId=" + nodeId + " failed: " + e.getMessage(), e);
                        } catch (UaException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
        return nodes;
    }

    public static void writeNodesToFile(List<UaNode> nodes, String fileName) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            for (UaNode node : nodes) {
                writer.write(node.getNodeId().getIdentifier() + " " + node.getBrowseName().getName() + " " + node.getNodeId().getNamespaceIndex());
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Ошибка при записи в файл: " + e.getMessage());
        }
    }

    public static void writeValuesToFile(UaVariableNode variableNode, String fileName) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName, true))) {
            DataValue dataValue = variableNode.readValue();
            writer.write("Узел: " + variableNode.getBrowseName().getName() + ", Значение: " + dataValue.getValue());
            writer.newLine();
        } catch (IOException | UaException e) {
            System.err.println("Ошибка при записи значения в файл: " + e.getMessage());
        }
    }

    public static void subscribeToTag(UaSubscription subscription, UaVariableNode variableNode, String fileName) throws UaException, ExecutionException, InterruptedException {
        List<UaMonitoredItem> monitoredItems = subscription.createMonitoredItems(
                TimestampsToReturn.Both,
                newArrayList(new MonitoredItemCreateRequest(
                        new ReadValueId(variableNode.getNodeId(), AttributeId.Value.uid(), null, null),
                        MonitoringMode.Reporting,
                        new MonitoringParameters(
                                uint(1),
                                1000.0,
                                null,
                                uint(10),
                                true
                        )
                )),
                (item, id) -> item.setValueConsumer((monitoredItem, value) -> {
                    System.out.println("Значение изменилось: " + value.getValue());
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName, true))) {
                        writer.write("Узел: " + variableNode.getBrowseName().getName() + ", Новое значение: " + value.getValue());
                        writer.newLine();
                    } catch (IOException e) {
                        System.err.println("Ошибка при записи значения в файл: " + e.getMessage());
                    }
                })
        ).get();

        // Убедитесь, что все элементы подписки успешно созданы
        for (UaMonitoredItem item : monitoredItems) {
            if (item.getStatusCode().isGood()) {
                System.out.println("Подписка на тег " + variableNode.getBrowseName().getName() + " успешно создана.");
            }
        }
    }
}


