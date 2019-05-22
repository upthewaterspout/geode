/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
/*
 * PathExpressionEvaluator.java
 *
 * Created on February 11, 2005, 4:25 PM
 */
package org.apache.geode.cache.query.internal.index;

import java.util.List;

import org.apache.geode.cache.query.types.ObjectType;

public interface IndexedExpressionEvaluator {

  String getIndexedExpression();

  String getFromClause();

  String getProjectionAttributes();

  ObjectType getIndexResultSetType();

  void expansion(List expandedResults, Object lowerBoundKey, Object upperBoundKey,
      int lowerBoundOperator, int upperBoundOperator, Object value) throws IMQException;

}
