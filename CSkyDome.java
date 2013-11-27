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
import java.util.ArrayList;

import jives.utils.CMoonObserver;
import jives.utils.CSunObserver;
import jives.utils.ResourceLoader;

import com.jme.image.Texture;
import com.jme.light.Light;
import com.jme.light.PointLight;
import com.jme.light.SimpleLightNode;
import com.jme.math.FastMath;
import com.jme.math.Vector3f;
import com.jme.renderer.ColorRGBA;
import com.jme.renderer.pass.ShadowedRenderPass;
import com.jme.scene.BillboardNode;
import com.jme.scene.Node;
import com.jme.scene.SceneElement;
import com.jme.scene.batch.TriangleBatch;
import com.jme.scene.shape.Dome;
import com.jme.scene.shape.Quad;
import com.jme.scene.shape.Sphere;
import com.jme.scene.state.AlphaState;
import com.jme.scene.state.ClipState;
import com.jme.scene.state.LightState;
import com.jme.scene.state.MaterialState;
import com.jme.scene.state.RenderState;
import com.jme.scene.state.TextureState;
import com.jme.system.DisplaySystem;
import com.jme.util.TextureManager;
import com.jme.util.geom.BufferUtils;
import com.jmex.effects.LensFlare;
import com.jmex.effects.LensFlareFactory;

public class CSkyDome {
	/**
	 * Representation of sky color in 3d space
	 */
	class ColorXYZ {
		private float x = 0.0f;
		private float y = 0.0f;
		private float z = 0.0f;
		private float r = 0.0f;
		private float g = 0.0f;
		private float b = 0.0f;
		private float a = 1.0f;
		private float hue = 0.0f;
		private float saturation = 0.0f;
		private float value = 0.0f;

		public ColorXYZ(float x, float y, float z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}

		public void clamp() {
			if (r < 0)
				r = 0;
			if (g < 0)
				g = 0;
			if (b < 0)
				b = 0;
			if (r > 1)
				r = 1;
			if (g > 1)
				g = 1;
			if (b > 1)
				b = 1;
		}

		/**
		 * Converte HSV to RGB
		 */
		public ColorXYZ convertHSVtoRGB() {
			if (FastMath.abs(saturation) < EPSILON) { // achromatic (grey)
				this.r = value;
				this.g = value;
				this.b = value;
				this.a = value;
			}

			hue /= 60.0f; // sector 0 to 5
			int sector = (int) FastMath.floor(hue);

			float f = hue - sector; // factorial part of hue
			float p = value * (1.0f - saturation);
			float q = value * (1.0f - saturation * f);
			float t = value * (1.0f - saturation * (1.0f - f));
			switch (sector) {
			case 0:
				this.r = value;
				this.g = t;
				this.b = p;
				break;
			case 1:
				this.r = q;
				this.g = value;
				this.b = p;
				break;
			case 2:
				this.r = p;
				this.g = value;
				this.b = t;
				break;
			case 3:
				this.r = p;
				this.g = q;
				this.b = value;
				break;
			case 4:
				this.r = t;
				this.g = p;
				this.b = value;
				break;
			default: // case 5:
				this.r = value;
				this.g = p;
				this.b = q;
				break;
			}
			return this;
		}

		/**
		 * Converte RGB to HSV
		 */
		public ColorXYZ convertRGBtoHSV() {
			float minColor = Math.min(Math.min(r, g), b);
			float maxColor = Math.max(Math.max(r, g), b);
			float delta = maxColor - minColor;

			this.value = maxColor; // Value
			if (!(FastMath.abs(maxColor) < EPSILON)) {
				this.saturation = delta / maxColor; // Saturation
			} else { // r = g = b = 0
				this.saturation = 0.0f; // Saturation = 0
				this.hue = -1; // Hue = undefined
				return this;
			}

			if (FastMath.abs(r - maxColor) < EPSILON)
				this.hue = (g - b) / delta; // between yellow & magenta
			else if (FastMath.abs(g - maxColor) < EPSILON)
				this.hue = 2.0f + (b - r) / delta; // between cyan & yellow
			else
				this.hue = 4.0f + (r - g) / delta; // between magenta & cyan

			this.hue *= 60.0f; // degrees

			if (this.hue < 0.0f)
				this.hue += 360.0f; // positive
			return this;
		}

		/**
		 * Converte XYZ to RGB color
		 */
		public ColorXYZ convertXYZtoRGB() {
			this.r = 3.240479f * x - 1.537150f * y - 0.498535f * z;
			this.g = -0.969256f * x + 1.875992f * y + 0.041556f * z;
			this.b = 0.055648f * x - 0.204043f * y + 1.057311f * z;
			return this;
		}

		/**
		 * Retorna o RGBA color
		 */
		public ColorRGBA getRGBA() {
			return new ColorRGBA(r, g, b, a);
		}

		public float getValue() {
			return this.value;
		}

		public void setGammaCorrection(float gammaCorrection) {
			r = FastMath.pow(r, gammaCorrection);
			g = FastMath.pow(g, gammaCorrection);
			b = FastMath.pow(b, gammaCorrection);
		}

		public void setValue(float value) {
			this.value = value;
		}
	}

	/** the Moon */
	public class Moon {
		public CMoonObserver moonObserver;
		public Node moonNode;
		// The moonlight and its node
		public PointLight light;
		// The moon lens flare effect object.
		public Quad moonFlareEffect;
	}

	/** the Sun */
	public class Sun {
		public CSunObserver sunObserver;
		public SimpleLightNode sunNode;
		// The sunlight and its node
		public PointLight light;
		// The sun's lens flare effect object.
		public LensFlare sunFlareEffect;
		// A multiplier for sun size set by user
		public float sizeMult;
		// Node to pick geometry occlusion from
		public Node pickNode;
	}

