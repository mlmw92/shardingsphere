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

package org.apache.shardingsphere.transaction.base.seata.at;

import io.seata.tm.api.GlobalTransaction;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Seata transaction holder.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SeataTransactionHolder {
    
    private static final ThreadLocal<GlobalTransaction> CONTEXT = new ThreadLocal<>();
    
    /**
     * Set seata global transaction.
     *
     * @param transaction global transaction context
     */
    public static void set(final GlobalTransaction transaction) {
        CONTEXT.set(transaction);
    }
    
    /**
     * Get seata global transaction.
     *
     * @return global transaction
     */
    public static GlobalTransaction get() {
        return CONTEXT.get();
    }
    
    /**
     * Clear global transaction.
     */
    public static void clear() {
        CONTEXT.remove();
    }
}
