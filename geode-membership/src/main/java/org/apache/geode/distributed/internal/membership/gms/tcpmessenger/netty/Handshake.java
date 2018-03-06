package org.apache.geode.distributed.internal.membership.gms.tcpmessenger.netty;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.apache.geode.distributed.internal.membership.api.MemberIdentifier;
import org.apache.geode.distributed.internal.membership.gms.messages.AbstractGMSMessage;
import org.apache.geode.internal.serialization.DeserializationContext;
import org.apache.geode.internal.serialization.KnownVersion;
import org.apache.geode.internal.serialization.SerializationContext;
import org.apache.geode.internal.serialization.Version;

public class Handshake<ID extends MemberIdentifier> extends AbstractGMSMessage<ID> {

  @Override
  public int getDSFID() {
    return HANDSHAKE;
  }

  @Override
  public void toData(DataOutput out, SerializationContext context) throws IOException {
    out.writeInt(KnownVersion.CURRENT_ORDINAL);
    context.getSerializer().writeObject(getSender(), out);
  }

  @Override
  public void fromData(DataInput in, DeserializationContext context)
      throws IOException, ClassNotFoundException {
    // Remove version is currently unused, but it could be used to control deserialization logic in
    // the future
    int remoteVersion = in.readInt();
    setSender(context.getDeserializer().readObject(in));

  }

  @Override
  public KnownVersion[] getSerializationVersions() {

    // NOTE - Handshakes cannot use this method, they will always be deserialized with the current
    // version. Any versioning logic must happen in fromData itself.
    return new KnownVersion[0];
  }
}
