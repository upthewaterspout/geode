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
package org.apache.geode.internal.protocol.protobuf.v1.operations;

import java.util.List;

import org.apache.logging.log4j.Logger;

import org.apache.geode.cache.query.Query;
import org.apache.geode.cache.query.QueryException;
import org.apache.geode.cache.query.SelectResults;
import org.apache.geode.cache.query.Struct;
import org.apache.geode.cache.query.internal.InternalQueryService;
import org.apache.geode.internal.exception.InvalidExecutionContextException;
import org.apache.geode.internal.logging.LogService;
import org.apache.geode.internal.protocol.operations.ProtobufOperationHandler;
import org.apache.geode.internal.protocol.protobuf.v1.BasicTypes;
import org.apache.geode.internal.protocol.protobuf.v1.BasicTypes.EncodedValue;
import org.apache.geode.internal.protocol.protobuf.v1.ClientProtocol.ErrorResponse;
import org.apache.geode.internal.protocol.protobuf.v1.Failure;
import org.apache.geode.internal.protocol.protobuf.v1.MessageExecutionContext;
import org.apache.geode.internal.protocol.protobuf.v1.ProtobufSerializationService;
import org.apache.geode.internal.protocol.protobuf.v1.RegionAPI.OQLQueryRequest;
import org.apache.geode.internal.protocol.protobuf.v1.RegionAPI.OQLQueryResponse;
import org.apache.geode.internal.protocol.protobuf.v1.RegionAPI.OQLQueryResponse.Builder;
import org.apache.geode.internal.protocol.protobuf.v1.Result;
import org.apache.geode.internal.protocol.protobuf.v1.Success;
import org.apache.geode.internal.protocol.protobuf.v1.serialization.exception.DecodingException;
import org.apache.geode.internal.protocol.protobuf.v1.serialization.exception.EncodingException;
import org.apache.geode.internal.protocol.protobuf.v1.state.exception.ConnectionStateException;
import org.apache.geode.internal.protocol.protobuf.v1.utilities.ProtobufResponseUtilities;

public class OqlQueryRequestOperationHandler
    implements ProtobufOperationHandler<OQLQueryRequest, OQLQueryResponse> {
  Logger logger = LogService.getLogger();

  @Override
  public Result<OQLQueryResponse, ErrorResponse> process(
      final ProtobufSerializationService serializationService, final OQLQueryRequest request,
      final MessageExecutionContext messageExecutionContext)
      throws InvalidExecutionContextException, ConnectionStateException, EncodingException,
      DecodingException {
    String queryString = request.getQuery();
    List<EncodedValue> encodedParameters = request.getBindParameterList();

    InternalQueryService queryService = messageExecutionContext.getCache().getQueryService();

    Query query = queryService.newQuery(queryString);

    Object[] bindParameters = new Object[encodedParameters.size()];
    for (int i = 0; i < encodedParameters.size(); i++) {
      bindParameters[i] = serializationService.decode(encodedParameters.get(i));
    }
    try {
      Object results = query.execute(bindParameters);
      return Success.of(encodeResults(serializationService, results));
    } catch (QueryException e) {
      logger.info("Query failed: " + query, e);
      return Failure.of(ProtobufResponseUtilities.makeErrorResponse(e));
    }

  }

  private OQLQueryResponse encodeResults(final ProtobufSerializationService serializationService,
      final Object value) throws EncodingException {
    final Builder builder = OQLQueryResponse.newBuilder();
    if (value instanceof SelectResults) {
      for (Object row : (SelectResults) value) {
        if (row instanceof Struct) {
          builder.addResult(encodeStruct(serializationService, (Struct) row));
        } else {
          builder.addResult(
              BasicTypes.Struct.newBuilder().addElement(serializationService.encode(row)));
        }
      }

    } else {
      builder
          .addResult(BasicTypes.Struct.newBuilder().addElement(serializationService.encode(value)));
    }
    return builder.build();
  }

  private BasicTypes.Struct.Builder encodeStruct(
      final ProtobufSerializationService serializationService, final Struct row)
      throws EncodingException {
    final Struct struct = row;
    BasicTypes.Struct.Builder structBuilder = BasicTypes.Struct.newBuilder();
    for (Object element : struct.getFieldValues()) {
      structBuilder.addElement(serializationService.encode(element));
    }
    return structBuilder;
  }

}