	public static final float INFINITY = 3.3e+38f;
	public static final float EPSILON = 0.000001f;
	private static final int RAD_SAMPLES = 12;
	// shading parameters
	private ColorRGBA skyColor;
	private float turbidity = 2.0f;
	private boolean isLinearExpControl;
	private float exposure = 18.0f;
	private float overcast;
	private float gammaCorrection = 2.5f;
	// used at update color
	private float chi;
	private float zenithLuminance;
	private float zenithX;
	private float zenithY;
	private float[] perezLuminance;
	private float[] perezX;
	private float[] perezY;
	private ColorXYZ color;
	private ColorXYZ colorTemp;
	private ColorRGBA additionColor;
	private TriangleBatch batch;
	private FloatBuffer colorBuf;
	private FloatBuffer normalBuf;
	private Vector3f vertex = new Vector3f();
	private ColorRGBA vertexColor = new ColorRGBA();
	private float gamma;
	private float cosTheta;
	private float cosGamma2;
	private float x_value;
	private float y_value;
	private float yClear;
	private float yOver;

	private float _Y;

	private float _X;

	private float _Z;

	/** Distribution coefficients for the luminance(Y) distribution function */
	private float distributionLuminance[][] = { // Perez distributions
	{ 0.17872f, -1.46303f }, // a = darkening or brightening of the horizon
			{ -0.35540f, 0.42749f }, // b = luminance gradient near the
			// horizon,
			{ -0.02266f, 5.32505f }, // c = relative intensity of the
			// circumsolar region
			{ 0.12064f, -2.57705f }, // d = width of the circumsolar region
			{ -0.06696f, 0.37027f } }; // e = relative backscattered light

	/** Distribution coefficients for the x distribution function */
	private float distributionXcomp[][] = { { -0.01925f, -0.25922f },
			{ -0.06651f, 0.00081f }, { -0.00041f, 0.21247f },
			{ -0.06409f, -0.89887f }, { -0.00325f, 0.04517f } };

	/** Distribution coefficients for the y distribution function */
	private float distributionYcomp[][] = { { -0.01669f, -0.26078f },
			{ -0.09495f, 0.00921f }, { -0.00792f, 0.21023f },
			{ -0.04405f, -1.65369f }, { -0.01092f, 0.05291f } };

	/** Zenith x value */
	private float zenithXmatrix[][] = {
			{ 0.00165f, -0.00375f, 0.00209f, 0.00000f },
			{ -0.02903f, 0.06377f, -0.03202f, 0.00394f },
			{ 0.11693f, -0.21196f, 0.06052f, 0.25886f } };
	/** Zenith y value */
	private float zenithYmatrix[][] = {
			{ 0.00275f, -0.00610f, 0.00317f, 0.00000f },
			{ -0.04214f, 0.08970f, -0.04153f, 0.00516f },
			{ 0.15346f, -0.26756f, 0.06670f, 0.26688f } };
	private DisplaySystem display = DisplaySystem.getDisplaySystem();
	/** The radius of the sky dome. */
	private float radius = 400000;

	/**
	 * The float array contains the numbers of the sunlight color at dawn and
	 * dusk.
	 */
	private float[] dwColor = new float[] { 0.9843f, 0.7098f, 0.3523f, 1 };

	private float[] duColor = new float[] { 0.6843f, 0.5098f, 0.1246f, 1 };

	/**
	 * The float array contains the numbers of the moonlight color during night
	 * time.
	 */
	private float[] mColor = new float[] { 0.2745f, 0.3961f, 0.6196f, 1 };

	/** The stars dome. */
	private Sphere starDome;

	private ArrayList<Sun> suns;

	private ArrayList<Moon> moons;

	private Vector3f sceneOffset;
	private LightState skyLightState;

	/** World light state where sky lights will be applied */
	private LightState ambient;
	/** World shadows */
	private ShadowedRenderPass shadows;

	/** the sky hemisphere */
	private Dome dome;

	/** the sky hemisphere node */
	private Node domeNode = new Node("Dome Node");
	/** Node of the suns and moons */
	private Node skyNode = new Node("Sky Node");
	/** Node whatever in sky depends on */
	private Node skyDomeNode = new Node("Sky Dome Node");
	/**
	 * Lens flares must not be attached to the sky node so that they wont appear
	 * in reflections, etc...
	 */
	private Node lensFlaresNode = new Node("Lens Flares Node");
	/** Root node the sky node is attached to, or null */
	private Node rootNode;
	/** Color of the haze */
	private ColorRGBA hazeColor;
	/** Used to prevent unusefull repeated updates */
	private boolean wasNightTime;

	public CSkyDome(String name, Vector3f sceneOffset) {
		this(name, sceneOffset, null);
	}

