/*******************************************************************************
 * Copyright (c) 2013, 2014 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.cf.commands;

import java.net.URI;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.httpclient.methods.GetMethod;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.server.cf.CFActivator;
import org.eclipse.orion.server.cf.objects.Space;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SetSpaceCommand extends AbstractCFCommand {

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;

	private Target target;
	private String spaceName;

	public SetSpaceCommand(Target target, String spaceName) {
		this.commandName = "Login"; //$NON-NLS-1$
		this.target = target;
		this.spaceName = spaceName;
	}

	public IStatus doIt() {
		IStatus status = super.doIt();
		if (!status.isOK())
			return status;

		try {
			URI targetURI = URIUtil.toURI(target.getUrl());
			String spaceUrl = target.getOrg().getCFJSON().getJSONObject("entity").getString("spaces_url");
			URI spaceURI = targetURI.resolve(spaceUrl);

			GetMethod getMethod = new GetMethod(spaceURI.toString());
			HttpUtil.configureHttpMethod(getMethod, target);
			ServerStatus getStatus = HttpUtil.executeMethod(getMethod);
			if (!getStatus.isOK())
				return getStatus;

			JSONObject result = getStatus.getJsonData();
			JSONArray spaces = result.getJSONArray("resources");

			if (spaces.length() == 0) {
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "Space not found", null);
			}

			if (this.spaceName == null || "".equals(this.spaceName)) {
				JSONObject space = spaces.getJSONObject(0);
				target.setSpace(new Space().setCFJSON(space));
			} else {
				for (int i = 0; i < spaces.length(); i++) {
					JSONObject space = spaces.getJSONObject(i);
					if (spaceName.equals(space.getJSONObject("entity").getString("name")))
						target.setSpace(new Space().setCFJSON(space));
				}
			}

			if (target.getSpace() == null) {
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "Space not found", null);
			}

			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, target.getSpace().toJSON());
		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new Status(IStatus.ERROR, CFActivator.PI_CF, msg, e);
		}
	}
}
