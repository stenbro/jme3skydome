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

import java.net.MalformedURLException;
import java.nio.FloatBuffer;

import jives.utils.ResourceLoader;

import com.jme.image.Texture;
import com.jme.math.FastMath;
import com.jme.math.Vector3f;
import com.jme.renderer.ColorRGBA;
import com.jme.scene.Node;
import com.jme.scene.batch.TriangleBatch;
import com.jme.scene.shape.Sphere;
import com.jme.scene.state.AlphaState;
import com.jme.scene.state.FogState;
import com.jme.scene.state.MaterialState;
import com.jme.scene.state.RenderState;
import com.jme.scene.state.TextureState;
import com.jme.system.DisplaySystem;
import com.jme.util.TextureManager;
import com.jme.util.geom.BufferUtils;

public class CAtmosphere {

	/** sky dome related to this atmospheric effects object */
	private CSkyDome skydome;
	/** clouds dome */
	private Sphere cloudsDome;
	/** clouds material */
	private MaterialState cloudsMat;
	/** clouds Textures */
	private Texture cloudsTex;
	/** clouds translation controller */
	private Thread windThread;
	/** the "winds" */
	private Vector3f wind;
	/** the wind update speed */
	private float windSpeed;
	/** the haze ender state */
	private FogState haze;
	/** the earth node */
	private Node earthNode;

	private DisplaySystem display = DisplaySystem.getDisplaySystem();

	private boolean running;
	
