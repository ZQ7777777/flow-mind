package com.flowmind.platform.core.validation;

import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessEdgeDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.enums.NodeTypeEnum;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 发布校验和运行时推进期间使用的只读流程图索引。
 *
 * <p>索引只整理正式 DTO 的节点和连线集合，不复制领域模型，也不承担持久化或运行时加载职责。</p>
 *
 * @author FlowMind
 * @since 2026-07-20
 */
public final class DefinitionGraphIndex {

    /** 节点稳定排序规则。 */
    private static final Comparator<ProcessNodeDTO> NODE_ORDER = new Comparator<ProcessNodeDTO>() {
        /**
         * 按运行时和校验共用的稳定规则比较节点。
         *
         * @param left 左侧节点
         * @param right 右侧节点
         * @return 排序比较结果
         */
        @Override
        public int compare(ProcessNodeDTO left, ProcessNodeDTO right) {
            int order = compareNullableInteger(left.getSortOrder(), right.getSortOrder());
            if (order != 0) {
                return order;
            }
            order = compareNullableString(left.getNodeCode(), right.getNodeCode());
            if (order != 0) {
                return order;
            }
            order = compareNullableString(nodeTypeName(left), nodeTypeName(right));
            if (order != 0) {
                return order;
            }
            order = compareNullableString(left.getPairedGatewayCode(), right.getPairedGatewayCode());
            if (order != 0) {
                return order;
            }
            order = compareNullableString(approverRuleTypeName(left), approverRuleTypeName(right));
            return order != 0 ? order
                    : compareNullableString(left.getApproverRuleConfig(), right.getApproverRuleConfig());
        }
    };
    /** 连线稳定排序规则。 */
    private static final Comparator<ProcessEdgeDTO> EDGE_ORDER = new Comparator<ProcessEdgeDTO>() {
        /**
         * 按运行时和校验共用的稳定规则比较连线。
         *
         * @param left 左侧连线
         * @param right 右侧连线
         * @return 排序比较结果
         */
        @Override
        public int compare(ProcessEdgeDTO left, ProcessEdgeDTO right) {
            int order = compareNullableInteger(left.getSortOrder(), right.getSortOrder());
            if (order != 0) {
                return order;
            }
            order = compareNullableString(left.getEdgeCode(), right.getEdgeCode());
            if (order != 0) {
                return order;
            }
            order = compareNullableString(left.getSourceNodeCode(), right.getSourceNodeCode());
            if (order != 0) {
                return order;
            }
            order = compareNullableString(left.getTargetNodeCode(), right.getTargetNodeCode());
            if (order != 0) {
                return order;
            }
            order = compareNullableBoolean(left.getDefaultEdge(), right.getDefaultEdge());
            return order != 0 ? order
                    : compareNullableString(left.getConditionExpression(), right.getConditionExpression());
        }
    };

    /** 稳定排序后的非空节点。 */
    private final List<ProcessNodeDTO> nodes;
    /** 稳定排序后的非空连线。 */
    private final List<ProcessEdgeDTO> edges;
    /** 按节点编码读取的节点索引。 */
    private final Map<String, ProcessNodeDTO> nodesByCode;
    /** 按连线编码读取的连线索引。 */
    private final Map<String, ProcessEdgeDTO> edgesByCode;
    /** 按来源节点读取的出线。 */
    private final Map<String, List<ProcessEdgeDTO>> outgoingEdges;
    /** 按目标节点读取的入线。 */
    private final Map<String, List<ProcessEdgeDTO>> incomingEdges;
    /** 原节点列表中的空元素数量。 */
    private final int nullNodeCount;
    /** 原连线列表中的空元素数量。 */
    private final int nullEdgeCount;

