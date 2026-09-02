#version 310 es

//============================================================================================================
//
//                  Copyright (c) 2025, Qualcomm Innovation Center, Inc. All rights reserved.
//                              SPDX-License-Identifier: BSD-3-Clause
//
//============================================================================================================

// Fallback if the defines are not injected dynamically
#ifndef SRC_W
#define SRC_W 1280.0
#define SRC_H 720.0
#define INV_SRC_W (1.0 / 1280.0)
#define INV_SRC_H (1.0 / 720.0)
#endif

precision mediump float;
precision mediump int;

////////////////////////
// USER CONFIGURATION //
////////////////////////

#define EdgeThreshold (8.0 / 255.0)
#define EdgeSharpness 2.0

////////////////////////
////////////////////////
////////////////////////

// ViewportInfo is a compile-time constant expression for optimal performance
const mediump vec4 ViewportInfo = vec4(INV_SRC_W, INV_SRC_H, SRC_W, SRC_H);

uniform mediump sampler2D ps0;

// Variables passed down from the Vertex Shader to avoid per-pixel calculations
in mediump vec2 v_texCoord;
in mediump vec2 v_imgCoord;

out mediump vec4 out_Target0;

mediump float fastLanczos2(mediump float x)
{
	mediump float wA = x - 4.0;
	mediump float wB = x * wA - wA;
	return wB * (wA * wA);
}

mediump vec2 weightY(mediump vec2 d, mediump float c, mediump vec3 data)
{
	mediump float std = data.x;
	mediump vec2 dir = data.yz;

	mediump float edgeDis = dot(d, dir.yx);
	mediump float x = dot(d, d) + (edgeDis * edgeDis) * (clamp((c * c) * std, 0.0, 1.0) * 0.7 - 1.0);

	return vec2(1.0, c) * fastLanczos2(x);
}

mediump vec2 edgeDirection(mediump vec4 left, mediump vec4 right)
{
	mediump float RxLz = right.x - left.z;
	mediump float RwLy = right.w - left.y;
	mediump vec2 delta = vec2(RxLz + RwLy, RxLz - RwLy);
	mediump float lengthInv = inversesqrt(dot(delta, delta) + 3.075740e-05);

	return delta * lengthInv;
}

void main()
{
	mediump vec4 color = textureLod(ps0, v_texCoord, 0.0);

	mediump vec2 coord = floor(v_imgCoord) * ViewportInfo.xy;
	mediump vec2 pl = fract(v_imgCoord);

	mediump vec4 left = textureGather(ps0, coord, 1);

	mediump float edgeVote = abs(left.z - left.y) + abs(color.y - left.y) + abs(color.y - left.z);

	if(edgeVote > EdgeThreshold)
	{
		coord.x += ViewportInfo.x;

		mediump vec4 right = textureGather(ps0, coord + vec2(ViewportInfo.x, 0.0), 1);
		mediump vec4 upDown;
		upDown.xy = textureGather(ps0, coord + vec2(0.0, -ViewportInfo.y), 1).wz;
		upDown.zw = textureGather(ps0, coord + vec2(0.0, ViewportInfo.y), 1).yx;

		mediump float mean = (left.y + left.z + right.x + right.w) * 0.25;

		left -= mean;
		right -= mean;
		upDown -= mean;
		color.w = color.y - mean;

		mediump float sum = dot(abs(left), vec4(1.0))
		                  + dot(abs(right), vec4(1.0))
		                  + dot(abs(upDown), vec4(1.0));

		mediump float sumMean = 10.14185 / sum;
		mediump float std = sumMean * sumMean;

		mediump vec3 data = vec3(std, edgeDirection(left, right));

		mediump vec2 aWY = weightY(pl + vec2(0.0, 1.0), upDown.x, data);
		aWY += weightY(pl + vec2(-1.0, 1.0), upDown.y, data);
		aWY += weightY(pl + vec2(-1.0, -2.0), upDown.z, data);
		aWY += weightY(pl + vec2(0.0, -2.0), upDown.w, data);
		aWY += weightY(pl + vec2(1.0, -1.0), left.x, data);
		aWY += weightY(pl + vec2(0.0, -1.0), left.y, data);
		aWY += weightY(pl, left.z, data);
		aWY += weightY(pl + vec2(1.0, 0.0), left.w, data);
		aWY += weightY(pl + vec2(-1.0, -1.0), right.x, data);
		aWY += weightY(pl + vec2(-2.0, -1.0), right.y, data);
		aWY += weightY(pl + vec2(-2.0, 0.0), right.z, data);
		aWY += weightY(pl + vec2(-1.0, 0.0), right.w, data);

		mediump float finalY = aWY.y / aWY.x;

		mediump vec2 leftYZ_rightXW = vec2(max(left.y, left.z), max(right.x, right.w));
		mediump float maxY = max(leftYZ_rightXW.x, leftYZ_rightXW.y);

		mediump vec2 min_leftYZ_rightXW = vec2(min(left.y, left.z), min(right.x, right.w));
		mediump float minY = min(min_leftYZ_rightXW.x, min_leftYZ_rightXW.y);

		mediump float deltaY = clamp(EdgeSharpness * finalY, minY, maxY) - color.w;

		deltaY = clamp(deltaY, -23.0 / 255.0, 23.0 / 255.0);

		color.rgb = clamp(color.rgb + deltaY, 0.0, 1.0);
	}

	color.w = 1.0;
	out_Target0 = color;
}
