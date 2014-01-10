package com.limelight.nvstream.http;

import java.io.IOException;

public class GfeHttpResponseException extends IOException {
	private static final long serialVersionUID = 1543508830807804222L;
	
	private int errorCode;
	private String errorMsg;
	
	public GfeHttpResponseException(int errorCode, String errorMsg) {
		this.errorCode = errorCode;
		this.errorMsg = errorMsg;
	}
	
	@Override
	public String getMessage() {
		return "GFE error: "+errorMsg+" (Error code: "+errorCode+")";
	}
}
