/*
 * © 2016-2019 FlowCrypt Limited. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.api.retrofit.request.node;

import com.flowcrypt.email.api.retrofit.node.NodeService;

import java.io.IOException;

import retrofit2.Response;

/**
 * This {@linkplain NodeRequest request} can be used to receive information about a version.
 *
 * @author Denis Bondarenko
 * Date: 1/10/19
 * Time: 5:46 PM
 * E-mail: DenBond7@gmail.com
 */
public final class VersionRequest extends BaseNodeRequest {

  @Override
  public String getEndpoint() {
    return "version";
  }

  @Override
  public byte[] getData() {
    return null;
  }

  @Override
  public Response getResponse(NodeService nodeService) throws IOException {
    if (nodeService != null) {
      return nodeService.getVersion(this).execute();
    } else return null;
  }
}