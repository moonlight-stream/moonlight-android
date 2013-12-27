package com.limelight.binding.video;

import android.content.Context;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;
import android.view.Surface;

public class RsRenderer {
	private RenderScript rs;
	private Allocation renderBuffer;
	
	public RsRenderer(Context context, int width, int height, Surface renderTarget) {
		rs = RenderScript.create(context);

		Type.Builder tb = new Type.Builder(rs, Element.RGBA_8888(rs));
		tb.setX(width);
		tb.setY(height);
		Type bufferType = tb.create();

		renderBuffer = Allocation.createTyped(rs, bufferType, Allocation.USAGE_SCRIPT | Allocation.USAGE_IO_OUTPUT);
		renderBuffer.setSurface(renderTarget);
	}
	
	public void release() {
		renderBuffer.setSurface(null);
		renderBuffer.destroy();
		rs.destroy();
	}
	
	public void render(byte[] rgbData) {
		renderBuffer.copyFrom(rgbData);
		renderBuffer.ioSend();
	}
}
