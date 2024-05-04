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

package org.apache.shardingsphere.mode.engine;

import com.google.common.base.Strings;
import lombok.SneakyThrows;
import org.apache.shardingsphere.infra.spi.type.typed.TypedSPILoader;
import org.apache.shardingsphere.infra.util.yaml.YamlEngine;
import org.apache.shardingsphere.infra.util.yaml.datanode.RepositoryTuple;
import org.apache.shardingsphere.infra.yaml.config.pojo.rule.YamlGlobalRuleConfiguration;
import org.apache.shardingsphere.infra.yaml.config.pojo.rule.YamlRuleConfiguration;
import org.apache.shardingsphere.infra.yaml.config.pojo.rule.annotation.RepositoryTupleEntity;
import org.apache.shardingsphere.infra.yaml.config.pojo.rule.annotation.RepositoryTupleField;
import org.apache.shardingsphere.infra.yaml.config.pojo.rule.annotation.RepositoryTupleKeyNameGenerator;
import org.apache.shardingsphere.infra.yaml.config.pojo.rule.annotation.RepositoryTupleType;
import org.apache.shardingsphere.mode.path.GlobalNodePath;
import org.apache.shardingsphere.mode.path.rule.RuleNodePath;
import org.apache.shardingsphere.mode.spi.RuleNodePathProvider;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Auto repository tuple swapper engine.
 */
public final class AutoRepositoryTupleSwapperEngine {
    
    /**
     * Swap to repository tuples.
     *
     * @param yamlRuleConfig YAML rule configuration to be swapped
     * @return repository tuples
     */
    public Collection<RepositoryTuple> swapToRepositoryTuples(final YamlRuleConfiguration yamlRuleConfig) {
        if (null == yamlRuleConfig.getClass().getAnnotation(RepositoryTupleEntity.class)) {
            return Collections.emptyList();
        }
        RepositoryTupleType tupleType = yamlRuleConfig.getClass().getAnnotation(RepositoryTupleType.class);
        if (null != tupleType) {
            return Collections.singleton(new RepositoryTuple(tupleType.value(), YamlEngine.marshal(yamlRuleConfig)));
        }
        Collection<RepositoryTuple> result = new LinkedList<>();
        RuleNodePath ruleNodePath = TypedSPILoader.getService(RuleNodePathProvider.class, yamlRuleConfig.getRuleConfigurationType()).getRuleNodePath();
        for (Field each : getFields(yamlRuleConfig.getClass())) {
            @SuppressWarnings("deprecation")
            boolean isAccessible = each.isAccessible();
            each.setAccessible(true);
            result.addAll(swapToRepositoryTuples(yamlRuleConfig, ruleNodePath, each));
            each.setAccessible(isAccessible);
        }
        return result;
    }
    
    @SneakyThrows(ReflectiveOperationException.class)
    @SuppressWarnings("rawtypes")
    private Collection<RepositoryTuple> swapToRepositoryTuples(final YamlRuleConfiguration yamlRuleConfig, final RuleNodePath ruleNodePath, final Field field) {
        Object fieldValue = field.get(yamlRuleConfig);
        if (null == fieldValue) {
            return Collections.emptyList();
        }
        RepositoryTupleField tupleField = field.getAnnotation(RepositoryTupleField.class);
        RepositoryTupleKeyNameGenerator tupleKeyNameGenerator = field.getAnnotation(RepositoryTupleKeyNameGenerator.class);
        if (null != tupleKeyNameGenerator && fieldValue instanceof Collection) {
            Collection<RepositoryTuple> result = new LinkedList<>();
            for (Object value : (Collection) fieldValue) {
                String tupleKeyName = tupleKeyNameGenerator.value().getConstructor().newInstance().generate(value);
                result.add(new RepositoryTuple(ruleNodePath.getNamedItem(tupleField.value()).getPath(tupleKeyName), value.toString()));
            }
            return result;
        }
        if (fieldValue instanceof Map) {
            Collection<RepositoryTuple> result = new LinkedList<>();
            for (Object entry : ((Map) fieldValue).entrySet()) {
                result.add(new RepositoryTuple(ruleNodePath.getNamedItem(tupleField.value()).getPath(((Entry) entry).getKey().toString()), YamlEngine.marshal(((Entry) entry).getValue())));
            }
            return result;
        }
        if (fieldValue instanceof Collection && !((Collection) fieldValue).isEmpty()) {
            return Collections.singleton(new RepositoryTuple(ruleNodePath.getUniqueItem(tupleField.value()).getPath(), YamlEngine.marshal(fieldValue)));
        }
        if (fieldValue instanceof String && !((String) fieldValue).isEmpty()) {
            return Collections.singleton(new RepositoryTuple(ruleNodePath.getUniqueItem(tupleField.value()).getPath(), fieldValue.toString()));
        }
        if (fieldValue instanceof Boolean || fieldValue instanceof Integer || fieldValue instanceof Long) {
            return Collections.singleton(new RepositoryTuple(ruleNodePath.getUniqueItem(tupleField.value()).getPath(), fieldValue.toString()));
        }
        return Collections.singleton(new RepositoryTuple(ruleNodePath.getUniqueItem(tupleField.value()).getPath(), YamlEngine.marshal(fieldValue)));
    }
    