	/**
	 * Constructor. Creates a new instance of CSkyDome<br>
	 * <strong>IMPORTANT:</strong> set your camera far clipping plane to a very
	 * high value, like <code>Integer.MAX_VALUE</code> if you dont want
	 * clipping to occur in the distance.
	 * 
	 * @param name -
	 *            Name of the node geometry
	 * @param sceneOffset -
	 *            Vector where to set the dome to
	 * @param shadows -
	 *            World shadow pass to update with sky lightings if any
	 */
	public CSkyDome(String name, Vector3f sceneOffset,
			LongShadowedRenderPass shadows) {

		// Initialize stuff
		suns = new ArrayList<Sun>();
		moons = new ArrayList<Moon>();
		this.sceneOffset = sceneOffset;
		this.shadows = shadows;
		this.skyColor = ColorRGBA.black;

		// Create sky dome
		dome = new Dome(name, new Vector3f(0, -radius * 0.0125f, 0),
				RAD_SAMPLES, 20, radius, true);
		dome.setIsCollidable(false);
		dome.setSolidColor(skyColor);
		dome.setCullMode(SceneElement.CULL_NEVER);
		domeNode.attachChild(dome);

		// Create a light state
		skyLightState = display.getRenderer().createLightState();
		skyLightState.setGlobalAmbient(ColorRGBA.white);
		skyNode.setRenderState(skyLightState);

		// Clip lower hemisphere
		ClipState clipState = display.getRenderer().createClipState();
		clipState.setEnableClipPlane(ClipState.CLIP_PLANE0, true);
		clipState.setClipPlaneEquation(0, 0, 0.01f, 0, 0.01f);
		skyDomeNode.setRenderState(clipState);

		skyDomeNode.attachChild(domeNode);
		skyDomeNode.attachChild(skyNode);

		setTurbidity(2.95f);
		setExposure(false, 21.0f);
		setOvercastFactor(0.45f);
		setGammaCorrection(1.09f);

	}

