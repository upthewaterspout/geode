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
package org.apache.geode.protocol.serialization;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.generator.GeneratorConfiguration;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;

import org.apache.geode.pdx.PdxInstance;
import org.apache.geode.pdx.PdxInstanceFactory;

public class PdxInstanceGenerator extends Generator {


  private Set<Class<?>> allowedFieldTypes;
  private String className = "NO_CLASS";


  public PdxInstanceGenerator() {
    super(PdxInstance.class);
  }

  @Override
  public Object generate(SourceOfRandomness random, GenerationStatus status) {

    Map<Class<?>, Method> writeMethods = getAllowedWriteMethods();

    int numFields = random.nextInt(0, 20);
    PdxInstanceFactory factory = PdxInstanceFactoryMock.createMockFactory(className);
    Set<String> fieldNames =
        new HashSet<>(gen().type(String.class).times(numFields).generate(random, status));
    for (String fieldName : fieldNames) {
      Map.Entry<Class<?>, Method> writeMethod = random.choose(writeMethods.entrySet());
      Class<?> type = writeMethod.getKey();
      Method method = writeMethod.getValue();
      Object value = gen().type(type).generate(random, status);
      try {
        method.invoke(factory, fieldName, value);
      } catch (IllegalAccessException | InvocationTargetException e) {
        throw new IllegalStateException(e);
      }
    }

    return factory.create();
  }

  private Map<Class<?>, Method> getAllowedWriteMethods() {
    final Map<Class<?>, Method> writeMethods = new HashMap<>();

    Method[] methods = PdxInstanceFactory.class.getDeclaredMethods();
    for (Method method : methods) {
      if (method.getName().startsWith("write") && method.getParameterTypes().length == 2
          && (allowedFieldTypes == null
              || allowedFieldTypes.contains(method.getParameterTypes()[1]))) {
        Class<?> type = method.getParameterTypes()[1];
        writeMethods.put(type, method);
      }
    }

    return writeMethods;
  }

  public void configure(FieldTypes fieldTypes) {
    this.allowedFieldTypes = new HashSet<>(Arrays.asList(fieldTypes.value()));
  }

  public void configure(ClassName className) {
    this.className = className.value();
  }

  @Target({PARAMETER, FIELD, ANNOTATION_TYPE, TYPE_USE})
  @Retention(RUNTIME)
  @GeneratorConfiguration
  public @interface FieldTypes {
    Class<?>[] value();
  }

  @Target({PARAMETER, FIELD, ANNOTATION_TYPE, TYPE_USE})
  @Retention(RUNTIME)
  @GeneratorConfiguration
  public @interface ClassName {
    String value();
  }
}
