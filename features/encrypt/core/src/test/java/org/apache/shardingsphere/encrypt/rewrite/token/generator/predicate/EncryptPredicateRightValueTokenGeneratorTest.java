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

package org.apache.shardingsphere.encrypt.rewrite.token.generator.predicate;

import org.apache.shardingsphere.encrypt.rewrite.condition.EncryptCondition;
import org.apache.shardingsphere.encrypt.rewrite.condition.EncryptConditionEngine;
import org.apache.shardingsphere.encrypt.rewrite.token.generator.fixture.EncryptGeneratorFixtureBuilder;
import org.apache.shardingsphere.infra.binder.context.statement.dml.UpdateStatementContext;
import org.apache.shardingsphere.infra.database.core.DefaultDatabase;
import org.apache.shardingsphere.infra.metadata.database.ShardingSphereDatabase;
import org.apache.shardingsphere.infra.metadata.database.schema.model.ShardingSphereSchema;
import org.apache.shardingsphere.infra.rewrite.sql.token.common.pojo.SQLToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EncryptPredicateRightValueTokenGeneratorTest {
    
    private EncryptPredicateRightValueTokenGenerator generator;
    
    @BeforeEach
    void setup() {
        generator = new EncryptPredicateRightValueTokenGenerator(EncryptGeneratorFixtureBuilder.createEncryptRule());
    }
    
    @Test
    void assertIsGenerateSQLToken() {
        generator.setDatabaseName(DefaultDatabase.LOGIC_NAME);
        assertTrue(generator.isGenerateSQLToken(EncryptGeneratorFixtureBuilder.createUpdateStatementContext()));
    }
    
    @Test
    void assertGenerateSQLTokenFromGenerateNewSQLToken() {
        UpdateStatementContext updateStatementContext = EncryptGeneratorFixtureBuilder.createUpdateStatementContext();
        generator.setDatabaseName(DefaultDatabase.LOGIC_NAME);
        generator.setEncryptConditions(getEncryptConditions(updateStatementContext));
        Collection<SQLToken> sqlTokens = generator.generateSQLTokens(updateStatementContext);
        assertThat(sqlTokens.size(), is(1));
        assertThat(sqlTokens.iterator().next().toString(), is("'assistedEncryptValue'"));
    }
    
    private Collection<EncryptCondition> getEncryptConditions(final UpdateStatementContext updateStatementContext) {
        ShardingSphereDatabase database = mock(ShardingSphereDatabase.class);
        when(database.getSchema(DefaultDatabase.LOGIC_NAME)).thenReturn(mock(ShardingSphereSchema.class));
        return new EncryptConditionEngine(EncryptGeneratorFixtureBuilder.createEncryptRule(), database)
                .createEncryptConditions(updateStatementContext.getWhereSegments(), updateStatementContext.getColumnSegments(), updateStatementContext, DefaultDatabase.LOGIC_NAME);
    }
}
