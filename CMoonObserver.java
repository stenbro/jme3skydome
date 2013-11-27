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

package jives.utils;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.logging.Logger;

import com.jme.math.Matrix3f;
import com.jme.math.Vector3f;

public class CMoonObserver {
	private static final Logger logger = Logger.getLogger(CMoonObserver.class
            .getName());
	private static float distScaleFactor = 6378.137f; // Earth radii to KM *
	// 10
	double lambda;
	// 10
	double beta;
	// 10
	double r;
	double lambdaOffset;
	double betaOffset;
	double rOffset;

	Date currentDate;

	Vector3f moonPosition = new Vector3f();
	private boolean debug = false;
	private double xs;
	private double ys;
	private float siteLon;
	private float siteLat;

	/**
	 * CONSTRUCTOR: build an earth moon giving earth date and ecliptic
	 * coordinates offsets.<br>
	 * 
	 * @param currentDate -
	 *            Date from which sun position is set
	 * @param lambdaOffset -
	 *            Ecliptic longitude offset [h], or 0 for Solar System Sun
	 *            longitude
	 * @param betaOffset -
	 *            Ecliptic latitude offset [°], or 0 for Solar System Sun
	 *            latitude
	 * @param rOffset -
	 *            Distance offset [Km], or 0 for Solar System Sun distance
	 */
	public CMoonObserver(Date currentDate, double lambdaOffset,
			double betaOffset, double rOffset) {
		this.currentDate = currentDate;
		this.lambdaOffset = lambdaOffset;
		this.betaOffset = betaOffset;
		this.rOffset = rOffset;

		calculateCartesianCoords(currentDate);
	}

	/**
	 * SOURCE: http://graphics.ucsd.edu/~henrik/papers/nightsky/nightsky.pdf
	 * 
	 * @param date -
	 *            current date
	 */
	private void calculateCartesianCoords(Date date) {

		GregorianCalendar gc = new GregorianCalendar();
		gc.setTimeZone(TimeZone.getTimeZone("UTC"));
		gc.setTime(date);
		int DD = gc.get(Calendar.DAY_OF_MONTH);
		int MM = gc.get(Calendar.MONTH) + 1;
		int YY = gc.get(Calendar.YEAR);
		int HOUR = gc.get(Calendar.HOUR_OF_DAY);
		int MN = gc.get(Calendar.MINUTE);

		// fecha juliana
		double HR = HOUR + (MN / 60.f);
		double GGG = 1;
		if (YY <= 1585)
			GGG = 0;
		double JD = -1
				* Math.floor(7 * (Math.floor((MM + 9) / 12.f) + YY) / 4.f);
		double S = 1;
		if ((MM - 9) < 0)
			S = -1;
		double A = Math.abs(MM - 9);
		double J1 = Math.floor(YY + S * Math.floor(A / 7.f));
		J1 = -1 * Math.floor((Math.floor(J1 / 100.f) + 1) * 3 / 4.f);
		JD = JD + Math.floor(275 * MM / 9.f) + DD + (GGG * J1);
		JD = JD + 1721027 + 2 * GGG + 367 * YY - 0.5;
		JD = JD + (HR / 24.f);

		double T = (JD - 2451545.0) / 36525;
		double ladj = 3.8104 + 8399.7091 * T;
		double madj = 2.3554 + 8328.6911 * T;
		double m = 6.2300 + 628.3019 * T;
		double d = 5.1985 + 7771.3772 * T;
		double f = 1.6280 + 8433.4663 * T;
		lambda = ladj + 0.1098 * Math.sin(madj) + 0.0222
				* Math.sin(2 * d - madj) + 0.0115 * Math.sin(2 * d) + 0.0037
				* Math.sin(2 * madj) - 0.0032 * Math.sin(m) - 0.0020
				* Math.sin(2 * f) + 0.0010 * Math.sin(2 * d - 2 * madj)
				+ 0.0010 * Math.sin(2 * d - m * madj) + 0.0009
				* Math.sin(2 * d + madj) + 0.0008 * Math.sin(2 * d - m)
				+ 0.0007 * Math.sin(madj - m) - 0.0006 * Math.sin(d) - 0.0005
				* Math.sin(m + madj);
		beta = 0.0895 * Math.sin(f) + 0.0049 * Math.sin(madj + f) + 0.0048
				* Math.sin(madj - f) + 0.0030 * Math.sin(2 * d - f) + 0.0010
				* Math.sin(2 * d + f - madj) + 0.0008
				* Math.sin(2 * d - f - madj) + 0.0006 * Math.sin(2 * d + f);
		double piadj = 0.016593 + 0.000904 * Math.cos(madj) + 0.000166
				* Math.cos(2 * d - madj) + 0.000137 * Math.cos(2 * d)
				+ 0.000049 * Math.cos(2 * madj) + 0.000015
				* Math.cos(2 * d + madj) + 0.000009 * Math.cos(2 * d - m);
		// Apply offsetts
		lambda += lambdaOffset;
		beta += betaOffset;
		r = 1 / piadj + rOffset;

		moonPosition = new Vector3f((float) (r * Math.sin(beta)), (float) (r
				* Math.sin(lambda) * Math.cos(beta)), (float) (r
				* Math.cos(lambda) * Math.cos(beta)));

		// latitude, longitude of the place on earth
		float lat = siteLat;
		float lon = siteLon + (float) (Math.PI * 3 / 2);
		// Convert to local horizon coordinates
		double eta = 0.409093 - 0.000227 * T; // obliquity of the ecliptic
		double LMST = 4.894961 + 230121.675315 * T + lon; // local sidereal
		// time
		Matrix3f matRx = new Matrix3f();
		Matrix3f matRy = new Matrix3f();
		Matrix3f matRz = new Matrix3f();

		matRx.fromAngleNormalAxis((float) -eta, new Vector3f(1, 0, 0));
		matRy.fromAngleNormalAxis((float) -(lat - Math.PI / 2), new Vector3f(0,
				1, 0));
		matRz.fromAngleNormalAxis((float) -LMST, new Vector3f(0, 0, 1));
		moonPosition = matRz.mult(matRx.mult(matRy.mult(moonPosition)));
		moonPosition.subtract(new Vector3f(0, 0, 1));

		// Get long, lat
		xs = Math.atan2(moonPosition.z, -moonPosition.x);
		ys = Math.atan2(moonPosition.y, -moonPosition.x);

		// Scale distance
		moonPosition.multLocal(distScaleFactor);

		if (debug) {
			logger.info("MOON OBSERVER > " + DD + "/" + MM + "/" + YY
					+ " - " + HOUR + ":" + MN);
			logger.info("             > POS " + moonPosition);
		}
	}