    /**
     * 基于节点和连线集合创建只读索引。
     *
     * @param sourceNodes 原始节点集合
     * @param sourceEdges 原始连线集合
     */
    private DefinitionGraphIndex(List<ProcessNodeDTO> sourceNodes,
                                 List<ProcessEdgeDTO> sourceEdges) {
        NodeCollection nodeCollection = sortNodes(sourceNodes);
        EdgeCollection edgeCollection = sortEdges(sourceEdges);
        this.nodes = nodeCollection.nodes;
        this.edges = edgeCollection.edges;
        this.nullNodeCount = nodeCollection.nullElementCount;
        this.nullEdgeCount = edgeCollection.nullElementCount;
        this.nodesByCode = indexNodes(nodes);
        this.edgesByCode = indexEdges(edges);
        this.outgoingEdges = indexEdgesByNode(edges, nodesByCode, true);
        this.incomingEdges = indexEdgesByNode(edges, nodesByCode, false);
    }

    /**
     * 从正式流程定义详情构建校验索引。
     *
     * @param definition 流程定义详情，可为空
     * @return 只读流程图索引
     */
    public static DefinitionGraphIndex from(ProcessDefinitionDetailDTO definition) {
        return new DefinitionGraphIndex(definition == null ? null : definition.getNodes(),
                definition == null ? null : definition.getEdges());
    }

    /**
     * 获取稳定排序后的节点视图。
     *
     * @return 非空节点列表
     */
    public List<ProcessNodeDTO> getNodes() {
        return nodes;
    }

    /**
     * 获取稳定排序后的连线视图。
     *
     * @return 非空连线列表
     */
    public List<ProcessEdgeDTO> getEdges() {
        return edges;
    }

    /**
     * 获取节点编码索引。
     *
     * @return 节点编码到节点的只读映射
     */
    public Map<String, ProcessNodeDTO> getNodesByCode() {
        return nodesByCode;
    }

    /**
     * 获取连线编码索引。
     *
     * @return 连线编码到连线的只读映射
     */
    public Map<String, ProcessEdgeDTO> getEdgesByCode() {
        return edgesByCode;
    }

    /**
     * 按节点编码读取节点。
     *
     * @param nodeCode 节点编码
     * @return 节点；不存在时返回 null
     */
    public ProcessNodeDTO getNode(String nodeCode) {
        return nodesByCode.get(nodeCode);
    }

    /**
     * 按连线编码读取连线。
     *
     * @param edgeCode 连线编码
     * @return 连线；不存在时返回 null
     */
    public ProcessEdgeDTO getEdge(String edgeCode) {
        return edgesByCode.get(edgeCode);
    }

    /**
     * 获取指定节点的出线。
     *
     * @param nodeCode 节点编码
     * @return 稳定排序后的出线列表
     */
    public List<ProcessEdgeDTO> getOutgoingEdges(String nodeCode) {
        return edgesOf(outgoingEdges, nodeCode);
    }

    /**
     * 获取指定节点的入线。
     *
     * @param nodeCode 节点编码
     * @return 稳定排序后的入线列表
     */
    public List<ProcessEdgeDTO> getIncomingEdges(String nodeCode) {
        return edgesOf(incomingEdges, nodeCode);
    }

    /**
     * 获取开始节点视图。
     *
     * @return 开始节点列表
     */
    public List<ProcessNodeDTO> getStartNodes() {
        return nodesOfType(NodeTypeEnum.START);
    }

    /**
     * 获取结束节点视图。
     *
     * @return 结束节点列表
     */
    public List<ProcessNodeDTO> getEndNodes() {
        return nodesOfType(NodeTypeEnum.END);
    }

    /**
     * 获取网关节点视图。
     *
     * @return 排他和并行网关节点列表
     */
    public List<ProcessNodeDTO> getGatewayNodes() {
        List<ProcessNodeDTO> gateways = new ArrayList<ProcessNodeDTO>();
        for (ProcessNodeDTO node : nodes) {
            if (NodeTypeEnum.EXCLUSIVE_GATEWAY.equals(node.getNodeType())
                    || NodeTypeEnum.PARALLEL_SPLIT_GATEWAY.equals(node.getNodeType())
                    || NodeTypeEnum.PARALLEL_JOIN_GATEWAY.equals(node.getNodeType())) {
                gateways.add(node);
            }
        }
        return Collections.unmodifiableList(gateways);
    }

    /**
     * 获取原节点列表中的空元素数量。
     *
     * @return 空节点数量
     */
    public int getNullNodeCount() {
        return nullNodeCount;
    }

