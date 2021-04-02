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
 *
 */
package org.apache.geode.redis.internal.netty;

import java.io.DataInput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import org.apache.geode.internal.cache.CountingDataInputStream;
import org.apache.geode.redis.internal.statistics.RedisStats;

public class ByteToCommandReader {


  private static final byte rID = 13; // '\r';
  private static final byte nID = 10; // '\n';
  private static final byte bulkStringID = 36; // '$';
  private static final byte arrayID = 42; // '*';
  private static final int MAX_BULK_STRING_LENGTH = 512 * 1024 * 1024; // 512 MB

  private final RedisStats redisStats;

  public ByteToCommandReader(RedisStats redisStats) {
    this.redisStats = redisStats;
  }

  protected void decode(CountingDataInputStream in, List<Object> out) throws Exception {
    Command c;
    long bytesRead = 0;
      long startReadIndex = in.getCount();
      c = parse(in);
      bytesRead += in.getCount() - startReadIndex;
      out.add(c);
    redisStats.incNetworkBytesRead(bytesRead);
  }

  public Command parse(CountingDataInputStream buffer)
      throws RedisCommandParserException, IOException {
    if (buffer == null) {
      throw new NullPointerException();
    }

    byte firstB = buffer.readByte();
    if (firstB != arrayID) {
      throw new RedisCommandParserException(
          "Expected: " + (char) arrayID + " Actual: " + (char) firstB);
    }
    List<byte[]> commandElems = parseArray(buffer);

    if (commandElems == null) {
      return null;
    }

    return new Command(commandElems);
  }

  private List<byte[]> parseArray(CountingDataInputStream buffer)
      throws RedisCommandParserException, IOException {
    byte currentChar;
    int arrayLength = parseCurrentNumber(buffer);
    if (arrayLength < 0 || arrayLength > 1000000000) {
      throw new RedisCommandParserException("invalid multibulk length");
    }

    List<byte[]> commandElems = new ArrayList<>(arrayLength);

    for (int i = 0; i < arrayLength; i++) {
      currentChar = buffer.readByte();
      if (currentChar == bulkStringID) {
        byte[] newBulkString = parseBulkString(buffer);
        if (newBulkString == null) {
          return null;
        }
        commandElems.add(newBulkString);
      } else {
        throw new RedisCommandParserException(
            "expected: \'$\', got \'" + (char) currentChar + "\'");
      }
    }
    return commandElems;
  }

  /**
   * Helper method to parse a bulk string when one is seen
   *
   * @param buffer Buffer to read from
   * @return byte[] representation of the Bulk String read
   * @throws RedisCommandParserException Thrown when there is illegal syntax
   */
  private byte[] parseBulkString(CountingDataInputStream buffer)
      throws RedisCommandParserException, IOException {
    int bulkStringLength = parseCurrentNumber(buffer);
    if (bulkStringLength > MAX_BULK_STRING_LENGTH) {
      throw new RedisCommandParserException(
          "invalid bulk length, cannot exceed max length of " + MAX_BULK_STRING_LENGTH);
    }

    byte[] bulkString = new byte[bulkStringLength];
    buffer.readFully(bulkString);

    if (!parseRN(buffer)) {
      return null;
    }

    return bulkString;
  }

  /**
   * Helper method to parse the number at the beginning of the buffer
   *
   * @param buffer Buffer to read
   * @return The number found at the beginning of the buffer
   */
  private int parseCurrentNumber(CountingDataInputStream buffer)
      throws IOException, RedisCommandParserException {
    int number = 0;
    byte b = 0;
    while (true) {
      b = buffer.readByte();
      if (Character.isDigit(b)) {
        number = number * 10 + (int) (b - '0');
      } else if (b == rID) {
        break;
      } else {
        throw new RedisCommandParserException(
            "expected \'" + (char) rID + "\', got \'" + (char) b + "\'");
      }
    }
    //Expected a \r\n. The loop above terminated on the \r
    b = buffer.readByte();
    if (b != nID) {
      throw new RedisCommandParserException(
          "expected: \'" + (char) nID + "\', got \'" + (char) b + "\'");
    }
    return number;
  }

  /**
   * Helper method that is called when the next characters are supposed to be "\r\n"
   *
   * @param buffer Buffer to read from
   * @throws RedisCommandParserException Thrown when the next two characters are not "\r\n"
   */
  private boolean parseRN(CountingDataInputStream buffer) throws RedisCommandParserException, IOException {
    byte b = buffer.readByte();
    if (b != rID) {
      throw new RedisCommandParserException(
          "expected \'" + (char) rID + "\', got \'" + (char) b + "\'");
    }
    b = buffer.readByte();
    if (b != nID) {
      throw new RedisCommandParserException(
          "expected: \'" + (char) nID + "\', got \'" + (char) b + "\'");
    }
    return true;
  }

}