	/**
	 * Constructor. Creates a new instance of CAtmosphere<br>
	 * <strong>IMPORTANT:</strong> set your camera far clipping plane to a very
	 * high value, like <code>Integer.MAX_VALUE</code> if you dont want
	 * clipping to occur in the distance.
	 * 
	 * @param skydome -
	 *            an instance of a CSkyDome class
	 * @param earthNode -
	 *            a node that contains the terrain/ground geometry that is going
	 *            to be affected by the haze effect
	 * @param cloudsTexFilename
	 *            The texture used for the cloud layer.
	 */
	public CAtmosphere(CSkyDome skydome, Node earthNode,
			String cloudsTexFilename) {

		try {
			// Set properties
			this.skydome = skydome;
			this.earthNode = earthNode;
	
			// Create a sphere for the clouds
			cloudsDome = new Sphere("Clouds", new Vector3f(0,
					-skydome.getRadius() / 3f, 0), 15, 20,
					skydome.getRadius() * 0.45f);
			cloudsDome.setIsCollidable(false);
			cloudsDome.setSolidColor(ColorRGBA.white);
	
			// Flip normals of the sphere to correctly reflect sun light
			FloatBuffer normalBuf;
			TriangleBatch batch;
			Vector3f vertex = new Vector3f();
			for (int i = 0; i < cloudsDome.getBatchCount(); i++) {
				batch = cloudsDome.getBatch(i);
	
				normalBuf = batch.getNormalBuffer();
	
				for (int j = 0; j < batch.getVertexCount(); j++) {
					BufferUtils.populateFromBuffer(vertex, normalBuf, j);
					vertex.negateLocal();
					BufferUtils.setInBuffer(vertex, normalBuf, j);
				}
			}
	
			// Load the clouds texture
			TextureState textureState = display.getRenderer().createTextureState();

			cloudsTex = TextureManager.loadTexture(ResourceLoader.locateResource(cloudsTexFilename),
					Texture.MM_LINEAR_LINEAR, Texture.FM_LINEAR);

			// Tile
			cloudsTex.setWrap(Texture.WM_WRAP_S_WRAP_T);
			cloudsTex.setScale(new Vector3f(4, 1, 4));
			cloudsTex.setTranslation(new Vector3f());
			textureState.setTexture(cloudsTex);
	
			textureState.setEnabled(true);
			textureState.apply();
	
			// Create the alpha state.
			AlphaState alphaState = display.getRenderer().createAlphaState();
			alphaState.setBlendEnabled(true);
			alphaState.setSrcFunction(AlphaState.SB_SRC_ALPHA);
			alphaState.setDstFunction(AlphaState.DB_ONE);
			alphaState.setTestEnabled(true);
			alphaState.setTestFunction(AlphaState.TF_GREATER);
			alphaState.setEnabled(true);
			// Assign render states
			cloudsDome.setRenderState(textureState);
			cloudsDome.setRenderState(alphaState);
			cloudsDome.updateRenderState();
			// Setup sky light
			cloudsDome.setRenderState(skydome.getSkyLightState());
	
			// Add a material that can affect transparency
			cloudsMat = display.getRenderer().createMaterialState();
			cloudsMat.setDiffuse(ColorRGBA.white);
			cloudsMat.setMaterialFace(MaterialState.MF_FRONT_AND_BACK);
			cloudsMat.setColorMaterial(MaterialState.CM_DIFFUSE);
			cloudsDome.setRenderState(cloudsMat);
	
			// Attach the star dome to the skydome.
			skydome.getSkyDomeNode().attachChild(cloudsDome);
	
			// Create haze on the earth node
			haze = display.getRenderer().createFogState();
			haze.setDensity(0.0005f);
			haze.setEnabled(true);
			haze.setColor(new ColorRGBA(0.7f, 0.7f, 0.7f, 0.5f));
			haze.setEnd(7000);
			haze.setStart(600);
			haze.setDensityFunction(FogState.DF_EXP);
			haze.setApplyFunction(FogState.AF_PER_PIXEL);
	
			earthNode.setRenderState(haze);
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Sets clouds thickness
	 * 
	 * @param value -
	 *            value of eterogenous transparency, clamped in 0-1 interval
	 */
	public void setCloudness(float value) {
		value = Math.max(Math.min(1, value), 0);
		ColorRGBA cloudsDiffuse = cloudsMat.getDiffuse();

		// apply geometry color
		FloatBuffer colorBuf;
		TriangleBatch batch;
		for (int i = 0; i < cloudsDome.getBatchCount(); i++) {
			batch = cloudsDome.getBatch(i);

			colorBuf = batch.getColorBuffer();
			for (int j = 0; j < batch.getVertexCount(); j++) {
				cloudsDiffuse.a = value
						* (FastMath.rand.nextFloat() * 0.9f + 0.1f);
				BufferUtils.setInBuffer(cloudsDiffuse, colorBuf, j);
			}
		}
	}

	/**
	 * Change the clouds texture
	 * 
	 * @param cloudsTexFilename
	 *            The texture file name used for the cloud layer.
	 * @throws MalformedURLException if the file is not found
	 */
	public void setCloudsTexture(String cloudsTexFilename) throws MalformedURLException {
		Texture oldTex = cloudsTex.createSimpleClone();
		cloudsTex = TextureManager.loadTexture(ResourceLoader.locateResource(cloudsTexFilename),
				Texture.MM_LINEAR_LINEAR, Texture.FM_LINEAR);

		// Tile
		cloudsTex.setWrap(oldTex.getWrap());
		cloudsTex.setScale(oldTex.getScale());
		cloudsTex.setTranslation(oldTex.getTranslation());

		TextureState ts = (TextureState) cloudsDome
				.getRenderState(RenderState.RS_TEXTURE);
		ts.setTexture(cloudsTex);
		cloudsDome.updateRenderState();
	}

	/**
	 * Won't you like the built in one, huh?
	 * 
	 * @param state -
	 *            FogState render state to apply to the haze
	 */
	public void setHazeRenderState(FogState state) {
		earthNode.setRenderState(state);
	}

	/**
	 * Sets the amount the clouds are translated
	 * 
	 * @param wind -
	 *            a translation vector with coordinates between 0 and 1
	 * @param windSpeed -
	 *            multiplier of update speed
	 */
	public void setWind(Vector3f wind, float windSpeed) {
		this.wind = wind;
		this.windSpeed = windSpeed;
		this.running = true;
		if (windThread == null) {
			windThread = new Thread() {
				private Vector3f theWind;
				private long sleepTime;

				@Override
				public void run() {
					updateParams();
					Vector3f currentTra; Vector3f newTra;

					while (running) {
						currentTra = cloudsTex.getTranslation();
						newTra = new Vector3f(
								(currentTra.x + theWind.x) % 1,
								(currentTra.y + theWind.y) % 1,
								(currentTra.z + theWind.z) % 1);
						cloudsTex.setTranslation(newTra);
						try {
							Thread.sleep(sleepTime);
						} catch (InterruptedException e) {
						}
						updateParams();
					}
				}

				private void updateParams() {
					theWind = CAtmosphere.this.wind;
					sleepTime = (long) (200 * CAtmosphere.this.windSpeed);
				}
			};
			windThread.start();
		}
	}

	/**
	 * Updates sky
	 * 
	 * @param tpf -
	 *            Time per frame from
	 *            <code>BasicGameState</code> <code>Update()</code> method
	 * @param elapsHH -
	 *            Elapsed hours
	 * @param elapsMM -
	 *            Elapsed minutes
	 * @param elapsSS -
	 *            Elapsed seconds
	 */
	public void update(float tpf, int elapsHH, int elapsMM, int elapsSS) {
		// Update haze color and backbuffer color
		haze.setColor(skydome.getHazeColor());

		// Warp clouds
		Vector3f currentTra = cloudsTex.getTranslation();
		float timeFactor = 0.25f * (elapsHH + 0.016f * elapsMM);
		Vector3f normWind = wind.normalize();
		Vector3f newTra = new Vector3f(
				(currentTra.x + normWind.x * timeFactor) % 0.95f,
				(currentTra.y + normWind.y * timeFactor) % 0.95f,
				(currentTra.z + normWind.z * timeFactor) % 0.95f);
		cloudsTex.setTranslation(newTra);
	}
	
	/** Call this in the scene cleanup method. */
	public void cleanup() {
		running = false;
		windThread.interrupt();
	}
}