    /**
     * 获取原连线列表中的空元素数量。
     *
     * @return 空连线数量
     */
    public int getNullEdgeCount() {
        return nullEdgeCount;
    }

    /**
     * 按节点类型筛选稳定排序后的节点。
     *
     * @param nodeType 节点类型
     * @return 指定类型的只读节点列表
     */
    private List<ProcessNodeDTO> nodesOfType(NodeTypeEnum nodeType) {
        List<ProcessNodeDTO> matched = new ArrayList<ProcessNodeDTO>();
        for (ProcessNodeDTO node : nodes) {
            if (nodeType.equals(node.getNodeType())) {
                matched.add(node);
            }
        }
        return Collections.unmodifiableList(matched);
    }

    /**
     * 过滤空节点并按稳定规则排序。
     *
     * @param source 原始节点集合
     * @return 排序结果和空元素数量
     */
    private static NodeCollection sortNodes(List<ProcessNodeDTO> source) {
        List<ProcessNodeDTO> copied = new ArrayList<ProcessNodeDTO>();
        int nullCount = 0;
        if (source != null) {
            for (ProcessNodeDTO node : source) {
                if (node == null) {
                    nullCount++;
                } else {
                    copied.add(node);
                }
            }
        }
        Collections.sort(copied, NODE_ORDER);
        return new NodeCollection(Collections.unmodifiableList(copied), nullCount);
    }

    /**
     * 过滤空连线并按稳定规则排序。
     *
     * @param source 原始连线集合
     * @return 排序结果和空元素数量
     */
    private static EdgeCollection sortEdges(List<ProcessEdgeDTO> source) {
        List<ProcessEdgeDTO> copied = new ArrayList<ProcessEdgeDTO>();
        int nullCount = 0;
        if (source != null) {
            for (ProcessEdgeDTO edge : source) {
                if (edge == null) {
                    nullCount++;
                } else {
                    copied.add(edge);
                }
            }
        }
        Collections.sort(copied, EDGE_ORDER);
        return new EdgeCollection(Collections.unmodifiableList(copied), nullCount);
    }

    /**
     * 按节点编码构建只读节点索引。
     *
     * @param nodes 稳定排序后的节点集合
     * @return 节点编码到节点的只读映射
     */
    private static Map<String, ProcessNodeDTO> indexNodes(List<ProcessNodeDTO> nodes) {
        Map<String, ProcessNodeDTO> indexed = new LinkedHashMap<String, ProcessNodeDTO>();
        for (ProcessNodeDTO node : nodes) {
            if (!isBlank(node.getNodeCode())) {
                indexed.put(node.getNodeCode(), node);
            }
        }
        return Collections.unmodifiableMap(indexed);
    }

    /**
     * 按连线编码构建只读连线索引。
     *
     * @param edges 稳定排序后的连线集合
     * @return 连线编码到连线的只读映射
     */
    private static Map<String, ProcessEdgeDTO> indexEdges(List<ProcessEdgeDTO> edges) {
        Map<String, ProcessEdgeDTO> indexed = new LinkedHashMap<String, ProcessEdgeDTO>();
        for (ProcessEdgeDTO edge : edges) {
            if (!isBlank(edge.getEdgeCode())) {
                indexed.put(edge.getEdgeCode(), edge);
            }
        }
        return Collections.unmodifiableMap(indexed);
    }

