package com.limelight.utils;

public class Vector2d {
	private float x;
	private float y;
	private double magnitude;
	
	public static final Vector2d ZERO = new Vector2d();
	
	public Vector2d() {
		initialize(0, 0);
	}
	
	public void initialize(float x, float y) {
		this.x = x;
		this.y = y;
		this.magnitude = Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2));
	}
	
	public double getMagnitude() {
		return magnitude;
	}
	
	public void getNormalized(Vector2d vector) {
		vector.initialize((float)(x / magnitude), (float)(y / magnitude));
	}
	
	public void scalarMultiply(double factor) {
		initialize((float)(x * factor), (float)(y * factor));
	}
	
	public void setX(float x) {
		initialize(x, this.y);
	}
	
	public void setY(float y) {
		initialize(this.x, y);
	}
	
	public float getX() {
		return x;
	}
	
	public float getY() {
		return y;
	}
}