	public void enableDebug(boolean enable) {
		debug = true;
	}

	/**
	 * 
	 * @return current date
	 */
	public Date getCurrentDate() {
		return currentDate;
	}

	/**
	 * 
	 * @return moon latitude
	 */
	public double getLatitude() {
		return ys;
	}

	/**
	 * 
	 * @return moon longitude
	 */
	public double getLongitude() {
		return xs;
	}

	/**
	 * 
	 * @return moon position, centered at the origin
	 */
	public Vector3f getPosition() {
		return moonPosition;
	}

	/**
	 * 
	 * @return earth site latitude within (-PI, PI) interval
	 */
	public double getSiteLatitude() {
		return siteLat;
	}

	/**
	 * 
	 * @return earth site longitude within (-PI, PI) interval
	 */
	public double getSiteLongitude() {
		return siteLon;
	}

	/**
	 * 
	 * Set earth site latitude
	 */
	public void setSiteLatitude(float siteLat) {
		this.siteLon = siteLat;
	}

	/**
	 * 
	 * Set earth site longitude
	 */
	public void setSiteLongitude(float siteLon) {
		this.siteLon = siteLon;
	}

	/**
	 * Updates moon position
	 * 
	 * @param elapsHH -
	 *            Elapsed hours
	 * @param elapsMM -
	 *            Elapsed minutes
	 * @param elapsSS -
	 *            Elapsed seconds
	 */
	public void updateMoonPosition(int elapsHH, int elapsMM, int elapsSS) {
		GregorianCalendar gc = new GregorianCalendar();
		gc.setTime(currentDate);
		gc.add(Calendar.HOUR_OF_DAY, elapsHH);
		gc.add(Calendar.MINUTE, elapsMM);
		gc.add(Calendar.SECOND, elapsSS);
		currentDate = gc.getTime();

		calculateCartesianCoords(gc.getTime());

	}

}