    private Collection<Field> getFields(final Class<? extends YamlRuleConfiguration> yamlRuleConfigurationClass) {
        return Arrays.stream(yamlRuleConfigurationClass.getDeclaredFields())
                .filter(each -> null != each.getAnnotation(RepositoryTupleField.class))
                .sorted(Comparator.comparingInt(o -> o.getAnnotation(RepositoryTupleField.class).order())).collect(Collectors.toList());
    }
    
    /**
     * Swap from repository tuple to YAML rule configurations.
     *
     * @param repositoryTuples repository tuples
     * @param yamlRuleConfigurationClass YAML rule configuration class
     * @return swapped YAML rule configurations
     */
    public Optional<YamlRuleConfiguration> swapToObject(final Collection<RepositoryTuple> repositoryTuples, final Class<? extends YamlRuleConfiguration> yamlRuleConfigurationClass) {
        if (null == yamlRuleConfigurationClass.getAnnotation(RepositoryTupleEntity.class)) {
            return Optional.empty();
        }
        RepositoryTupleType tupleType = yamlRuleConfigurationClass.getAnnotation(RepositoryTupleType.class);
        return null == tupleType
                ? swapToObject(repositoryTuples, yamlRuleConfigurationClass, getFields(yamlRuleConfigurationClass))
                : swapToObject(repositoryTuples, yamlRuleConfigurationClass, tupleType);
    }
    
    @SneakyThrows(ReflectiveOperationException.class)
    private Optional<YamlRuleConfiguration> swapToObject(final Collection<RepositoryTuple> repositoryTuples,
                                                         final Class<? extends YamlRuleConfiguration> yamlRuleConfigurationClass, final RepositoryTupleType tupleType) {
        if (YamlGlobalRuleConfiguration.class.isAssignableFrom(yamlRuleConfigurationClass)) {
            for (RepositoryTuple each : repositoryTuples) {
                if (GlobalNodePath.getVersion(tupleType.value(), each.getKey()).isPresent()) {
                    return Optional.of(YamlEngine.unmarshal(each.getValue(), yamlRuleConfigurationClass));
                }
            }
            return Optional.empty();
        }
        YamlRuleConfiguration yamlRuleConfig = yamlRuleConfigurationClass.getConstructor().newInstance();
        RuleNodePath ruleNodePath = TypedSPILoader.getService(RuleNodePathProvider.class, yamlRuleConfig.getRuleConfigurationType()).getRuleNodePath();
        for (RepositoryTuple each : repositoryTuples.stream().filter(each -> ruleNodePath.getRoot().isValidatedPath(each.getKey())).collect(Collectors.toList())) {
            if (ruleNodePath.getUniqueItem(tupleType.value()).isValidatedPath(each.getKey())) {
                return Optional.of(YamlEngine.unmarshal(each.getValue(), yamlRuleConfigurationClass));
            }
        }
        return Optional.empty();
    }
    
