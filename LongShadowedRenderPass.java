/*
 *  Copyright (c) 2008 Adriano Dalpane
 *  All rights reserved.
 *
 *  This file is part of JIVES.
 *
 *  JIVES is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  JIVES is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with JIVES.  If not, see <http://www.gnu.org/licenses/>. 
 */

package jives.xutils;

import com.jme.renderer.pass.ShadowedRenderPass;
import com.jme.scene.SceneElement;
import com.jme.scene.Spatial;
import com.jme.scene.batch.TriangleBatch;
import com.jme.scene.shadow.MeshShadows;
import com.jme.scene.state.LightState;
import com.jme.scene.state.RenderState;

public class LongShadowedRenderPass extends ShadowedRenderPass {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private long projectionDistance = 10000;

	@Override
	protected void generateVolumes() {

		for (int c = 0; c < occluderMeshes.size(); c++) {
			TriangleBatch tb = occluderMeshes.get(c);
			if (!getShadowGate().shouldUpdateShadows(tb))
				continue;
			if (!meshes.containsKey(tb)) {
				meshes.put(tb, new MeshShadows(tb));
			} else if ((tb.getLocks() & SceneElement.LOCKED_SHADOWS) != 0) {
				continue;
			}

			MeshShadows sv = meshes.get(tb);

			sv.setProjectionLength(projectionDistance);
			// Create the geometry for the shadow volume
			sv.createGeometry((LightState) tb.states[RenderState.RS_LIGHT]);

		}
	}

	public long getProjectionDistance() {
		return projectionDistance;
	}

	public void setProjectionDistance(long value) {
		projectionDistance = value;
	}
	
   /**
    * <code>addOccluder</code> adds an occluder to this pass.
    *
    * @param toAdd
    *            Occluder Spatial to add to this pass.
    */
	@Override
   public void addOccluder(Spatial toAdd) {
		occluders.add(toAdd);
   }
}
