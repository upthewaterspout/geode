package org.apache.geode.distributed.internal.membership.gms.tcpmessenger.netty;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.MessageToMessageDecoder;

import org.apache.geode.internal.serialization.DSFIDSerializer;

/**
 * Message decoder that converts bytes into an object using the DataSerializer.
 *
 * This decoder expects the passed int ByteBuf to contain the entire message, so
 * it must have something in front of it in the pipeline that ensures that the entire
 * message is received, for example {@link LengthFieldBasedFrameDecoder}
 */
public class DataSerializableDecoder extends MessageToMessageDecoder<ByteBuf> {
  private DSFIDSerializer serializer;

  public DataSerializableDecoder(DSFIDSerializer serializer) {

    this.serializer = serializer;
  }

  @Override
  protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out)
      throws Exception {
    Object message = serializer.getObjectDeserializer().readObject(new ByteBufInputStream(in));
    out.add(message);
    return;
  }
}