    @SneakyThrows(ReflectiveOperationException.class)
    private Optional<YamlRuleConfiguration> swapToObject(final Collection<RepositoryTuple> repositoryTuples,
                                                         final Class<? extends YamlRuleConfiguration> yamlRuleConfigurationClass, final Collection<Field> fields) {
        YamlRuleConfiguration yamlRuleConfig = yamlRuleConfigurationClass.getConstructor().newInstance();
        RuleNodePath ruleNodePath = TypedSPILoader.getService(RuleNodePathProvider.class, yamlRuleConfig.getRuleConfigurationType()).getRuleNodePath();
        List<RepositoryTuple> validTuples = repositoryTuples.stream().filter(each -> ruleNodePath.getRoot().isValidatedPath(each.getKey())).collect(Collectors.toList());
        if (validTuples.isEmpty()) {
            return Optional.empty();
        }
        for (RepositoryTuple each : validTuples) {
            if (!Strings.isNullOrEmpty(each.getValue())) {
                setFieldValue(yamlRuleConfig, fields, ruleNodePath, each);
            }
        }
        return Optional.of(yamlRuleConfig);
    }
    
    @SneakyThrows(ReflectiveOperationException.class)
    private void setFieldValue(final YamlRuleConfiguration yamlRuleConfig, final Collection<Field> fields, final RuleNodePath ruleNodePath, final RepositoryTuple repositoryTuple) {
        for (Field each : fields) {
            @SuppressWarnings("deprecation")
            boolean isAccessible = each.isAccessible();
            each.setAccessible(true);
            setFieldValue(yamlRuleConfig, each, ruleNodePath, repositoryTuple);
            each.setAccessible(isAccessible);
        }
    }
    
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void setFieldValue(final YamlRuleConfiguration yamlRuleConfig, final Field field, final RuleNodePath ruleNodePath, final RepositoryTuple repositoryTuple) throws IllegalAccessException {
        Object fieldValue = field.get(yamlRuleConfig);
        RepositoryTupleField tupleField = field.getAnnotation(RepositoryTupleField.class);
        RepositoryTupleKeyNameGenerator tupleKeyNameGenerator = field.getAnnotation(RepositoryTupleKeyNameGenerator.class);
        if (null != tupleKeyNameGenerator && fieldValue instanceof Collection) {
            ruleNodePath.getNamedItem(tupleField.value()).getName(repositoryTuple.getKey()).ifPresent(optional -> ((Collection) fieldValue).add(repositoryTuple.getValue()));
            return;
        }
        if (fieldValue instanceof Map) {
            Class<?> valueClass = (Class) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[1];
            ruleNodePath.getNamedItem(tupleField.value()).getName(repositoryTuple.getKey())
                    .ifPresent(optional -> ((Map) fieldValue).put(optional, YamlEngine.unmarshal(repositoryTuple.getValue(), valueClass)));
            return;
        }
        if (fieldValue instanceof Collection) {
            if (ruleNodePath.getUniqueItem(tupleField.value()).isValidatedPath(repositoryTuple.getKey())) {
                field.set(yamlRuleConfig, YamlEngine.unmarshal(repositoryTuple.getValue(), List.class));
            }
            return;
        }
        if (field.getType().equals(String.class)) {
            if (ruleNodePath.getUniqueItem(tupleField.value()).isValidatedPath(repositoryTuple.getKey())) {
                field.set(yamlRuleConfig, repositoryTuple.getValue());
            }
            return;
        }
        if (field.getType().equals(boolean.class) || field.getType().equals(Boolean.class)) {
            if (ruleNodePath.getUniqueItem(tupleField.value()).isValidatedPath(repositoryTuple.getKey())) {
                field.set(yamlRuleConfig, Boolean.parseBoolean(repositoryTuple.getValue()));
            }
            return;
        }
        if (field.getType().equals(int.class) || field.getType().equals(Integer.class)) {
            if (ruleNodePath.getUniqueItem(tupleField.value()).isValidatedPath(repositoryTuple.getKey())) {
                field.set(yamlRuleConfig, Integer.parseInt(repositoryTuple.getValue()));
            }
            return;
        }
        if (field.getType().equals(long.class) || field.getType().equals(Long.class)) {
            if (ruleNodePath.getUniqueItem(tupleField.value()).isValidatedPath(repositoryTuple.getKey())) {
                field.set(yamlRuleConfig, Long.parseLong(repositoryTuple.getValue()));
            }
            return;
        }
        if (ruleNodePath.getUniqueItem(tupleField.value()).isValidatedPath(repositoryTuple.getKey())) {
            field.set(yamlRuleConfig, YamlEngine.unmarshal(repositoryTuple.getValue(), field.getType()));
        }
    }
}