	/**
	 * Adds a moon to the sky.
	 * 
	 * @param sunObs -
	 *            Observer of this moon
	 * @param moonSizeMult -
	 *            Multiplier of moon size respect to earth's one
	 * @param moonTexFilename -
	 *            Filename of the texture used to render moon.
	 * @param flareTexFilename -
	 *            Filename of the texture used to render lens flare.<strong>NOTE:</strong>
	 *            It's more realistic to use a ring. Can be null to have no
	 *            flare.
	 * @return light to attach to world light state
	 */
	public Light addMoon(CMoonObserver moonObs, float moonSizeMult,
			String moonTexFilename, String flareTexFilename) {
		// Create Moon
		Moon theMoon = new Moon();
		moons.add(theMoon);
		theMoon.moonObserver = moonObs;

		try {
			// Create the moonlight which is a DirectionalLight.
			theMoon.light = new PointLight();
			theMoon.light.setDiffuse(new ColorRGBA(0.25f, 0.25f, 0.25f, 0.25f));
			theMoon.light.setAmbient(new ColorRGBA(0.1f, 0.1f, 0.1f, 1.f));
			theMoon.light.setEnabled(true);
			theMoon.light.setShadowCaster(false);
			theMoon.light.setLocation(moonObs.getPosition().subtract(sceneOffset));
	
			// Create moon geometry
			Sphere moonSphere = new Sphere("moonSphere");
			moonSphere.setData(new Vector3f(), 25, 25, moonSizeMult * 7000.f);
			MaterialState moonMat = display.getRenderer().createMaterialState();
			moonMat.setAmbient(new ColorRGBA(0.f, 0.f, 0.f, 1.f));
			moonMat.setDiffuse(new ColorRGBA(0.7f, 0.7f, 0.7f, 0.f));
			moonSphere.setRenderState(moonMat);
			TextureState moonTexture = display.getRenderer().createTextureState();
			Texture map;

			map = TextureManager.loadTexture(ResourceLoader.locateResource(moonTexFilename),
					Texture.MM_LINEAR_LINEAR, Texture.FM_LINEAR);
			moonTexture.setTexture(map);
			moonTexture.setEnabled(true);
			moonSphere.setRenderState(moonTexture);
			AlphaState moonAlpha = display.getRenderer().createAlphaState();
			moonAlpha.setBlendEnabled(true);
			moonAlpha.setTestEnabled(true);
			moonAlpha.setSrcFunction(AlphaState.SB_ONE_MINUS_SRC_ALPHA);
			moonAlpha.setDstFunction(AlphaState.DB_ONE);
			moonSphere.setRenderState(moonAlpha);
	
			// Create the moon which is a LightNode.
			theMoon.moonNode = new Node("moonNode");
			SimpleLightNode moonLightNode = new SimpleLightNode("moonLightNode",
					theMoon.light);
			theMoon.moonNode.attachChild(moonLightNode);
			theMoon.moonNode.attachChild(moonSphere);
			Vector3f absMoonPos = sceneOffset.add(theMoon.moonObserver
					.getPosition());
			theMoon.moonNode.setLocalTranslation(absMoonPos);
	
			// Create lens flare effect
			if (flareTexFilename != null) {
				buildMoonLensFlare(theMoon, flareTexFilename, moonSizeMult);
				updateMoonLensFlare(theMoon.moonFlareEffect, theMoon.moonObserver
						.getLatitude(), 0);
			}
	
			skyNode.attachChild(theMoon.moonNode);
			skyNode.updateRenderState();
	
			// Attach light to sky light state and return it to be attach to the sky
			// LightState.
			skyLightState.attach(theMoon.light);
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		return theMoon.light;
	}

	/**
	 * Adds a sun to the sky.
	 * 
	 * @param sunObs -
	 *            Observer of this sun
	 * @param sunSizeMult -
	 *            Multiplier of sun size respect to earth's one
	 * @param flareTexFilenames -
	 *            Array of four filenames of the textures to use to render lens
	 *            flare. <strong>NOTE:</strong> while the latter 3 are used to
	 *            render circles, the first is used to render sun.
	 * @param pickNode -
	 *            Node to pick geometry occlusion from, used for the lens flare
	 *            effect
	 * @return light to attach to world light state
	 */
	public Light addSun(CSunObserver sunObs, float sunSizeMult,
			String[] flareTexFilenames, Node pickNode) {
		try {
			// Create sun
			Sun theSun = new Sun();
			suns.add(theSun);
			theSun.sunObserver = sunObs;
			theSun.sizeMult = sunSizeMult;
			theSun.pickNode = pickNode;
		
			// Create the sunlight which is a PointLight.
			theSun.light = new PointLight();
			theSun.light.setDiffuse(new ColorRGBA(1.f, 1.0f, 1.0f, 1.f));
			theSun.light.setAmbient(new ColorRGBA(0.f, 0.f, 0.f, 0.f));
			theSun.light.setEnabled(true);
			theSun.light.setShadowCaster(true);
			// Create the sun which is a LightNode.
			theSun.sunNode = new SimpleLightNode("Sun", theSun.light);
			Vector3f absSunPos = sceneOffset.add(theSun.sunObserver.getPosition());
			theSun.sunNode.setLocalTranslation(absSunPos);
			// Create lens flare effect

			buildSunLensFlare(theSun, flareTexFilenames, pickNode);
			updateSunLensFlare(theSun);
			skyNode.attachChild(theSun.sunNode);

			// Attach light to sky light state and return it to be attach to the sky
			// LightState.
			skyLightState.attach(theSun.light);
			return theSun.light;
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Build the moon's lens flare effect.
	 * 
	 * @param texFilename -
	 *            filename used for lens flare texture
	 * @param moon -
	 *            Moon to set effect to
	 * @param moonSizeMult -
	 *            Moon size multiplier
	 * @throws MalformedURLException if file is not found
	 */
	private void buildMoonLensFlare(Moon moon, String texFilename,
			float moonSizemult) throws MalformedURLException {
		// Load the texture.
		Texture tex = new Texture();
		TextureState textureState = display.getRenderer().createTextureState();
		tex = TextureManager.loadTexture(ResourceLoader.locateResource(texFilename), Texture.MM_LINEAR_LINEAR,
				Texture.FM_LINEAR);
		textureState.setTexture(tex);
		textureState.setEnabled(true);
		AlphaState flareAlpha = display.getRenderer().createAlphaState();
		flareAlpha.setBlendEnabled(true);
		flareAlpha.setSrcFunction(AlphaState.SB_SRC_ALPHA);
		flareAlpha.setDstFunction(AlphaState.DB_ONE);
		flareAlpha.setTestEnabled(true);
		flareAlpha.setTestFunction(AlphaState.TF_GREATER);
		flareAlpha.setEnabled(true);

		// Create the sun's lens flare effect.
		moon.moonFlareEffect = new Quad("moonFlare", moonSizemult * 30000,
				moonSizemult * 30000);
		moon.moonFlareEffect.setRenderState(textureState);
		moon.moonFlareEffect.setRenderState(flareAlpha);
		BillboardNode moonFlareNode = new BillboardNode("moonFlareNode");
		moonFlareNode.setLightCombineMode(LightState.OFF);
		moonFlareNode.attachChild(moon.moonFlareEffect);
		moon.moonNode.attachChild(moonFlareNode);
	}

	/**
	 * Build the sun's lens flare effect.
	 * 
	 * @param texFilenames -
	 *            filenames array used for lens flare textures
	 * @param sun -
	 *            Sun to set effect to
	 * @param pickNode -
	 *            Node to pick occlusion from
	 * @throws MalformedURLException if a file is not found
	 */
	private void buildSunLensFlare(Sun sun, String[] texFilenames, Node pickNode) throws MalformedURLException {
		// Load the textures.
		TextureState[] textureStates = new TextureState[texFilenames.length];
		Texture[] texs = new Texture[texFilenames.length];
		for (int i = 0; i < textureStates.length; i++) {
			textureStates[i] = display.getRenderer().createTextureState();
			String fileName = texFilenames[i];
			texs[i] = TextureManager.loadTexture(ResourceLoader.locateResource(fileName),
					Texture.MM_LINEAR_LINEAR, Texture.FM_LINEAR);
			textureStates[i].setTexture(texs[i]);
			textureStates[i].setEnabled(true);
		}
		// Create the sun's lens flare effect.
		sun.sunFlareEffect = LensFlareFactory.createBasicLensFlare(
				"LensFlareEffect", textureStates);
		sun.sunFlareEffect.setTriangleAccurateOcclusion(true);
		sun.sunFlareEffect.setRootNode(pickNode);
		sun.sunFlareEffect.setLocalTranslation(sun.sunNode
				.getLocalTranslation());
		lensFlaresNode.attachChild(sun.sunFlareEffect);
	}

	/**
	 * clamp the value between min and max values
	 */
	private float clamp(float value, float min, float max) {
		if (value < min)
			return min;
		else if (value > max)
			return max;
		else
			return value;
	}

	/**
	 * Create a stars layer given a seamless texture of the stars. The texture
	 * is tiled many times and blended with alpha to create stars
	 * 
	 * @param starsTexFilename
	 *            The file name of the texture used for the stars layer.
	 */
	public void createStars(String starsTexFilename) {

		try {
			// Create a skybox for the star effect.
			starDome = new Sphere("We are stars. We are.", new Vector3f(0,
					-radius / 2.5f, 0), 8, 12, radius * 0.99f);
			starDome.setIsCollidable(false);
			starDome.setSolidColor(new ColorRGBA(1, 1, 1, 1));
			starDome.setCullMode(SceneElement.CULL_NEVER);
			starDome.setTextureMode(Sphere.TEX_PROJECTED);
	
			// Load the stars texture
			TextureState textureState = display.getRenderer().createTextureState();
			Texture tex = TextureManager.loadTexture(ResourceLoader.locateResource(starsTexFilename),
					Texture.MM_LINEAR_LINEAR, Texture.FM_LINEAR);
	
			// Tile
			tex.setWrap(Texture.WM_WRAP_S_WRAP_T);
			tex.setScale(new Vector3f(6, 6, 6));
			// Translation
			tex.setTranslation(new Vector3f(0, 0, 0));
	
			textureState.setTexture(tex);
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
			starDome.setRenderState(textureState);
			starDome.setRenderState(alphaState);
			starDome.updateRenderState();
	
			// Attach the star dome to the skydome.
			domeNode.attachChild(starDome);
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
	}

	/**
	 * @return Exposure factor
	 */
	public float getExposure() {
		return exposure;
	}

	/**
	 * @return gamma correction factor
	 */
	public float getGammaCorrection() {
		return gammaCorrection;
	}

	/**
	 * @return haze color
	 */
	public ColorRGBA getHazeColor() {
		return hazeColor;
	}

	/**
	 * @param index -
	 *            insertion index
	 * @return moon inserted at that index
	 */
	public Moon getMoon(int index) {
		return moons.get(index);
	}

	/**
	 * @return Over Cast factor
	 */
	public float getOvercastFactor() {
		return overcast;
	}

	private float[] getPerez(float[][] distribution, float turbidity) {
		float[] perez = new float[5];
		perez[0] = distribution[0][0] * turbidity + distribution[0][1];
		perez[1] = distribution[1][0] * turbidity + distribution[1][1];
		perez[2] = distribution[2][0] * turbidity + distribution[2][1];
		perez[3] = distribution[3][0] * turbidity + distribution[3][1];
		perez[4] = distribution[4][0] * turbidity + distribution[4][1];
		return perez;
	}

	/**
	 * @return dome radius
	 */
	public float getRadius() {
		return radius;
	}

	/**
	 * 
	 * @return root node the sky dome node was attached to if any, null
	 *         otherwise
	 */
	public Node getRootNode() {
		return rootNode;
	}

	/**
	 * @return Node whatever in sky depends on
	 */
	public Node getSkyDomeNode() {
		return skyDomeNode;
	}

	/**
	 * @return The light state associated to the sky dome node
	 */
	public LightState getSkyLightState() {
		return skyLightState;
	}

	/**
	 * @return sky color
	 */
	public ColorRGBA getSkySolidColor() {
		return skyColor;
	}

	/**
	 * @param index -
	 *            insertion index
	 * @return sun inserted at that index
	 */
	public Sun getSun(int index) {
		return suns.get(index);
	}

	/**
	 * @return get Turbidity factor
	 */
	public float getTurbidity() {
		return turbidity;
	}

	private float getZenith(float[][] zenithMatrix, float theta, float turbidity) {
		float theta2 = theta * theta;
		float theta3 = theta * theta2;

		return (zenithMatrix[0][0] * theta3 + zenithMatrix[0][1] * theta2
				+ zenithMatrix[0][2] * theta + zenithMatrix[0][3])
				* turbidity
				* turbidity
				+ (zenithMatrix[1][0] * theta3 + zenithMatrix[1][1] * theta2
						+ zenithMatrix[1][2] * theta + zenithMatrix[1][3])
				* turbidity
				+ (zenithMatrix[2][0] * theta3 + zenithMatrix[2][1] * theta2
						+ zenithMatrix[2][2] * theta + zenithMatrix[2][3]);
	}

	/**
	 * @return is linear exposure control enabled
	 */
	public boolean isLinearExposureControl() {
		return isLinearExpControl;
	}

	/**
	 * @param lat -
	 *            a sun's latitude
	 * @return true if this sun is fallen or not risen yet
	 */
	private boolean isNightTime(float lat) {
		return (lat > -0.9 * FastMath.PI && lat < -0.1 * FastMath.PI);
	}

	private float perezFunctionO1(float[] perezCoeffs, float thetaSun,
			float zenithValue) {
		float val = (1.0f + perezCoeffs[0] * FastMath.exp(perezCoeffs[1]))
				* (1.0f + perezCoeffs[2]
						* FastMath.exp(perezCoeffs[3] * thetaSun) + perezCoeffs[4]
						* FastMath.sqr(FastMath.cos(thetaSun)));
		return zenithValue / val;
	}

	private float perezFunctionO2(float[] perezCoeffs, float cosTheta,
			float gamma, float cosGamma2, float zenithValue) {
		return zenithValue
				* (1.0f + perezCoeffs[0]
						* FastMath.exp(perezCoeffs[1] * cosTheta))
				* (1.0f + perezCoeffs[2] * FastMath.exp(perezCoeffs[3] * gamma) + perezCoeffs[4]
						* cosGamma2);
	}

	/**
	 * Set Dawn color
	 */
	public void setDawnColor(ColorRGBA color) {
		dwColor[0] = color.r;
		dwColor[1] = color.g;
		dwColor[2] = color.b;
		dwColor[3] = color.a;
	}

	/**
	 * Set Dusk color
	 */
	public void setDuskColor(ColorRGBA color) {
		duColor[0] = color.r;
		duColor[1] = color.g;
		duColor[2] = color.b;
		duColor[3] = color.a;
	}

	/**
	 * Set Exposure factor
	 */
	public void setExposure(boolean isLinearExpControl, float exposure) {
		this.isLinearExpControl = isLinearExpControl;
		this.exposure = 1.0f / clamp(exposure, 1.0f, INFINITY);
	}

	/**
	 * Set gamma correction factor
	 */
	public void setGammaCorrection(float gamma) {
		this.gammaCorrection = 1.0f / clamp(gamma, EPSILON, INFINITY);
	}

	/**
	 * Set Over Cast factor
	 */
	public void setOvercastFactor(float overcast) {
		this.overcast = clamp(overcast, 0.0f, 1.0f);
	}

	/**
	 * Set root node for the sky dome color
	 * 
	 * @param rootNode -
	 *            Root node of the scene.
	 */
	public void setRootNode(Node rootNode) {
		// Detach flares to previously selected root node, then add to new one
		lensFlaresNode.removeFromParent();
		this.rootNode = rootNode;
		rootNode.attachChild(lensFlaresNode);
		rootNode.attachChild(skyDomeNode);
		// Get ambient light state
		ambient = (LightState) rootNode.getRenderState(RenderState.RS_LIGHT);
		if (ambient == null)
			ambient = display.getRenderer().createLightState();
	}

	/**
	 * Set sky color
	 * 
	 * @param skyColor -
	 *            Color of the sky. Black by default, you can try set it to
	 *            green or red for unusual efffects
	 */
	public void setSkySolidColor(ColorRGBA skyColor) {
		this.skyColor = skyColor;
	}

	/**
	 * Set the appropriate lightings to sky and earth based on the sun day/night
	 * cycle
	 * 
	 * @param theSun -
	 *            a sun to set
	 */
	private void setSunDayNightBehaviour(Sun theSun) {
		// Disable shadows at night
		if (isNightTime((float) theSun.sunObserver.getLatitude())
				&& wasNightTime) {
			wasNightTime = false;
			theSun.light.setShadowCaster(false);
			if (rootNode != null && ambient != null) {
				ambient.detach(theSun.light);
				rootNode.updateRenderState();
			}
		} else if (!isNightTime((float) theSun.sunObserver.getLatitude())
				&& !wasNightTime) {
			wasNightTime = true;
			theSun.light.setShadowCaster(true);
			if (rootNode != null && ambient != null) {
				rootNode.updateRenderState();
				ambient.attach(theSun.light);
			}
		}
	}

	/**
	 * Set Turbidity factor
	 */
	public void setTurbidity(float turbidity) {
		this.turbidity = clamp(turbidity, 1.0f, 512.0f);
	}

	/**
	 * Updates sky
	 * 
	 * @param viewerPos -
	 *            Vector representing the 3d position of the viewer
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
	public void update(Vector3f viewerPos, float tpf, int elapsHH, int elapsMM,
			int elapsSS) {

		// Set the sky dome's local translation to the camera's current
		// position.
		skyNode.setLocalTranslation(viewerPos.x, viewerPos.y - this.radius / 2,
				viewerPos.z);

		// Update suns
		double sunsLatitude = 0;
		float sunsLightness = 0;
		dome.setSolidColor(skyColor);
		for (int i = 0; i < suns.size(); i++) {
			Sun theSun = suns.get(i);

			// See if it's night time for this sun
			setSunDayNightBehaviour(theSun);

			// Move sun
			theSun.sunObserver.updateSunPosition(elapsHH, elapsMM, elapsSS);
			Vector3f absPos = sceneOffset.add(theSun.sunObserver.getPosition());
			theSun.sunNode.setLocalTranslation(absPos);
			theSun.light.setLocation(absPos);
			theSun.sunNode.updateGeometricState(tpf, true);
			// Update the sun light color and flares based on the current sun
			// coords.
			updateSunLightColor(theSun.light, (float) theSun.sunObserver
					.getLatitude());
			updateSunLensFlare(theSun);
			// Get sun shared params
			sunsLatitude += theSun.sunObserver.getLatitude();
			sunsLightness = theSun.sunFlareEffect.getIntensity();

			// Finally, render the sky color contribute for this sun
			updateSkyColor(theSun);

		}
		// Set ambient light depending on hour of the day.
		if (ambient != null)
			ambient.getGlobalAmbient().multLocal(sunsLightness / suns.size());

		// If set, update shadows color depending on mean sun latitude.
		if (shadows != null)
			shadows.setShadowColor(new ColorRGBA(0.75f, 0.75f, 0.75f, 0.75f)
					.multLocal((float) (1.2f - Math.sin(sunsLatitude))));
		// Update the star effect based on mean sun latitude.
		this.updateStars(sunsLatitude / suns.size(), elapsHH * 1 + elapsMM
				* 0.166f);

		// Update moons
		for (int i = 0; i < moons.size(); i++) {
			Moon theMoon = moons.get(i);
			// Move moon
			theMoon.moonObserver.updateMoonPosition(elapsHH, elapsMM, elapsSS);
			Vector3f absPos = sceneOffset.add(theMoon.moonObserver
					.getPosition());
			theMoon.light.setLocation(theMoon.moonObserver.getPosition()
					.subtract(sceneOffset));
			theMoon.moonNode.setLocalTranslation(absPos);
			theMoon.moonNode.updateGeometricState(tpf, true);

			// Update the moon light color and flares based on the current moon
			// coords.
			updateMoonLightColor(theMoon.light, (float) theMoon.moonObserver
					.getLatitude(), sunsLatitude);
			if (theMoon.moonFlareEffect != null)
				updateMoonLensFlare(theMoon.moonFlareEffect,
						theMoon.moonObserver.getLatitude(), sunsLatitude);
		}

	}

	/**
	 * Update a moon lens flare effect
	 * 
	 * @param moonFlare -
	 *            The flare effect to update
	 * @param moonLatitude -
	 *            The latitude of effect's moon
	 * @param sunsLatitude -
	 *            Mean latitude of the suns
	 */
	private void updateMoonLensFlare(Quad moonFlare, double moonLatitude,
			double sunsLatitude) {

		// Suns in the sky affects moon flare...
		// This weights rising sun, lowering moon flare:
		double meanMoonLatitude = moonLatitude * Math.sin(-sunsLatitude);

		if (meanMoonLatitude >= 0) {
			moonFlare.setLocalScale((float) (0.9 * Math.abs(Math
					.sin(meanMoonLatitude))));
			float alpha = (float) (0.3f * Math.abs(Math.sin(meanMoonLatitude)));
			moonFlare.setSolidColor(new ColorRGBA(1.0F, 1.0F, 1.0F, alpha));
			moonFlare.updateRenderState();
		} else
			moonFlare.setLocalScale(0);
	}

	/**
	 * Change the moonlight color based on the given current latitude.
	 * 
	 * @param light -
	 *            The moon light
	 * @param moonLatitude -
	 *            The moon latitude.
	 * @param sunsLatitude -
	 *            Mean suns latitude.
	 */
	private void updateMoonLightColor(Light light, double moonLatitude,
			double sunsLatitude) {

		// Suns in the sky affects moon light...
		// This weights rising sun, lowering moon light:
		double meanMoonLatitude = moonLatitude * Math.sin(-sunsLatitude);

		// If moon is away, change the moonlight color towards black.
		if (meanMoonLatitude < 0) {
			light.setDiffuse(ColorRGBA.black);
		}
		// Else if its moonlight time, change light color towards the mColor.
		else {
			light.setDiffuse(new ColorRGBA(mColor[0], mColor[1], mColor[2],
					mColor[3]).multLocal((float) Math.sin(-sunsLatitude)));
		}
		// Finally, set light intensity proportional to moon latitude
		light.setDiffuse(light.getDiffuse().multLocal(
				FastMath.abs(FastMath.sin((float) moonLatitude))));
	}

	/**
	 * update Sky color
	 */
	private void updateSkyColor(Sun sun) {

		float lat = (float) sun.sunObserver.getLatitude();
		float lon = (float) sun.sunObserver.getLongitude();

		// Get solar position
		// While latitude and longitude are given with EAST as 0, theta has 0 at
		// zenith and phi has 0 at WEST. Make the appropriate conversions
		float thetaSun, phiSun;
		if (lat >= 0)
			thetaSun = FastMath.abs(FastMath.abs(lat) - FastMath.HALF_PI);
		else
			thetaSun = FastMath.PI
					- FastMath.abs(FastMath.abs(lat) - FastMath.HALF_PI);
		phiSun = -Math.abs(lon + FastMath.PI);

		Vector3f sunDirection = new Vector3f();
		sunDirection.x = FastMath.cos(FastMath.HALF_PI - thetaSun)
				* FastMath.cos(phiSun);
		sunDirection.y = FastMath.sin(FastMath.HALF_PI - thetaSun);
		sunDirection.z = FastMath.cos(FastMath.HALF_PI - thetaSun)
				* FastMath.sin(phiSun);
		sunDirection.normalize();

		// get zenith luminance
		chi = ((4.0f / 9.0f) - (turbidity / 120.0f))
				* (FastMath.PI - (2.0f * thetaSun));
		zenithLuminance = ((4.0453f * turbidity) - 4.9710f) * FastMath.tan(chi)
				- (0.2155f * turbidity) + 2.4192f;
		if (zenithLuminance < 0.0f)
			zenithLuminance = -zenithLuminance;

		// get x / y zenith
		zenithX = getZenith(zenithXmatrix, thetaSun, turbidity);
		zenithY = getZenith(zenithYmatrix, thetaSun, turbidity);

		// get perez function parameters
		perezLuminance = getPerez(distributionLuminance, turbidity);
		perezX = getPerez(distributionXcomp, turbidity);
		perezY = getPerez(distributionYcomp, turbidity);

		// make some precalculation
		zenithX = perezFunctionO1(perezX, thetaSun, zenithX);
		zenithY = perezFunctionO1(perezY, thetaSun, zenithY);
		zenithLuminance = perezFunctionO1(perezLuminance, thetaSun,
				zenithLuminance);

		// trough all vertices
		for (int i = 0; i < dome.getBatchCount(); i++) {
			batch = dome.getBatch(i);

			normalBuf = batch.getNormalBuffer();
			colorBuf = batch.getColorBuffer();

			for (int j = 0; j < batch.getVertexCount(); j++) {
				BufferUtils.populateFromBuffer(vertexColor, colorBuf, j);
				BufferUtils.populateFromBuffer(vertex, normalBuf, j);

				// angle between sun and vertex
				gamma = FastMath.acos(vertex.dot(sunDirection));

				cosTheta = 1.0f / vertex.y;
				cosGamma2 = FastMath.sqr(FastMath.cos(gamma));

				// Compute x,y values
				x_value = perezFunctionO2(perezX, cosTheta, gamma, cosGamma2,
						zenithX);
				y_value = perezFunctionO2(perezY, cosTheta, gamma, cosGamma2,
						zenithY);

				// luminance(Y) for clear & overcast sky
				yClear = perezFunctionO2(perezLuminance, cosTheta, gamma,
						cosGamma2, zenithLuminance);
				yOver = (1.0f + 2.0f * vertex.y) / 3.0f;

				_Y = FastMath.LERP(overcast, yClear, yOver);
				_X = (x_value / y_value) * _Y;
				_Z = ((1.0f - x_value - y_value) / y_value) * _Y;

				colorTemp = new ColorXYZ(_X, _Y, _Z);
				if (isNightTime(lat)) {
					// It's night time
					colorTemp = new ColorXYZ(_X * 0.01f, _Y * 0.01f,
							-_Z * 0.045f);
				}
				color = colorTemp.convertXYZtoRGB();
				colorTemp = color.convertRGBtoHSV();

				if (isLinearExpControl) { // linear scale
					colorTemp.setValue(colorTemp.getValue() * exposure);
				} else { // exp scale
					colorTemp.setValue(1.0f - FastMath.exp(-exposure
							* colorTemp.getValue()));
				}
				color = colorTemp.convertHSVtoRGB();

				// gamma control
				color.setGammaCorrection(gammaCorrection);

				// clamp rgb between 0.0 - 1.0
				color.clamp();
				// Add to previously painted color
				additionColor = color.getRGBA().addLocal(vertexColor);
				BufferUtils.setInBuffer(additionColor, colorBuf, j);

				// Produce the mean color between sun horizon position and its
				// opposite
				if (!isNightTime(lat)) {
					if (j == 0)
						hazeColor = additionColor;
					else if (j == RAD_SAMPLES)
						hazeColor.interpolate(additionColor, 0.5f);
				} else
					hazeColor = ColorRGBA.black;
			}
		}
		// Check for invalid results
		if (Float.isNaN(hazeColor.r) || Float.isNaN(hazeColor.g)
				|| Float.isNaN(hazeColor.b) || Float.isNaN(hazeColor.a))
			hazeColor = ColorRGBA.black;
	}

	/**
	 * Change the star layer opacity based on the mean latitude of the suns in
	 * the sky.
	 * 
	 * @param sunsLatitude -
	 *            Mean suns latitude.
	 * @param angleFactor -
	 *            Stars rotation angle multiplier that represents elapsed time
	 *            from last update.
	 */
	private void updateStars(double meanSunsLatitude, float angleFactor) {
		if (starDome == null)
			return;
		// Suns in the sky affects stars visibility...
		// This weights rising sun, lowering stars visibility:
		float sunlightFactor = 0.4f - FastMath.sin((float) meanSunsLatitude);
		starDome.setSolidColor(new ColorRGBA(1, 1, 1, sunlightFactor));
		// Give the stars a sense of rotation
		TextureState ts = (TextureState) starDome
				.getRenderState(RenderState.RS_TEXTURE);
		Texture stars = ts.getTexture();
		Vector3f currentTra = stars.getTranslation();
		Vector3f angle = new Vector3f((currentTra.x + 0.02f * angleFactor) % 1,
				(currentTra.y + 0.02f * angleFactor) % 1,
				(currentTra.z + 0.01f * angleFactor) % 1);
		stars.setTranslation(angle);

	}

	/**
	 * Update a sun lens flare effect
	 * 
	 * @param sun -
	 *            The sun to update
	 */
	private void updateSunLensFlare(Sun theSun) {
		theSun.sunFlareEffect.setLocalTranslation(theSun.sunNode
				.getLocalTranslation());
		if (theSun.sunObserver.getLatitude() < 0) {
			theSun.sunFlareEffect.setTriangleAccurateOcclusion(false);
			theSun.sunFlareEffect.setIntensity(0);
		} else {
			theSun.sunFlareEffect.setTriangleAccurateOcclusion(true);
			theSun.sunFlareEffect.setIntensity((Math.max(FastMath.abs(FastMath
					.sin((float) theSun.sunObserver.getLatitude()))
					* 0.4f * theSun.sizeMult, 0.25f)));
		}

	}

	/**
	 * Change the sunlight color based on the given current latitude.
	 * 
	 * @param light -
	 *            The sun light
	 * @param sunLatitude -
	 *            The sun latitude.
	 */
	private void updateSunLightColor(Light light, float sunLatitude) {
		// Create an array defines the new sunlight color.
		float[] newColor = new float[4];
		float PI_6 = FastMath.PI / 6;
		if (sunLatitude < 0)
			sunLatitude += 2 * Math.PI;

		// If the current time is between 6:00 and 12:00, change the color
		// towards light.
		if (sunLatitude >= 11 * PI_6) {
			for (int i = 0; i < newColor.length; i++) {
				newColor[i] = (this.dwColor[i] + ((1 - this.dwColor[i]) / (3 * PI_6))
						* (sunLatitude - 11 * PI_6));
			}
			light.setDiffuse(new ColorRGBA(newColor[0], newColor[1],
					newColor[2], newColor[3]));
		} else if (sunLatitude < 2 * PI_6) {
			for (int i = 0; i < newColor.length; i++) {
				newColor[i] = (this.dwColor[i] + ((1 - this.dwColor[i]) / (3 * PI_6))
						* (sunLatitude - 0));
			}
			light.setDiffuse(new ColorRGBA(newColor[0], newColor[1],
					newColor[2], newColor[3]));
		}
		// Else if the current time is between 12:00 and 18:00, change the color
		// to white.
		else if (sunLatitude >= 2 * PI_6 && sunLatitude < 4 * PI_6) {
			light.setDiffuse(ColorRGBA.white);
		}
		// Else if the current time is between 18:00 and 22:00, change the color
		// towards dusk.
		else if (sunLatitude >= 4 * PI_6 && sunLatitude < 7 * PI_6) {
			for (int i = 0; i < newColor.length; i++) {
				newColor[i] = (1 - ((1 - this.duColor[i]) / (2 * PI_6))
						* (sunLatitude - 4 * PI_6));
			}
			light.setDiffuse(new ColorRGBA(newColor[0], newColor[1],
					newColor[2], newColor[3]));
		}
		// Else if the current time is between 22:00 and 24:00, change the color
		// from dusk to white.
		else if (sunLatitude >= 7 * PI_6 && sunLatitude < 9 * PI_6) {
			for (int i = 0; i < newColor.length; i++) {
				newColor[i] = (this.duColor[i] + ((1 - this.duColor[i]) / (2 * PI_6))
						* (sunLatitude - 7 * PI_6));
			}
			light.setDiffuse(new ColorRGBA(newColor[0], newColor[1],
					newColor[2], newColor[3]));
		}
		// Else if the current time is between 0:00 and 6:00, change the color
		// towards dawn.
		else if (sunLatitude >= 9 * PI_6 && sunLatitude < 11 * PI_6) {
			for (int i = 0; i < newColor.length; i++) {
				newColor[i] = (1 - ((1 - this.dwColor[i]) / (4 * PI_6))
						* (sunLatitude - 9 * PI_6));
			}
			light.setDiffuse(new ColorRGBA(newColor[0], newColor[1],
					newColor[2], newColor[3]));
		}

	}
}
