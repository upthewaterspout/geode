package org.apache.geode.distributed.internal.membership.gms.tcpmessenger.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import org.apache.geode.internal.serialization.DSFIDSerializer;

/**
 * An netty encoder which translates a DataSerializable message into
 * a netty ByteBuf.
 */
public class DataSerializableEncoder extends MessageToByteEncoder<Object> {
  private DSFIDSerializer serializer;

  public DataSerializableEncoder(DSFIDSerializer serializer) {
    this.serializer = serializer;
  }

  @Override
  protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
    serializer.getObjectSerializer().writeObject(msg, new ByteBufOutputStream(out));
  }
}
