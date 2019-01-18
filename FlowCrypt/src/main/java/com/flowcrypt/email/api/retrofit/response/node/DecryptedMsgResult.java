package com.flowcrypt.email.api.retrofit.response.node;

import android.os.Parcel;

import com.flowcrypt.email.api.retrofit.node.NodeGson;
import com.flowcrypt.email.api.retrofit.response.model.node.BlockMetas;
import com.flowcrypt.email.api.retrofit.response.model.node.Longids;
import com.flowcrypt.email.api.retrofit.response.model.node.MsgBlock;
import com.google.gson.annotations.Expose;

import java.io.BufferedInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * It's a result for "decryptMsg" requests.
 *
 * @author Denis Bondarenko
 * Date: 1/11/19
 * Time: 3:48 PM
 * E-mail: DenBond7@gmail.com
 */
public class DecryptedMsgResult extends BaseNodeResult {

  public static final Creator<DecryptedMsgResult> CREATOR = new Creator<DecryptedMsgResult>() {
    @Override
    public DecryptedMsgResult createFromParcel(Parcel source) {
      return new DecryptedMsgResult(source);
    }

    @Override
    public DecryptedMsgResult[] newArray(int size) {
      return new DecryptedMsgResult[size];
    }
  };

  @Expose
  private boolean success;

  @Expose
  private List<BlockMetas> blockMetas;

  @Expose
  private boolean isEncrypted;

  @Expose
  private Longids longids;

  private List<MsgBlock> msgBlocks;

  public DecryptedMsgResult() {
    this.msgBlocks = new ArrayList<>();
  }

  protected DecryptedMsgResult(Parcel in) {
    super(in);
    this.success = in.readByte() != 0;
    this.blockMetas = in.createTypedArrayList(BlockMetas.CREATOR);
    this.isEncrypted = in.readByte() != 0;
    this.longids = in.readParcelable(Longids.class.getClassLoader());
    this.msgBlocks = in.createTypedArrayList(MsgBlock.CREATOR);
  }

  @Override
  public void handleRawData(BufferedInputStream bufferedInputStream) {
    //todo-denbond7 Currently we peek only the first block. Need to fix that
    MsgBlock block = NodeGson.getInstance().getGson().fromJson(new InputStreamReader(bufferedInputStream),
        MsgBlock.class);

    if (block != null) {
      msgBlocks.add(block);
    }
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    super.writeToParcel(dest, flags);
    dest.writeByte(this.success ? (byte) 1 : (byte) 0);
    dest.writeTypedList(this.blockMetas);
    dest.writeByte(this.isEncrypted ? (byte) 1 : (byte) 0);
    dest.writeParcelable(this.longids, flags);
    dest.writeTypedList(this.msgBlocks);
  }

  public boolean isSuccess() {
    return success;
  }

  public List<BlockMetas> getBlockMetas() {
    return blockMetas;
  }

  public List<MsgBlock> getMsgBlocks() {
    return msgBlocks;
  }

  public boolean isEncrypted() {
    return isEncrypted;
  }

  public Longids getLongids() {
    return longids;
  }
}
