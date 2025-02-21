/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.sharding.distsql.handler.converter;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.shardingsphere.distsql.segment.AlgorithmSegment;
import org.apache.shardingsphere.infra.algorithm.core.config.AlgorithmConfiguration;
import org.apache.shardingsphere.infra.algorithm.core.exception.AlgorithmInitializationException;
import org.apache.shardingsphere.infra.datanode.DataNode;
import org.apache.shardingsphere.infra.datanode.DataNodeUtils;
import org.apache.shardingsphere.infra.exception.core.ShardingSpherePreconditions;
import org.apache.shardingsphere.infra.expr.core.InlineExpressionParserFactory;
import org.apache.shardingsphere.infra.spi.type.typed.TypedSPILoader;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingAutoTableRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.audit.ShardingAuditStrategyConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.keygen.KeyGenerateStrategyConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.sharding.NoneShardingStrategyConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.sharding.ShardingStrategyConfiguration;
import org.apache.shardingsphere.sharding.api.sharding.ShardingAutoTableAlgorithm;
import org.apache.shardingsphere.sharding.distsql.handler.enums.ShardingStrategyLevelType;
import org.apache.shardingsphere.sharding.distsql.handler.enums.ShardingStrategyType;
import org.apache.shardingsphere.sharding.distsql.segment.strategy.AuditStrategySegment;
import org.apache.shardingsphere.sharding.distsql.segment.strategy.KeyGenerateStrategySegment;
import org.apache.shardingsphere.sharding.distsql.segment.strategy.ShardingAuditorSegment;
import org.apache.shardingsphere.sharding.distsql.segment.strategy.ShardingStrategySegment;
import org.apache.shardingsphere.sharding.distsql.segment.table.AbstractTableRuleSegment;
import org.apache.shardingsphere.sharding.distsql.segment.table.AutoTableRuleSegment;
import org.apache.shardingsphere.sharding.distsql.segment.table.TableRuleSegment;
import org.apache.shardingsphere.sharding.spi.ShardingAlgorithm;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Sharding table rule converter.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ShardingTableRuleStatementConverter {
    
    /**
     * Convert sharding table rule segments to sharding rule configuration.
     *
     * @param rules sharding table rule statements
     * @return sharding rule configuration
     */
    public static ShardingRuleConfiguration convert(final Collection<AbstractTableRuleSegment> rules) {
        ShardingRuleConfiguration result = new ShardingRuleConfiguration();
        for (AbstractTableRuleSegment each : rules) {
            result.getKeyGenerators().putAll(createKeyGeneratorConfiguration(each));
            result.getAuditors().putAll(createAuditorConfiguration(each));
            if (each instanceof AutoTableRuleSegment) {
                result.getShardingAlgorithms().putAll(createAlgorithmConfiguration((AutoTableRuleSegment) each));
                result.getAutoTables().add(createAutoTableRuleConfiguration((AutoTableRuleSegment) each));
            }
            if (each instanceof TableRuleSegment) {
                result.getShardingAlgorithms().putAll(createAlgorithmConfiguration((TableRuleSegment) each));
                result.getTables().add(createTableRuleConfiguration((TableRuleSegment) each));
            }
        }
        return result;
    }
    
    private static Map<String, AlgorithmConfiguration> createKeyGeneratorConfiguration(final AbstractTableRuleSegment rule) {
        Map<String, AlgorithmConfiguration> result = new HashMap<>();
        Optional.ofNullable(rule.getKeyGenerateStrategySegment()).ifPresent(optional -> result.put(getKeyGeneratorName(rule.getLogicTable(), optional.getKeyGenerateAlgorithmSegment().getName()),
                createAlgorithmConfiguration(optional.getKeyGenerateAlgorithmSegment())));
        return result;
    }
    
    private static Map<String, AlgorithmConfiguration> createAuditorConfiguration(final AbstractTableRuleSegment rule) {
        Map<String, AlgorithmConfiguration> result = new HashMap<>();
        Optional.ofNullable(rule.getAuditStrategySegment()).ifPresent(optional -> {
            for (ShardingAuditorSegment each : optional.getAuditorSegments()) {
                result.put(each.getAuditorName(), new AlgorithmConfiguration(each.getAlgorithmSegment().getName(), each.getAlgorithmSegment().getProps()));
            }
        });
        return result;
    }
    
    private static Map<String, AlgorithmConfiguration> createAlgorithmConfiguration(final AutoTableRuleSegment rule) {
        Map<String, AlgorithmConfiguration> result = new HashMap<>();
        Optional.ofNullable(rule.getShardingAlgorithmSegment())
                .ifPresent(optional -> result.put(getAutoTableShardingAlgorithmName(rule.getLogicTable(), optional.getName()), createAlgorithmConfiguration(optional)));
        return result;
    }
    
    private static Map<String, AlgorithmConfiguration> createAlgorithmConfiguration(final TableRuleSegment rule) {
        Map<String, AlgorithmConfiguration> result = new HashMap<>();
        if (null != rule.getTableStrategySegment()) {
            Optional.ofNullable(rule.getTableStrategySegment().getShardingAlgorithm())
                    .ifPresent(optional -> result.put(getTableShardingAlgorithmName(rule.getLogicTable(), ShardingStrategyLevelType.TABLE, optional.getName()),
                            createAlgorithmConfiguration(optional)));
        }
        if (null != rule.getDatabaseStrategySegment()) {
            Optional.ofNullable(rule.getDatabaseStrategySegment().getShardingAlgorithm())
                    .ifPresent(optional -> result.put(getTableShardingAlgorithmName(rule.getLogicTable(), ShardingStrategyLevelType.DATABASE, optional.getName()),
                            createAlgorithmConfiguration(optional)));
        }
        return result;
    }
    
    /**
     * Create algorithm configuration.
     *
     * @param segment algorithm segment
     * @return ShardingSphere algorithm configuration
     */
    public static AlgorithmConfiguration createAlgorithmConfiguration(final AlgorithmSegment segment) {
        return new AlgorithmConfiguration(segment.getName().toLowerCase(), segment.getProps());
    }
    
    private static ShardingAutoTableRuleConfiguration createAutoTableRuleConfiguration(final AutoTableRuleSegment rule) {
        ShardingAutoTableRuleConfiguration result = new ShardingAutoTableRuleConfiguration(rule.getLogicTable(), String.join(",", rule.getDataSourceNodes()));
        result.setShardingStrategy(createAutoTableStrategyConfiguration(rule));
        Optional.ofNullable(rule.getKeyGenerateStrategySegment())
                .ifPresent(optional -> result.setKeyGenerateStrategy(createKeyGenerateStrategyConfiguration(rule.getLogicTable(), rule.getKeyGenerateStrategySegment())));
        Optional.ofNullable(rule.getAuditStrategySegment())
                .ifPresent(optional -> result.setAuditStrategy(createShardingAuditStrategyConfiguration(rule.getAuditStrategySegment())));
        return result;
    }
    
    private static ShardingStrategyConfiguration createAutoTableStrategyConfiguration(final AutoTableRuleSegment rule) {
        return createStrategyConfiguration(ShardingStrategyType.STANDARD.name(),
                rule.getShardingColumn(), getAutoTableShardingAlgorithmName(rule.getLogicTable(), rule.getShardingAlgorithmSegment().getName()));
    }
    
    private static ShardingTableRuleConfiguration createTableRuleConfiguration(final TableRuleSegment tableRuleSegment) {
        String dataSourceNodes = String.join(",", tableRuleSegment.getDataSourceNodes());
        ShardingTableRuleConfiguration result = new ShardingTableRuleConfiguration(tableRuleSegment.getLogicTable(), dataSourceNodes);
        Optional.ofNullable(tableRuleSegment.getTableStrategySegment())
                .ifPresent(optional -> result.setTableShardingStrategy(createShardingStrategyConfiguration(tableRuleSegment.getLogicTable(),
                        ShardingStrategyLevelType.TABLE, optional.getType(), optional)));
        Optional.ofNullable(tableRuleSegment.getDatabaseStrategySegment())
                .ifPresent(optional -> result.setDatabaseShardingStrategy(createShardingStrategyConfiguration(tableRuleSegment.getLogicTable(),
                        ShardingStrategyLevelType.DATABASE, optional.getType(), optional)));
        Optional.ofNullable(tableRuleSegment.getKeyGenerateStrategySegment())
                .ifPresent(optional -> result.setKeyGenerateStrategy(createKeyGenerateStrategyConfiguration(tableRuleSegment.getLogicTable(), optional)));
        Optional.ofNullable(tableRuleSegment.getAuditStrategySegment())
                .ifPresent(optional -> result.setAuditStrategy(createShardingAuditStrategyConfiguration(optional)));
        return result;
    }
    
    private static ShardingStrategyConfiguration createShardingStrategyConfiguration(final String logicTable, final ShardingStrategyLevelType strategyLevel, final String type,
                                                                                     final ShardingStrategySegment segment) {
        if ("none".equalsIgnoreCase(type)) {
            return new NoneShardingStrategyConfiguration();
        }
        String shardingAlgorithmName = getTableShardingAlgorithmName(logicTable, strategyLevel, segment.getShardingAlgorithm().getName());
        return createStrategyConfiguration(ShardingStrategyType.getValueOf(type).name(), segment.getShardingColumn(), shardingAlgorithmName);
    }
    
    private static KeyGenerateStrategyConfiguration createKeyGenerateStrategyConfiguration(final String logicTable, final KeyGenerateStrategySegment segment) {
        return new KeyGenerateStrategyConfiguration(segment.getKeyGenerateColumn(), getKeyGeneratorName(logicTable, segment.getKeyGenerateAlgorithmSegment().getName()));
    }
    
    private static ShardingAuditStrategyConfiguration createShardingAuditStrategyConfiguration(final AuditStrategySegment segment) {
        Collection<String> auditorNames = segment.getAuditorSegments().stream().map(ShardingAuditorSegment::getAuditorName).collect(Collectors.toList());
        return new ShardingAuditStrategyConfiguration(auditorNames, segment.isAllowHintDisable());
    }
    
    /**
     * Create strategy configuration.
     *
     * @param strategyType strategy type
     * @param shardingColumn sharding column
     * @param shardingAlgorithmName sharding algorithm name
     * @return sharding strategy configuration
     */
    public static ShardingStrategyConfiguration createStrategyConfiguration(final String strategyType, final String shardingColumn, final String shardingAlgorithmName) {
        ShardingStrategyType shardingStrategyType = ShardingStrategyType.getValueOf(strategyType);
        return shardingStrategyType.createConfiguration(shardingAlgorithmName, shardingColumn);
    }
    
    private static String getAutoTableShardingAlgorithmName(final String tableName, final String algorithmType) {
        return String.format("%s_%s", tableName, algorithmType).toLowerCase();
    }
    
    private static String getTableShardingAlgorithmName(final String tableName, final ShardingStrategyLevelType strategyLevel, final String algorithmType) {
        return String.format("%s_%s_%s", tableName, strategyLevel.name(), algorithmType).toLowerCase();
    }
    
    private static String getKeyGeneratorName(final String tableName, final String algorithmType) {
        return String.format("%s_%s", tableName, algorithmType).toLowerCase();
    }
    
    /**
     * Convert rule segments to data nodes.
     *
     * @param segments sharding table rule segments
     * @return data nodes map
     */
    public static Map<String, Collection<DataNode>> convertDataNodes(final Collection<AbstractTableRuleSegment> segments) {
        Map<String, Collection<DataNode>> result = new HashMap<>(segments.size(), 1F);
        for (AbstractTableRuleSegment each : segments) {
            if (each instanceof TableRuleSegment) {
                result.put(each.getLogicTable(), getActualDataNodes((TableRuleSegment) each));
                continue;
            }
            result.put(each.getLogicTable(), getActualDataNodes((AutoTableRuleSegment) each));
        }
        return result;
    }
    
    /**
     * Get actual data nodes for sharding table rule segment.
     *
     * @param ruleSegment sharding table rule segment
     * @return data nodes
     */
    public static Collection<DataNode> getActualDataNodes(final TableRuleSegment ruleSegment) {
        Collection<DataNode> result = new LinkedList<>();
        for (String each : ruleSegment.getDataSourceNodes()) {
            List<String> dataNodes = InlineExpressionParserFactory.newInstance(each).splitAndEvaluate();
            result.addAll(dataNodes.stream().map(DataNode::new).collect(Collectors.toList()));
        }
        return result;
    }
    
    /**
     * Get actual data nodes for auto sharding table rule segment.
     *
     * @param ruleSegment auto sharding table rule segment
     * @return data nodes
     */
    public static Collection<DataNode> getActualDataNodes(final AutoTableRuleSegment ruleSegment) {
        ShardingAlgorithm shardingAlgorithm =
                TypedSPILoader.getService(ShardingAlgorithm.class, ruleSegment.getShardingAlgorithmSegment().getName(), ruleSegment.getShardingAlgorithmSegment().getProps());
        ShardingSpherePreconditions.checkState(shardingAlgorithm instanceof ShardingAutoTableAlgorithm,
                () -> new AlgorithmInitializationException(shardingAlgorithm, "Auto sharding algorithm is required for table '%s'", ruleSegment.getLogicTable()));
        List<String> dataNodes = DataNodeUtils.getFormatDataNodes(((ShardingAutoTableAlgorithm) shardingAlgorithm).getAutoTablesAmount(),
                ruleSegment.getLogicTable(), ruleSegment.getDataSourceNodes());
        return dataNodes.stream().map(DataNode::new).collect(Collectors.toList());
    }
}