    /**
     * 按来源或目标节点构建连线邻接表。
     *
     * @param edges 稳定排序后的连线集合
     * @param nodesByCode 节点编码索引
     * @param outgoing true 表示按来源节点索引，false 表示按目标节点索引
     * @return 节点编码到只读连线列表的只读映射
     */
    private static Map<String, List<ProcessEdgeDTO>> indexEdgesByNode(
            List<ProcessEdgeDTO> edges,
            Map<String, ProcessNodeDTO> nodesByCode,
            boolean outgoing) {
        Map<String, List<ProcessEdgeDTO>> indexed = new LinkedHashMap<String, List<ProcessEdgeDTO>>();
        for (ProcessEdgeDTO edge : edges) {
            String nodeCode = outgoing ? edge.getSourceNodeCode() : edge.getTargetNodeCode();
            if (!nodesByCode.containsKey(nodeCode)) {
                continue;
            }
            List<ProcessEdgeDTO> nodeEdges = indexed.get(nodeCode);
            if (nodeEdges == null) {
                nodeEdges = new ArrayList<ProcessEdgeDTO>();
                indexed.put(nodeCode, nodeEdges);
            }
            nodeEdges.add(edge);
        }
        Map<String, List<ProcessEdgeDTO>> immutable = new LinkedHashMap<String, List<ProcessEdgeDTO>>();
        for (Map.Entry<String, List<ProcessEdgeDTO>> entry : indexed.entrySet()) {
            immutable.put(entry.getKey(), Collections.unmodifiableList(
                    new ArrayList<ProcessEdgeDTO>(entry.getValue())));
        }
        return Collections.unmodifiableMap(immutable);
    }

    /**
     * 从邻接表读取指定节点的连线列表。
     *
     * @param edgesByNode 邻接表
     * @param nodeCode 节点编码
     * @return 指定节点的连线列表，不存在时返回空列表
     */
    private static List<ProcessEdgeDTO> edgesOf(Map<String, List<ProcessEdgeDTO>> edgesByNode,
                                                String nodeCode) {
        List<ProcessEdgeDTO> nodeEdges = edgesByNode.get(nodeCode);
        return nodeEdges == null ? Collections.<ProcessEdgeDTO>emptyList() : nodeEdges;
    }

    /**
     * 比较可空整数，null 排在非 null 之后。
     *
     * @param left 左侧值
     * @param right 右侧值
     * @return 排序比较结果
     */
    private static int compareNullableInteger(Integer left, Integer right) {
        if (left == right) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return left.compareTo(right);
    }

    /**
     * 比较可空字符串，null 排在非 null 之后。
     *
     * @param left 左侧值
     * @param right 右侧值
     * @return 排序比较结果
     */
    private static int compareNullableString(String left, String right) {
        if (left == right) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return left.compareTo(right);
    }

    /**
     * 比较可空布尔值，null 排在非 null 之后。
     *
     * @param left 左侧值
     * @param right 右侧值
     * @return 排序比较结果
     */
    private static int compareNullableBoolean(Boolean left, Boolean right) {
        if (left == right) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return left.compareTo(right);
    }

    /**
     * 获取节点类型枚举名称。
     *
     * @param node 节点
     * @return 节点类型名称；节点类型为空时返回 null
     */
    private static String nodeTypeName(ProcessNodeDTO node) {
        return node.getNodeType() == null ? null : node.getNodeType().name();
    }

    /**
     * 获取审批规则类型枚举名称。
     *
     * @param node 节点
     * @return 审批规则类型名称；规则类型为空时返回 null
     */
    private static String approverRuleTypeName(ProcessNodeDTO node) {
        return node.getApproverRuleType() == null ? null : node.getApproverRuleType().name();
    }

    /**
     * 判断字符串是否为空白。
     *
     * @param value 待判断字符串
     * @return 字符串为 null 或去空格后为空时返回 true
     */
    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** 节点排序结果。 */
    private static final class NodeCollection {
        /** 排序后的节点。 */
        private final List<ProcessNodeDTO> nodes;
        /** 空元素数量。 */
        private final int nullElementCount;

        /**
         * 创建节点排序结果。
         *
         * @param nodes 排序后的节点
         * @param nullElementCount 空元素数量
         */
        private NodeCollection(List<ProcessNodeDTO> nodes, int nullElementCount) {
            this.nodes = nodes;
            this.nullElementCount = nullElementCount;
        }
    }

    /** 连线排序结果。 */
    private static final class EdgeCollection {
        /** 排序后的连线。 */
        private final List<ProcessEdgeDTO> edges;
        /** 空元素数量。 */
        private final int nullElementCount;

        /**
         * 创建连线排序结果。
         *
         * @param edges 排序后的连线
         * @param nullElementCount 空元素数量
         */
        private EdgeCollection(List<ProcessEdgeDTO> edges, int nullElementCount) {
            this.edges = edges;
            this.nullElementCount = nullElementCount;
        }
    }
}
